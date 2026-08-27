# Prompt E58 — publicar as seis etapas e provar que a imagem existe

> Leia `AGENTS.md`, `CLAUDE.md`, `docs/13-estado-do-projeto.md` e `docs/22-bugs-abertos-26-08.md`.
> **Esta etapa tem autorização explícita para `git push` na `main`.** É a única autorização desta
> etapa: nada de `--force`, nada de reescrever histórico já publicado, nada de deploy (o deploy é
> manual, no Dokploy, e é do Marcondes).

---

## Contexto

Seis commits locais aguardam publicação, nesta ordem:

```
0d467a2 fix: validar destino da transferencia humana        (E53)
c8d3da4 fix: reconciliar fontes de verdade do chat          (E54)
20c62f2 fix: separar rede da transacao da outbox            (E55 bloco 1)
4bf3d76 feat: leitura de atendimento por usuario            (E55 bloco 2, migration V41)
40e8e2f feat: uma conversa por cliente                      (E57)
0987732 feat: acabamento do chat e ajustes finos            (E56)
```

Vão para um ambiente com **número de WhatsApp real conectado**. Cada etapa foi verificada no HEAD
dela; esta valida o **estado final** e publica.

## Bloco 0 — Uma correção antes de publicar, e só ela

A E56 trocou o `field-sizing-content` por autosize no `onChange` em `components/ui/textarea.tsx`. O
ajuste de altura está correto (reseta `height = "auto"` antes de medir, então o campo encolhe), mas
**só roda quando a pessoa digita**. Todo valor definido por código passa batido:

- o composer **continua alto depois de enviar** — `onSalvo={() => setTexto("")}` limpa o texto sem
  disparar `onChange`, e a caixa fica com a altura da mensagem anterior;
- **mensagem rápida não faz a caixa crescer** — `setTexto(mensagem.conteudo)` é programático, e é
  justamente o caso em que o texto é longo;
- **no primeiro render a altura não é calculada** — um follow-up com mensagem de cinco linhas abre
  com barra de rolagem em vez de aberto, que é o comportamento que o `field-sizing-content` dava de
  graça.

Corrija no próprio `textarea.tsx`: além do `onChange`, ajuste a altura num `useLayoutEffect`
disparado por `props.value`. Todos os usos do componente são controlados, então o efeito cobre o
mount e qualquer `setTexto` programático. Vai precisar de um `ref` interno mesclado com o `ref` que
vier de fora — não descarte o `ref` do chamador.

Teste: valor definido por código (não digitado) resulta em altura de conteúdo, e limpar o valor
devolve a altura mínima.

**Commit à parte**, antes do commit de documentação. Esta é a **única** alteração de código
autorizada na E58.

## Bloco 1 — Verificar o estado final, não os estados intermediários

Antes de empurrar, no HEAD atual:

- `cd backend && ./mvnw clean verify` — reator inteiro, verde.
- `cd frontend && npm run lint && npm run typecheck && npm test && npm run build` — todos verdes.
- `git status` limpo quanto a código. Só devem restar arquivos de documentação e diretórios de
  trabalho.

Se qualquer um falhar, **pare e relate**. Não conserte por conta própria: uma correção às pressas em
cima de seis etapas é como se perde a rastreabilidade de qual delas quebrou.

## Bloco 2 — Um commit de documentação, antes do push

Estão fora de controle de versão documentos que fazem parte do registro destas etapas:

- `docs/21-entrega-contratos-internos-automacao.md`
- `docs/22-bugs-abertos-26-08.md`
- `docs/prompts/prompt-E50…E57`
- `docs/integracao-automacao-dylan.html` (modificado)
- `docker/provisionamento/automacao-padrao.sql`

Commite esses num commit só, de documentação.

**Não commite** `_to_delete/`, `output/` e `tmp/`. Acrescente os três ao `.gitignore` no mesmo commit
— são diretórios de trabalho que já apareceram como ruído em várias etapas.

