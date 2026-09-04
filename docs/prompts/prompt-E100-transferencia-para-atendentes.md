# Prompt E100 — Transferência entre atendentes liberada

> Leia `AGENTS.md` e `CLAUDE.md`. Branch própria (`feat/transferencia-entre-atendentes`) e PR.
> **Sem merge, sem deploy.** Toca um módulo do backend e o frontend: `./mvnw -pl <modulo> -am verify`
> mais a suíte do frontend. Sem migration.

---

## O pedido

Atendente passa a poder transferir um atendimento para **outro atendente**. Hoje ele só consegue
devolver para a IA. Isso acontece o tempo todo entre eles e é para liberar.

## Bloco 1 — O backend já permite. O bloqueio é na tela.

Confirme lendo antes de mexer em qualquer regra:

- `TransferirAtendimentoUseCase.executar` tem
  `@PreAuthorize("hasAnyRole('ATENDENTE','GESTOR','SUBGESTOR','ADMINISTRADOR')")` — **ATENDENTE já
  está lá.**
- E o destino já é validado: `destinos.exigirAtendenteAtivo(paraAtendenteId)` roda no caminho humano
  (foi o buraco que a E53 fechou). Liberar a tela **não** reabre aquele bug — confirme que essa
  linha continua no caminho de `executar`, e diga no relatório que confirmou.

O bloqueio real está em `frontend/src/components/atendimentos/dialogo-transferir.tsx`:

```tsx
const podeVerEquipe = papel !== null && papel !== "ATENDENTE";
```

e no comentário logo acima, que afirma que *"a trava de autorização do backend só permite devolver
pra IA de qualquer forma"*. **Esse comentário está errado hoje.** Ou ele descreveu uma versão
anterior, ou se referia a outra coisa. Apague ou corrija — comentário que mente é pior que
comentário nenhum.

## Bloco 2 — O que realmente falta: a lista de destinos

A tela monta os candidatos a partir de `listarEquipe()` → `GET /api/v1/usuarios`, que devolve o
registro inteiro do time. **Não libere esse endpoint para atendente.** Ele carrega e-mail, papel e
outros dados que não são da conta de um atendente só para escolher para quem passar a conversa.

Crie um endpoint estreito, de leitura, que devolva **apenas o necessário para escolher um destino**:
identificador e nome dos atendentes ativos. Nada de e-mail, papel, presença, métricas.

Antes de desenhar, leia `AtendentesDisponiveisInternalController`
(`GET /internal/v1/atendentes/disponiveis`): ele já resolve praticamente esse problema para a
Automação, devolvendo os atendentes ativos e disponíveis na ordem recomendada de distribuição. **Não
exponha o endpoint interno para o browser** — ele é do contrato `/internal/v1`, autenticado por
`X-Synapse-Token`, e misturar as duas portas é o começo de um vazamento. Mas leia como ele monta a
consulta e reaproveite o que fizer sentido no lado da aplicação, em vez de escrever uma terceira
definição de "quem pode receber um atendimento".

Decida e **relate**: a lista de destinos usa o mesmo critério do interno (ativos **e** disponíveis
para IA) ou só "ativos"? Para transferência entre colegas, "disponível para IA" provavelmente é
critério errado — um atendente indisponível para a IA continua podendo receber uma conversa de um
colega. Escolha, justifique, e não deixe implícito.

## Bloco 3 — O que não muda

- Continuar podendo devolver para a IA (`paraAtendenteId = null`). É o comportamento que o atendente
  já tem e não pode sumir.
- A RN-CRM-06 continua valendo: quem envia mensagem manual leva o lead. Transferir **não** é enviar;
  não invente efeito colateral novo aqui.
- `/internal/v1` intocado: `ContratoInternalV1IT` e `internal-v1-snapshot.json` não podem mudar.
- Um atendente não pode transferir um atendimento que não enxerga. A RLS já cuida disso — confirme
  que o resultado é 404, não 403.

## Bloco 4 — Testes

- Atendente transfere para outro atendente ativo: 200, o atendimento troca de responsável, e o
  evento da timeline registra quem pediu.
- Atendente transfere para um id que não é atendente ativo: recusado pelo mesmo caminho da E53
  (`AtendenteDestinoInvalidoException`), não 500.
- Atendente continua conseguindo devolver para a IA.
- O endpoint novo, chamado por um atendente, **não** devolve e-mail nem papel — teste isso
  explicitamente, é o motivo de ele existir.
- Frontend: com `papel === "ATENDENTE"` o diálogo agora lista colegas; o botão de devolver para a IA
  continua lá.

## Verificação

```
./mvnw -pl <modulo-tocado> -am verify      # na raiz de backend/
npm run lint && npm run typecheck && npm run test && npm run build   # em frontend/
```

Se você acabar tocando mais de um módulo, suba um degrau e rode `./mvnw verify` no reator.

## Relatório

1. Confirmação de que `exigirAtendenteAtivo` continua no caminho humano, com arquivo e linha.
2. A rota nova, o que ela devolve, e por que não é o `/api/v1/usuarios`.
3. O critério que você escolheu para "quem pode receber" e o porquê.
4. O que você fez com o comentário errado do `dialogo-transferir.tsx`.
