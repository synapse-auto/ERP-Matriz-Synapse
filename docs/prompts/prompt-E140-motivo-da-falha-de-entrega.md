# Prompt E140 — O balão diz "Falha ao enviar" e esconde o motivo

> Leia `AGENTS.md`, `CLAUDE.md` e `docs/13-estado-do-projeto.md`.
> Branch própria (`feat/motivo-da-falha-de-entrega`) e PR. **Sem merge, sem deploy.**
> Backend (leitura + DTO) e frontend. **Sem migration** — a coluna existe desde a V52.
> `cd backend && ./mvnw -pl crm-app -am verify` e a suíte do `frontend/`.

Quando um envio falha, o balão mostra só **"Falha ao enviar"** e um botão **Reenviar**. O atendente
não sabe se o problema é temporário ou definitivo, clica em Reenviar, falha de novo, e repete.

---

## O que já existe e nunca foi ligado

A V52 (E118) criou `mensagem.erro_entrega` JSONB `{codigo, titulo}` com o motivo que o provedor
devolve, e o comentário da própria migration diz para que ela existe:

> *"Nao vai so no log: o atendente precisa distinguir 'falhou' de 'falhou porque o arquivo nao e
> suportado' (o 131053 do audio)."*

A coluna é **gravada** — e só. `erro_entrega` aparece em exatamente um lugar do código:
o `UPDATE` em `MensagemRepositorioJdbc:58`. Não há leitura, não há campo no DTO
(`AtendimentoMensagensController.MensagemResposta`), não há nada em
`frontend/src/lib/atendimento/types.ts:127` nem na bolha. A metade que importava para o atendente
ficou pelo caminho.

## Por que isso é urgente e não cosmético

Dados de produção de 02/09:

| código | título do provedor | ocorrências no dia |
| --- | --- | --- |
| `131026` | Message undeliverable | 5 |
| `131047` | Re-engagement message | 8 — **sete deles em sequência às 08:00** |

Os sete seguidos são alguém clicando **Reenviar**. O `131026` é permanente para aquele número; o
balão não diz isso, o atendente insiste, e cada insistência vira um `131047` — porque o Reenviar
manda como **texto livre**, fora da janela de 24h. O silêncio do balão fabrica o segundo erro.

## Bloco 1 — Trazer o motivo até a tela

`erro_entrega` precisa sair do banco e chegar na bolha: consulta do histórico, DTO de resposta,
tipo do frontend, componente. Campo anulável em toda a cadeia — mensagem que não falhou não tem
motivo, e isso não é caso excepcional.

Não invente formato novo: o JSONB já é `{codigo, titulo}`. Leve os dois. O `titulo` é o texto do
provedor, em inglês, e é o que garante que **nunca** exista falha sem explicação.

## Bloco 2 — Texto humano, com escape

Traduza para português os códigos que realmente aparecem, e **caia no `titulo` do provedor para
qualquer outro**. A regra é: nenhuma falha pode aparecer sem motivo na tela.

Sugestão de partida, em `textos.json` (com `schema.ts` acompanhando):

| código | texto |
| --- | --- |
| `131026` | Número não recebe mensagens no WhatsApp |
| `131047` | Fora da janela de 24 horas — só template aprovado |
| `131053` | Formato de arquivo não suportado |
| `132000` | Template com número de parâmetros diferente do aprovado |
| `132001` | Template não existe nesse idioma |
| outro | usa o `titulo` que veio do provedor |

O código cru fica acessível para diagnóstico (`title` do elemento ou equivalente), sem poluir a
bolha. Não traduza inventando: se não souber o texto de um código, não o inclua na tabela — o
fallback resolve.

## Bloco 3 — Reenviar só quando pode funcionar

Botão que não pode dar certo é pior que botão ausente: ele produz o `131047`.

- **`131026`** — permanente para aquele número. **Sem Reenviar.**
- **Mensagem que era template** — o Reenviar atual manda como texto puro
  (`pagina-atendimentos-cliente.tsx:279` monta `{atendimentoId, leadId, conteudo}` e não passa
  `template`), então é 422 garantido fora da janela. **Sem Reenviar**; o caminho é "Nova mensagem"
  e escolher o template outra vez.
- **Texto livre com a janela fechada** — **sem Reenviar** enquanto a janela estiver fechada; a tela
  já sabe o estado da janela (`estadoDaJanela` no composer).
- Nos demais casos o Reenviar continua como está.

Quando o botão não aparecer, o motivo já está no balão pelo Bloco 2 — o atendente lê em vez de
tentar.

## Testes obrigatórios

1. Backend: histórico devolve `erroEntrega` preenchido para mensagem `FALHOU` com a coluna
   preenchida, e nulo para mensagem entregue. IT sobre o endpoint real, não só unitário.
2. Frontend: balão com `131026` mostra o texto em português **e não mostra Reenviar**.
3. Balão com código desconhecido mostra o `titulo` do provedor — **nunca** falha sem motivo.
4. Balão de template que falhou não mostra Reenviar.
5. Texto livre que falhou com a janela **aberta** continua mostrando Reenviar (sem regressão).
6. Mensagem entregue não mostra nem motivo nem Reenviar.

## Fora do escopo — não faça

**Não tente fazer o Reenviar de template funcionar.** Para isso a identidade do template (nome,
idioma, parâmetros) precisaria sobreviver na mensagem persistida e na mensagem otimista, e hoje não
sobrevive em nenhuma das duas. É etapa própria, com decisão de produto sobre o que persistir.

**Não mexa na causa do `131026`.** Número que não recebe no WhatsApp é qualidade de cadastro, não
código — entra na conversa dos contatos importados sem DDD, não aqui.

**Não mexa** no fluxo de envio, na janela de 24h, na outbox, no tradutor do provedor ou em RN-CRM.

## Definição de pronto

- Toda mensagem `FALHOU` mostra um motivo na tela — traduzido quando conhecido, o do provedor
  quando não.
- Reenviar desaparece nos casos em que não pode funcionar, e continua nos que pode.
- `./mvnw -pl crm-app -am verify` verde; testes, typecheck, lint e build do frontend verdes.
- Relatório final com os sete itens do `AGENTS.md`.
