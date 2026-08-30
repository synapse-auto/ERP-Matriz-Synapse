# Prompt E77 — Promover `hmlgc` para `main`

## Objetivo

Promover para a `main` a versão de homologação já integrada e validada na `hmlgc`.

O estado esperado no início desta etapa é:

- `hmlgc` / `origin/hmlgc`: `b35f1f82abe5496b7f0f3da5053a8d6022cd5bce`;
- `main` / `origin/main`: `43bf65e687795441bdb232c680cc7a01c9f9a837`;
- `hmlgc` contém as integrações da `hotfix` e da `fixtwo`;
- a E75 está publicada na `hmlgc`;
- `frontend/.playwright-cli/` e `frontend/output/` são artefatos locais e não devem entrar no Git.

Se algum SHA divergir, não presuma que a diferença é segura. Pare e relate o estado real antes de fazer o merge.

## Regra de autorização

Este prompt descreve a promoção. O agente pode preparar e executar a validação e o merge conforme a autorização recebida para esta etapa, mas deve pedir autorização explícita separada antes de fazer push, caso ela não esteja sendo concedida junto com a execução.

Não faça deploy, criação de tag, alteração no Dokploy ou exclusão de branches nesta etapa.

## Worktree e branch corretos

Não use automaticamente `C:\Users\marcondes\Desktop\projeto_matriz`: essa árvore estava associada à `hotfix`.

Antes de qualquer ação:

1. execute `git worktree list --porcelain`;
2. identifique um worktree da `main` limpo;
3. se não existir, crie um worktree dedicado, por exemplo:

```text
C:\Users\marcondes\Desktop\projeto_matriz-main
```

apontando para a branch `main`, sem mover ou limpar o worktree da `hotfix`, da `fixtwo` ou da `hmlgc`;

4. no worktree da `main`, confirme `git branch --show-current`, `git status --short --branch` e o SHA do `HEAD`;
5. não altere arquivos não rastreados de nenhuma outra árvore.

O merge deve ser executado somente estando na branch `main` do worktree dedicado.

## Leitura obrigatória

Leia integralmente, nesta ordem:

1. `AGENTS.md`;
2. `docs/13-estado-do-projeto.md`;
3. `docs/prompts/COMO-ESCREVER-PROMPTS.md`;
4. `docs/prompts/prompt-E73-homologacao-integrada-hotfix-fixtwo.md`;
5. `docs/prompts/prompt-E75-correcao-visual-e-validacao-E74.md`;
6. o relatório final da homologação da `hmlgc`.

Relatórios são contexto, não evidência. Confirme os refs, o histórico e o conteúdo no Git.

## Pré-validação do histórico

No worktree da `main`:

1. atualize as referências remotas com `git fetch origin main hmlgc`;
2. confirme que `origin/main` ainda é a base esperada;
3. confirme que `origin/hmlgc` aponta para a versão homologada aprovada;
4. confirme que `b35f1f8` é descendente da integração da `hotfix` (`74e528d`) e da `fixtwo` (`79b7b71`), usando `git merge-base --is-ancestor`;
5. confirme que `main` está limpa e não possui commits locais não publicados;
6. confirme que não há merge em andamento, `CHERRY_PICK_HEAD`, `REVERT_HEAD` ou rebase ativo.

Se `origin/main` avançou desde a conferência, se `origin/hmlgc` mudou ou se a `main` não estiver limpa, pare. Não faça rebase, reset ou merge adicional para “consertar” a situação.

## Operação de merge

Depois de todos os checks:

1. faça o merge da referência remota homologada, preferencialmente:

```text
git merge --no-ff origin/hmlgc -m "merge: promover hmlgc para main"
```

2. use `--no-ff` para preservar a fronteira de promoção da homologação;
3. não faça squash, rebase ou alteração manual do conteúdo do merge;
4. se houver conflito, pare imediatamente e liste os arquivos. Não escolha automaticamente a versão de `main` ou `hmlgc`;
5. se o merge terminar, confirme o novo SHA, os pais do commit e o conteúdo efetivamente incluído;
6. execute `git diff HEAD^ HEAD --check` e `git status --short --branch`.

Não inclua no commit:

- `frontend/.playwright-cli/`;
- `frontend/output/`;
- credenciais, `.env`, logs ou artefatos de execução;
- alterações preexistentes do worktree da `hotfix`;
- arquivos que não pertençam à `hmlgc` homologada.

## Validação pós-merge

No commit resultante da `main`, execute:

### Backend

```text
cd backend
./mvnw clean verify
```

Informe Java, módulos, testes do `crm-app`, falhas, erros, skips e execução de Testcontainers, Spotless e ArchUnit.

### Frontend

Na raiz do frontend, em instalação limpa e sem junction apontando para fora da raiz:

```text
npm ci
npm run lint
npm run typecheck
npm test -- --run
npm run build
```

Informe quantidade de arquivos/testes e warnings. Os três warnings de lint já conhecidos não devem ser apresentados como zero warnings se ainda existirem.

### Integridade e escopo

Confirme:

- `git diff --check`;
- ausência de arquivos backend/migration inesperados no merge;
- ausência de alterações de autenticação, RLS, contratos ou regras de negócio;
- presença da fonte local Hanken Grotesk;
- presença das telas administrativas e do refinamento visual da `hmlgc`;
- `main` sem conteúdo que não estivesse na homologação aprovada.

## Push e CI

Antes do push, informe o SHA do merge e peça autorização explícita se ela não tiver sido dada junto com esta etapa.

Com autorização:

```text
git push origin main
```

Depois do push:

1. confirme `git ls-remote origin refs/heads/main`;
2. aguarde a execução do CI;
3. consulte a run correspondente ao SHA publicado;
4. informe o número da run e o resultado de cada job;
5. se o CI falhar, não declare promoção concluída e não faça novo commit corretivo sem diagnóstico.

“CI verde” só pode ser escrito com o número da run. Sem push ou sem run identificada, escreva “CI não verificado”.

## Critérios de aceite

- [ ] Merge executado no worktree correto da `main`.
- [ ] `hmlgc` homologada contém as integrações de `hotfix`, `fixtwo` e E75.
- [ ] Merge foi feito com `--no-ff`, sem squash ou rebase.
- [ ] Nenhum conflito foi resolvido silenciosamente.
- [ ] Artefatos locais, segredos e alterações de outras árvores ficaram fora do commit.
- [ ] Backend `clean verify` passou no commit resultante, se a validação completa for executada.
- [ ] Frontend lint, typecheck, testes, build e `git diff --check` foram executados e reportados.
- [ ] Nenhuma regra funcional, autorização, RLS, migration ou contrato foi alterada pela promoção.
- [ ] Push só foi feito após autorização explícita.
- [ ] CI só foi declarado verde com número de run.
- [ ] Nenhum deploy ou tag foi criado sem autorização específica.

## Relatório final obrigatório

Informe, nesta ordem:

1. worktree usado, branch, SHA anterior e SHA do merge;
2. pais do commit de merge e referência exata promovida;
3. status local, status remoto e quantidade de arquivos incluídos;
4. confirmação de que `hotfix`, `fixtwo` e `hmlgc` de origem não foram alteradas;
5. cada critério de aceite com ✅, ⚠️ ou ❌ e evidência;
6. resultado do backend e frontend;
7. número da run do CI, ou “CI não verificado”;
8. push realizado ou não realizado;
9. deploy/tag realizados ou não realizados;
10. divergências, bugs e decisões necessárias.
