# Prompt E206 — A lista mostra um dono, o cabeçalho mostra outro

> Leia `AGENTS.md`, `CLAUDE.md` e `docs/13-estado-do-projeto.md`.
> Branch própria (`fix/atendente-divergente-lista-cabecalho`) e PR. **Sem merge, sem deploy.**
> Backend (SQL do painel) + teste de integração. **Sem migration** nesta etapa.
> `cd backend && ./mvnw -pl crm-app -am verify`.

**Etapa em duas partes.** A Parte 1 é investigação e **para** para decisão minha antes de qualquer
código da Parte 2 que mude regra de negócio. A Parte 2a (exibição) pode seguir direto.

---

## O sintoma

Lead **CLARICE ALVES**, tel `5561985831716`, canal `uzapi-autotic`. Ao mesmo tempo, na mesma tela:

| Onde | Mostra |
|---|---|
| Lista de atendimentos (nome abaixo da prévia "obgd!!") | **Debora** |
| Cabeçalho da conversa — `5561985831716 · Atendido por …` | **Joanna** |
| Painel "Detalhes do lead" → Informações Gerais (ícone de pessoa) | **Joanna** |

As últimas mensagens humanas do thread (14/09, 14:44–14:50) são todas da Debora:

```
Debora  14:44  Boa tarde!
Debora  14:46  Podemos confirmar sua consulta de QUARTA FEIRA dia 16/09 com o DR CIRO as 17:00 hr?
Debora  14:46  confirma?
Debora  14:50  obgd!!
```

## O que já foi mapeado no código (não refaça, confirme)

A hipótese inicial — "a lista usa o remetente da última mensagem e o cabeçalho usa um campo de
atribuição" — **está errada**. As três telas leem a **mesma coluna**, `atendimento.atendente_id`
→ `usuario.nome`, pela mesma query (`CAMPOS` + `ORIGEM` em
`PainelDeAtendimentosRepositorioJdbc`). A divergência é **de qual linha de `atendimento`** cada uma lê.

**Lista** — `cartao-conversa.tsx:183` exibe `cartao.atendenteNome`. O cartão vem de
`GET /api/v1/atendimentos/inbox`, que agrupa **um cartão por lead**:

```sql
ROW_NUMBER() OVER (
    PARTITION BY a.lead_id
    ORDER BY COALESCE(ultima.enviado_em, a.iniciado_em) DESC, a.iniciado_em DESC, a.id DESC
) AS linha_do_lead
... WHERE linha_do_lead = 1
```

Ou seja: o atendente do cartão é o do atendimento do lead **com a mensagem mais recente** —
inclusive se esse atendimento estiver `FINALIZADO`.

**Clique no cartão** — `pagina-atendimentos-cliente.tsx:191`:

```ts
const idParaAbrir = cartao.atendimentoAtivoId ?? cartao.atendimentoId;
```

`atendimento_ativo_id` é o atendimento **não finalizado** do lead com mensagem mais recente
(subquery em `CAMPOS`). É **outro id** sempre que o atendimento da última mensagem não é o aberto.

**Cabeçalho e Informações Gerais** — `cabecalho-conversa.tsx:144` e
`pagina-atendimentos-cliente.tsx:921` leem `conversa.atendenteNome`, onde
`conversa = estadoSelecionado.cartao`, vindo de `GET /atendimentos/{idParaAbrir}/estado`
(`ObterEstadoAtendimentoSelecionadoUseCase` → `painel.porAtendimentoId`).

**Thread** — carregado por lead, então exibe as mensagens da Debora mesmo com o atendimento da
Joanna aberto.

**Não há restrição no banco** de um atendimento aberto por lead: só `idx_atendimento_lead`
(índice simples, `V10__indices.sql:43`).

Cenário provável: A1 (Debora, mensagens até 14:50, provavelmente `FINALIZADO`) e A2 (Joanna,
aberto, sem mensagem posterior a 14:50). Lista → A1 → Debora. Clique → A2 → Joanna.

---

## Parte 1 — Confirmar com dado, não com leitura de código

