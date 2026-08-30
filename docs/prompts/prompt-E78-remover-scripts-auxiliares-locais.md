# Prompt E78 — Remover scripts auxiliares locais antes da promoção

## Objetivo

Limpar da promoção para `main` três scripts auxiliares de uso pontual que foram versionados acidentalmente na E67b e chegaram ao merge local `89d7dfc`:

- `script.js`;
- `update-sidebar-test.js`;
- `update-textos.js`.

Eles não fazem parte do runtime do CRM, não são referenciados pelo `package.json`, pelo build ou pela aplicação e não devem ser publicados como parte do produto.

## Evidências já verificadas

- os três arquivos estão no commit `56ede13` e no merge `89d7dfc`;
- `script.js` contém sintaxe inválida e caminho absoluto para outro worktree;
- os scripts usam `fs.readFileSync`/`fs.writeFileSync` para alterar arquivos do projeto;
- nenhuma referência operacional foi encontrada no repositório para executá-los;
- o merge `89d7dfc` ainda não foi enviado para `origin/main`.

## Autorização e árvore

Não remova nada até haver autorização explícita para esta limpeza.

Após a autorização, trabalhe exclusivamente em:

- worktree: `C:\Users\marcondes\Desktop\projeto_matriz-main`;
- branch: `main`;
- base atual esperada: `89d7dfc`.

Não altere `hotfix`, `fixtwo`, `hmlgc` ou qualquer outro worktree. Preserve os prompts e artefatos não rastreados das outras árvores.

Antes de editar, confirme:

```text
git branch --show-current
git status --short --branch
git log -1 --format=fuller
git diff --stat
git diff --cached --stat
```

Se a branch não for `main`, se `HEAD` divergir, se houver mudanças locais não relacionadas ou se houver operação Git em andamento, pare e relate.

## Leitura obrigatória

Leia integralmente, nesta ordem:

1. `AGENTS.md`;
2. `docs/13-estado-do-projeto.md`;
3. `docs/prompts/COMO-ESCREVER-PROMPTS.md`;
4. o relatório da promoção E77;
5. este prompt.

## Verificação antes da remoção

Confirme novamente que os arquivos não são necessários:

1. procure referências a cada nome em todo o repositório, excluindo `node_modules`, `frontend/.playwright-cli` e `frontend/output`;
2. procure referências em `package.json`, scripts npm, Dockerfiles, workflows, Makefiles e documentação operacional;
3. confirme que nenhum comando de build, teste, deploy ou inicialização depende deles;
4. confirme que nenhum segredo ou dado de produção está contido neles;
5. registre o resultado no relatório.

Se surgir referência válida, não remova o arquivo automaticamente: pare e peça decisão.

## Alteração permitida

Remova somente estes três arquivos auxiliares:

```text
script.js
update-sidebar-test.js
update-textos.js
```

Não altere migrations, código da aplicação, testes funcionais, catálogo, frontend ou backend para compensar a remoção. Não substitua os scripts por novos scripts.

Depois da remoção:

1. confirme que os três arquivos não existem mais na árvore;
2. confirme que nenhum outro arquivo foi alterado além da exclusão dos três;
3. execute `git diff --check`;
4. revise `git diff --stat` e `git diff --name-status`;
5. confirme que os artefatos `frontend/.playwright-cli/` e `frontend/output/` continuam fora do Git.

## Validação

No commit limpo resultante, valide pelo menos:

```text
cd backend
./mvnw clean verify
```

Na raiz do frontend:

```text
npm run lint
npm run typecheck
npm test -- --run
npm run build
```

Não declare CI verde com base em execução local. Se o build ou testes falharem, informe o erro e não faça push.

## Commit e push

Depois da remoção e das validações, pare antes de criar o commit se a autorização recebida cobrir apenas a remoção.

Se houver autorização explícita para commit, use uma mensagem Conventional Commit, por exemplo:

```text
chore: remover scripts auxiliares locais
```

Não faça push até receber autorização explícita para publicar `main`. O merge `89d7dfc` não deve ser enviado enquanto esses scripts permanecerem versionados.

## Critérios de aceite

- [ ] Os três scripts foram confirmados como não utilizados pela aplicação.
- [ ] Somente os três scripts foram removidos.
- [ ] Nenhum arquivo de outra etapa ou worktree foi descartado.
- [ ] Nenhuma migration, API, regra de negócio ou configuração foi alterada.
- [ ] `git diff --check` passou.
- [ ] Validações exigidas foram executadas e reportadas.
- [ ] Artefatos temporários continuam fora do commit.
- [ ] Commit só foi criado com autorização explícita.
- [ ] Push só foi feito com autorização explícita.

## Relatório obrigatório

Informe:

1. branch, worktree, `HEAD` e status antes e depois;
2. referências pesquisadas e prova de que os scripts não são usados;
3. arquivos removidos e quantidade;
4. resultado dos testes e build;
5. divergências ou novos riscos;
6. commit criado ou não criado;
7. push realizado ou não realizado;
8. CI com número da run ou “CI não verificado”.
