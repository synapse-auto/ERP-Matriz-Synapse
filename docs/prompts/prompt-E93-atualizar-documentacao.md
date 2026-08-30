# Prompt E93 — pôr a documentação de volta na realidade

> Leia `AGENTS.md`, `CLAUDE.md` e `docs/00-README.md` antes de começar.
> **Esta etapa não altera código de aplicação.** Só documentação, `.gitignore` e `AGENTS.md`.
> Trabalhe em branch própria (`docs/atualizar-estado-projeto`) e abra PR. **Não faça merge sozinho.**

---

## Contexto

A documentação descolou do repositório. `docs/13-estado-do-projeto.md` ainda diz "atualizado em
26/08, depois da E58", descreve o sistema como estando **em homologação** e aponta a **E32** como
próxima etapa. Desde então foram ~110 commits, 15 PRs mergeados e as migrations foram de `V43` a
`V47`. O produto está **em produção real** e o fluxo de trabalho mudou para **branch + Pull
Request**.

Esse arquivo é o primeiro que qualquer agente lê para se situar. Hoje ele desorienta.

## Bloco 0 — A regra que vale para a etapa inteira

**Reconstrua o estado a partir do repositório, não do que a documentação diz.** As fontes de verdade,
nesta ordem:

- `git log origin/main` e os merges de PR (a mensagem de cada merge diz qual branch entrou);
- a pasta de migrations, que é o registro mais confiável do que existe no banco;
- o código, quando a mensagem de commit for ambígua.

Se uma frase da documentação atual contradisser o repositório, **o repositório ganha** e você corrige
a frase. Se você não conseguir confirmar algo pelo repositório, **escreva que não foi confirmado** —
não complete a lacuna por plausibilidade. Documentação inventada é pior que documentação vazia,
porque a próxima pessoa acredita nela.

## Bloco 1 — `docs/13-estado-do-projeto.md`

Atualize:

- **O estado real:** produção, não homologação. Diga desde quando, se der para determinar pelo git.
- **A tabela de etapas.** Ela para na E58. Reconstrua da E59 em diante a partir dos merges de PR.
  A numeração **não é contígua** e há etapas com sufixo (E83b, E83c, E84c, E88b, E92b) — registre o
  que existe, não o que "deveria" existir. Para cada uma: número, o que entregou, e o SHA ou o número
  do PR.
- **O que entrou e a documentação não menciona.** Confirme cada um pelo código antes de listar:
  templates da Meta, avaliação de atendimento, reações de mensagem, responder/encaminhar, múltiplos
  anexos, catálogo de emoji, código numérico do lead, chat interno iniciado pela equipe.
  **Atenção:** `docs/16-acesso-da-automacao.md` e a documentação entregue ao Dylan afirmavam que
  **não existia nada sobre templates da Meta no CRM**. Isso mudou. Verifique e corrija onde aparecer.
- **O fluxo de trabalho novo:** branch por etapa, PR para a `main`, sem push direto. Diga explicitamente
  que agente **não faz merge** e **não faz deploy**.
- **As pendências reais.** Use `docs/prompts/pendencias-clickup-para-cursor.md` como ponto de partida,
  mas **ela já está desatualizada**: pelo menos a E86 (chat interno pela equipe), a E87
  (responder/encaminhar) e a correção dos balões de chat já estão na `main`. Confirme cada item da
  lista contra o `git log` e marque o que já foi feito.

## Bloco 2 — Os outros documentos

Não reescreva tudo. Vários foram atualizados em 28/08 e podem estar bons. Para cada um, **confira e
diga no relatório se estava certo ou o que você mudou**:

- `docs/11-banco-atual.md` e `docs/03-modelo-dados-postgres.md` — precisam refletir até a `V47`.
- `docs/16-acesso-da-automacao.md` e `docs/integracao-automacao-dylan.html` — são o contrato que o
  Dylan lê. Se surgiu rota `/internal/v1` nova depois da E51, ela precisa estar lá; se sumiu alguma,
  idem.
- `docs/22-bugs-abertos-26-08.md` — é um documento datado, de um dia específico. **Não o atualize
  como se fosse vivo.** Se todos os itens estiverem fechados, acrescente uma linha no topo dizendo que
  está encerrado e em que data, e mantenha o resto como registro histórico.
- `docs/14-pendencias-de-funcionalidade.md` — reconcilie com o que você apurou no Bloco 1.

## Bloco 3 — Arquivos soltos

- Há cerca de vinte prompts em `docs/prompts/` sem commit, da E70 em diante, mais
  `pendencias-clickup-para-cursor.md` e `pendencia-E88-download-midia-401.md`. Commite todos: são o
  registro de como cada etapa foi pedida, e é o que permite auditar uma decisão meses depois.
- Na raiz há `.tmp-crm-mobile.html`, `.tmp-extract-html.js` e `.tmp-login-mobile.html`. A E78 já
  removeu scripts auxiliares locais uma vez, então isto é recorrência: acrescente um padrão
  `.tmp-*` ao `.gitignore` em vez de só apagar os três.

## Bloco 4 — A regra de migration que falta no `AGENTS.md`

O fluxo de branches paralelas já produziu colisão de numeração: existiram `V44__reacoes_de_mensagem.sql`
e `V44__reserva_webhook_avaliacao.sql` ao mesmo tempo, e uma foi renumerada no meio do caminho.

Deu certo por acaso. Com produção no ar, o modo de falhar é ruim: duas branches criam o mesmo `V4x`,
uma faz merge, a outra continua passando no CI — que roda o schema do zero — e o Flyway quebra no
**boot do container em produção**, porque lá o histórico já tem aquele número aplicado com outro
checksum. O sintoma é a aplicação não subir depois de um deploy que parecia verde.

Acrescente ao `AGENTS.md`, na seção de banco:

- antes de criar migration, `git fetch` e tirar o próximo número a partir de **`origin/main`**, nunca
  do local;
- ao rebasear uma branch que tem migration, conferir se o número ainda é o próximo livre e renumerar
  se não for;
- migration já mergeada na `main` **nunca** é editada — corrige-se com uma nova.

Escreva isso com o motivo junto, não só a regra. Regra sem motivo é a primeira coisa que alguém
contorna quando está com pressa.

## O que não fazer

- Nada de código de aplicação, `pom.xml`, dependência ou migration nova.
- Nada de merge, push na `main` ou deploy.
- Não apague documento histórico para "limpar". `docs/22` e os prompts antigos são registro.
- Não invente etapa, data ou SHA que você não confirmou no git.

---

## Verificação

- `git log origin/main` bate com a tabela de etapas que você escreveu — confira por amostragem, pelo
  menos cinco linhas com o PR correspondente.
- A última migration citada na documentação é a mesma que existe na pasta.
- Todo item que você marcou como "feito" na lista de pendências tem um commit que prova.
- `.gitignore` cobre `.tmp-*` e a árvore fica limpa em `git status`.
- Nenhum arquivo fora de `docs/`, `.gitignore` e `AGENTS.md` aparece no diff.

## Relatório

1. Da E59 até a atual: quais etapas você conseguiu reconstruir e quais ficaram sem confirmação.
2. Quais documentos estavam certos e quais você corrigiu.
3. Quais itens da lista do ClickUp já estavam feitos.
4. O que você **não** conseguiu confirmar pelo repositório e ficou marcado como incerto.