Atualize também, no mesmo commit:

- **`docs/13-estado-do-projeto.md`** — é o documento que qualquer agente lê primeiro e ele ainda
  descreve o handoff anterior. Registre: a transferência humana passou a validar destino; o chat tem
  uma conversa por cliente; a leitura é por usuário (V41); a outbox envia fora de transação.
- **`docs/22-bugs-abertos-26-08.md`** — marque o 6, 7 e 8 como corrigidos, com o SHA de cada um. Os
  outros já estão marcados. **Não reescreva a descrição dos defeitos**: elas descrevem o bug
  original de propósito, para quem for ler o histórico.

## Bloco 3 — Push e a única prova que vale

`git push origin main`.

**Depois disso, a parte que já falhou duas vezes neste projeto:**

CI verde **não** significa que a imagem existe. O que publica a imagem é o job **`imagens`**, que
depende de `backend`, `frontend` e `infra`. Já houve deploy fantasma duas vezes aqui — a tag foi
apontada antes de a imagem existir, as tasks do Swarm ficaram `Rejected` com
`No such image: …:<sha>`, e o container antigo continuou servindo como se nada tivesse acontecido.

Então:

1. Acompanhe a run até o fim. Não relate enquanto o job `imagens` não terminar.
2. **Relate o SHA curto exato** que virou tag da imagem — é o que o Marcondes vai colar em
   `SYNAPSE_IMAGE_TAG`.
3. Se o job `imagens` falhar ou for pulado, **diga isso em letras claras** e não diga que está
   pronto para deploy.

## Bloco 4 — O que o Marcondes precisa saber antes de apontar a tag

Monte, no relatório, um checklist curto de publicação com o que esta entrega tem de sensível. Não
invente itens; são estes, e você confirma cada um lendo o código:

- **A V41 roda no start.** Confirme que é aditiva (`CREATE TABLE` + backfill) e que
  `atendimento.lido_ate` continua existindo e não foi apagada. Diga explicitamente se o rollback para
  a imagem anterior funciona — a imagem antiga lê `lido_ate`, que a V41 preservou.
- **Deploy reinicia o container, e é aí que a outbox tem sua janela.** Se o processo morrer entre a
  Meta aceitar e o resultado ser gravado, nada marca que houve despacho; passados 30 segundos do
  lease a linha volta elegível e a mensagem **sai de novo**. Diga que a recomendação é publicar com a
  conversa parada, e dê a consulta que mostra linhas reservadas e não publicadas:
  `SELECT id, criado_em, proxima_tentativa_em, tentativas, ultimo_erro FROM outbox_evento
   WHERE publicado_em IS NULL AND esgotado_em IS NULL ORDER BY criado_em;`
- **O que olhar depois de subir**, em ordem: `/health/critical`; uma conversa abre e mostra o
  histórico unificado com o marco entre atendimentos; enviar uma mensagem e ver **uma** bolha;
  transferir para um atendente e ver o cabeçalho mudar; o ponto azul sumir ao abrir.
- Se algo estiver errado, o caminho de volta é apontar `SYNAPSE_IMAGE_TAG` para o SHA anterior — diga
  qual é.

## O que não fazer

- Nada de `--force`, `rebase` do que já está no `origin`, ou merge de branch.
- Nada de deploy, de mexer no Dokploy ou em variável de ambiente.
- Nada de corrigir código além do Bloco 0. Se a verificação do Bloco 1 falhar, o resultado desta
  etapa é o relatório da falha — não conserte por conta própria.

---

## Relatório

1. Resultado das quatro verificações do Bloco 1.
2. SHA do commit do Bloco 0 e do commit de documentação.
3. Número da run de CI, estado de cada job, e **o SHA curto que virou tag da imagem**.
4. O checklist do Bloco 4, preenchido.
5. O SHA anterior, para rollback.