1. Rode em produção (somente leitura) e cole o resultado **literal** no relatório:

   ```sql
   SELECT a.id, a.status, u.nome AS atendente, a.iniciado_em, a.finalizado_em, a.canal_id,
          (SELECT max(enviado_em) FROM mensagem m WHERE m.atendimento_id = a.id) AS ultima_msg,
          (SELECT count(*)        FROM mensagem m WHERE m.atendimento_id = a.id) AS qtd_msgs
     FROM atendimento a
     JOIN lead l ON l.id = a.lead_id
     LEFT JOIN usuario u ON u.id = a.atendente_id
    WHERE l.telefone LIKE '%5561985831716%'
    ORDER BY a.iniciado_em;
   ```

   Ajuste nomes de coluna ao schema real (`docs/11-banco-atual.md`) — não invente coluna.

2. **Quem criou o A2 e por quê.** Cruze `iniciado_em` do A2 com: timeline de transferência
   (`TimelineRepositorioJdbc`), eventos da outbox, rodízio, e abertura de atendimento pela agenda
   (`docs/relatorio-abertura-atendimento-agenda.md` — a conversa é uma confirmação de consulta).
   A pergunta é: **algum caminho abre atendimento novo quando já existe um aberto para o lead,
   em vez de reaproveitá-lo?**

3. **Tamanho do problema.** Quantos leads, hoje, têm o dono do cartão ≠ dono do atendimento que o
   clique abre:

   ```sql
   -- leads com mais de um atendimento aberto
   SELECT lead_id, count(*) FROM atendimento
    WHERE status <> 'FINALIZADO' GROUP BY lead_id HAVING count(*) > 1;
   ```

   e, separadamente, leads cujo atendimento de mensagem mais recente está finalizado enquanto
   outro, de **outro** atendente, está aberto. Números, não "alguns".

4. **Caso Michele.** Leia `investigacao-michele-fora-do-rodizio-recebendo-15-09.md` (não está no
   repositório — peça o arquivo se não o encontrar; não suponha o conteúdo). Diga se envolve mais
   de um atendimento no mesmo lead. Se sim, é o mesmo padrão e o relatório precisa dizer isso.

**Pare aqui e reporte** se a Parte 1 mostrar um único atendimento para a Clarice — a causa é
outra e o resto deste prompt não se aplica.

---

## Parte 2a — Exibição: o cartão mostra o dono do atendimento que o clique abre

Mudança no `PainelDeAtendimentosRepositorioJdbc`: quando `atendimento_ativo_id` existe, `status`,
`atendente_id` e `atendente_nome` do cartão vêm **dele**, não da linha da última mensagem. Prévia,
horário e contagem de não lidas continuam como estão (são do lead).

- Mesma query para lista, contagem e `porAtendimentoId` — **não** escreva uma segunda definição
  de "dono do cartão" (ver o aviso em `WHERE_TODOS_ATIVOS` sobre 194eded0/5712722b).
- Não mexa na visibilidade (`WHERE_*`). Esta etapa corrige o que é exibido, não quem vê o quê.
  Se perceber que o recorte das abas (Ativos/Pendentes) também decide pelo atendimento errado,
  **reporte**, não corrija.
- Verifique o impacto no filtro por atendente (`lista-conversas.tsx:228` monta o mapa de nomes a
  partir de `cartao.atendenteId`) e no `atendenteEstaAtrasado` do cartão.

## Parte 2b — Regra: pode haver dois atendimentos abertos no mesmo lead?

**Não implemente.** Traga a resposta da Parte 1.2 e as opções com trade-off. Isto toca
`RN-CRM-02` (lead atribuído pertence ao atendente) e a disputa por comissão — decisão minha.

---

## Definição de pronto

- [ ] Resultado literal das queries da Parte 1 (Clarice + tamanho do problema)
- [ ] Origem do A2 identificada, com o caminho de código que o criou — ou "não identificado" e por quê
- [ ] Veredito sobre o caso Michele: mesmo padrão / independente / não verificado
- [ ] Teste de integração (Testcontainers) que **falha antes da correção**: lead com A1
      finalizado (atendente X, mensagem mais recente) e A2 aberto (atendente Y) → cartão da inbox
      e `/estado` do `atendimentoAtivoId` devolvem o **mesmo** atendente (Y)
- [ ] Negativo: lead com um único atendimento continua exibindo o mesmo atendente de antes
- [ ] Negativo de visibilidade: atendente X não passa a ver o cartão do lead por causa da mudança
- [ ] `./mvnw clean verify` completo; CI com número da run, ou "não verificado"
- [ ] Relatório no formato do `AGENTS.md`
