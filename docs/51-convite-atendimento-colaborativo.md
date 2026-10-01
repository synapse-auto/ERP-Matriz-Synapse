# 51. Convite e atendimento colaborativo

Diagnóstico e decisão sobre convidar um colega para o mesmo atendimento sem trocar o
responsável. Reprodução feita com backend e PostgreSQL reais (perfil `dev`, banco descartável),
dois atendentes da seed (A = Ana, B = Bruno) e um terceiro não convidado (C = Caio), com dados
sintéticos. Nenhum lead real foi usado.

## 1. Como reproduzir

```bash
# banco descartável já migrado até a última versão (ver docs/48 §1 para o runner da V73)
psql "$URL" -v ON_ERROR_STOP=1 -f frontend/e2e/fixtures/convite-colaborativo.sql
cd frontend
API_URL=http://localhost:8091 \
PSQL="docker exec -i synapse-postgres psql -U synapse -d <banco> -At" \
node e2e/_reproduzir-convite-colaborativo.mjs
```

O script abre duas sessões STOMP (A e B), executa o fluxo pela API HTTP e, em cada passo, grava
`lead.atendente_responsavel_id`, `atendimento.atendente_id`, pedidos de entrada, participantes
ativos, o status HTTP do histórico para A/B/C e os eventos recebidos por sessão. Sai com código 1
quando algum passo troca o responsável sem a ação explícita "Transferir".

## 2. Resultado antes da correção

Saída completa em `evidencias/convite-colaborativo/antes-reproducao.jsonl`.

| Passo | HTTP | Lead | Atendimento | Participantes | Histórico A/B/C | Eventos |
|---|---|---|---|---|---|---|
| 0. inicial | — | A | A | — | 200/404/404 | — |
| 1. A convida B | 200 | A | A | — (convite PENDENTE) | 200/200/404 | B: `CONVITE_ATENDIMENTO` |
| 2. B vê o convite | 200 | A | A | — | 200/200/404 | cartão em Pendentes |
| 3. B aceita | 200 | A | A | B | 200/200/404 | A: `RESPOSTA_PEDIDO_ENTRADA` |
| 4. A e B abrem | — | A | A | B | 200/200/404 | — |
| **5. B envia** | 200, `transferiuOLead=true` | **B** | **B** | B | **404**/200/404 | A: `MENSAGEM` + **revogação**; B: `TRANSFERENCIA` |
| 6. A envia | **404** | B | B | B | 404/200/404 | — |
| 7. C envia | 404 | B | B | B | 404/200/404 | — |

Conclusão, separando as três hipóteses do prompt:

1. **Clique em "Convidar" ou aceitação** — não transferem. O frontend chama só
   `POST /convidar` e `GerenciarParticipacaoAtendimentoUseCase` não toca no responsável.
2. **Primeira mensagem do convidado** — **é aqui que a propriedade muda.** `EnviarMensagemUseCase`
   aplica a RN-CRM-06 a todo envio manual, inclusive de participante ativo (decisão do commit
   `c7c5a65`, que removeu a exceção criada em `6cabcf2`). B vira dono do lead e do atendimento.
3. **Colaboração real** — consequência direta do item 2: A deixa de ser dono e não é participante,
   então perde a RLS, recebe revogação no WebSocket e não consegue mais ler nem responder (404).
   "Dois atendentes no mesmo lead" durava exatamente até o convidado falar.

### Defeitos adicionais reproduzidos

- **Convite expirado continua concedendo leitura.** A política RLS da V78 libera
  `atendimento`/`lead` para `convite.status = 'PENDENTE'` sem olhar a validade
  (`atendimento.pedido-entrada-expiracao-minutos`, 30 min). Com o convite envelhecido 3 h, B
  continuou lendo o histórico (200) e o cartão continuou em Pendentes.
- **Aceitar convite expirado responde 500.** `responder` lança `IllegalStateException`, que nenhum
  `@ExceptionHandler` traduz.
- **Convite expirado nunca é substituído.** O pedido expirado continua `PENDENTE` no banco; o índice
  `ux_pedido_convite_pendente` impede um novo e `POST /convidar` devolve o pedido velho com
  `jaExistia=true`. O destinatário não consegue mais ser convidado para aquele atendimento.

### Defeito visual

Capturas em `evidencias/convite-colaborativo/antes-modal-{1366,1024,768,390}.png` (Ana,
convite aberto pelo cabeçalho, lista com três candidatos, um com nome de 62 caracteres):

- O nome longo é **cortado no meio da palavra, sem reticências e sem `title`**: o conteúdo mede
  452 px dentro de um botão de 350 px (324 px em 390 px de largura) em todas as larguras.
- Um clique no nome já envia o convite: não há seleção, confirmação nem indicação do que acontece.
- O modal não mostra quem é o responsável nem quem já participa, e reaproveita sem distinção a lista
  da transferência — o que alimenta a leitura de que "Convidar" transfere.

Com nomes curtos o layout não quebra; nenhum outro defeito de geometria foi observado.
