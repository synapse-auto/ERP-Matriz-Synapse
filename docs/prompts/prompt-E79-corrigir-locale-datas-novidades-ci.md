# Prompt E79 — Corrigir teste de datas das Novidades no CI

## Objetivo

Corrigir a falha do job de frontend causada pelo teste de `NovidadesDialog` e tornar a formatação das datas determinística em qualquer ambiente de CI.

A sugestão de procurar um elemento cujo `textContent` contenha “22”, “julho” e “2026” não deve ser aplicada cegamente. O componente atual usa `Intl.DateTimeFormat(undefined, ...)`, portanto o resultado depende do locale do processo do runner. Além disso, um matcher de função sem restrição de elemento pode casar o cabeçalho e seus contêineres pais.

## Evidência confirmada

Na `main` em `7d729f8`:

- `frontend/src/components/shell/novidades-dialog.tsx` formata a data com `new Intl.DateTimeFormat(undefined, { dateStyle: "long" })`;
- `frontend/src/components/shell/novidades-dialog.test.tsx` espera `/22 de julho de 2026/i` e `/13 de julho de 2026/i`;
- o teste passa em ambiente local com locale português, mas falha no CI quando o locale padrão do runner não é português;
- o elemento de data é um `h3`, portanto o teste pode usar uma consulta semântica e exata por heading;
- a interface do produto está em `pt-BR`, conforme `<html lang="pt-BR">` e o catálogo atual.

## Branch e worktree

Não corrija diretamente a `main`.

Crie uma branch de trabalho derivada da `main` atual, por exemplo:

```text
fix/novidades-locale-ci
```

Use um worktree dedicado, sem tocar em:

- `C:\Users\marcondes\Desktop\projeto_matriz` (`hotfix`);
- `C:\Users\marcondes\Desktop\projeto_matriz-fixtwo`;
- `C:\Users\marcondes\Desktop\projeto_matriz-hmlgc`;
- `C:\Users\marcondes\Desktop\projeto_matriz-main` (`main`) sem antes confirmar que a nova branch está sendo usada.

Antes de alterar, confirme branch, worktree, `HEAD`, status, merge/rebase em andamento e diff. Preserve artefatos não rastreados de todas as árvores.

Não faça commit ou push sem autorização explícita posterior.

## Leitura obrigatória

Leia integralmente, nesta ordem:

1. `AGENTS.md`;
2. `docs/13-estado-do-projeto.md`;
3. `docs/prompts/COMO-ESCREVER-PROMPTS.md`;
4. este prompt.

## Correção obrigatória

### Produção

Altere `frontend/src/components/shell/novidades-dialog.tsx` para que a data seja formatada de forma determinística em português brasileiro:

```ts
new Intl.DateTimeFormat("pt-BR", { dateStyle: "long" })
```

Não use `undefined`, locale do sistema operacional, locale inferido do runner ou string manual montada por partes.

Mantenha:

- o horário `T12:00:00`, para evitar deslocamento de data por fuso;
- a ordenação por data;
- o agrupamento de itens;
- a tag `NOVO`;
- o catálogo de textos;
- as rotas e o comportamento visual existentes.

Não crie configuração por cliente para resolver este caso: todo o produto atual é `pt-BR`, e o documento HTML de referência está em português.

### Teste

Altere `frontend/src/components/shell/novidades-dialog.test.tsx` para consultar os cabeçalhos de data de forma semântica e sem ambiguidade:

```ts
expect(
  screen.getByRole("heading", {
    level: 3,
    name: "22 de julho de 2026",
  }),
).toBeInTheDocument();

expect(
  screen.getByRole("heading", {
    level: 3,
    name: "13 de julho de 2026",
  }),
).toBeInTheDocument();
```

Se a formatação do Testing Library exigir uma pequena adaptação, mantenha a consulta limitada ao `h3`/heading e com nome exato. Não use `getByText` em um contêiner genérico que possa casar o Dialog inteiro.

Mantenha as asserções dos três títulos e confirme que somente um item possui a tag `NOVO`.

Adicione, se necessário, um teste regressivo que prove que a saída permanece em português independentemente do locale padrão do processo. O teste deve verificar comportamento observável, não apenas procurar o texto-fonte da implementação.

## O que não fazer

- Não alterar o texto para inglês ou aceitar vários idiomas neste momento.
- Não mascarar a falha com `any`, `eslint-disable`, timeout maior ou retry.
- Não usar matcher que aceite qualquer pai com os mesmos fragmentos.
- Não alterar dados de produção, catálogo, backend, API, migration ou regra funcional.
- Não adicionar strings novas fora do catálogo.
- Não fazer mock de `Intl.DateTimeFormat` de modo que o teste deixe de exercitar a implementação real.
- Não alterar a aparência do modal para fazer o teste passar.
- Não tocar em `main`, `hotfix), `fixtwo) ou `hmlgc`.

## Validação

Na raiz do frontend, execute:

```text
npm ci
npm run lint
npm run typecheck
npm test -- --run
npm run build
```

O teste que falhou no CI deve ser executado isoladamente e também dentro da suíte completa. Informe:

- resultado do teste isolado;
- resultado da suíte completa;
- quantidade de arquivos e testes;
- erros e warnings;
- resultado do build;
- `git diff --check`.

Como a correção deve ser somente frontend/teste, confirme que nenhum arquivo backend foi alterado. Se backend ou configuração compartilhada for tocado por necessidade, execute obrigatoriamente:

```text
cd backend
./mvnw clean verify
```

Não declare CI verde sem número da run remota. A execução local não substitui o CI.

## Critérios de aceite

- [ ] A data das Novidades usa locale explícito `pt-BR`.
- [ ] O teste não depende do locale do sistema operacional ou do runner.
- [ ] O teste consulta o heading de data sem risco de múltiplos matches.
- [ ] O teste isolado e a suíte completa passam.
- [ ] Lint, typecheck e build passam.
- [ ] Não foi usado timeout aumentado, retry, `any` ou desativação de lint para esconder a falha.
- [ ] Tag `NOVO`, agrupamento e ordenação permanecem funcionando.
- [ ] Nenhum backend, API, migration, catálogo ou regra de negócio foi alterado.
- [ ] `git diff --check` passa.
- [ ] Nenhum commit ou push foi feito sem autorização.
- [ ] O CI só é declarado verde com número da run.

## Relatório obrigatório

Informe, nesta ordem:

1. branch, worktree, `HEAD), status e diff;
2. causa reproduzida e diferença entre ambiente local e CI;
3. arquivos alterados;
4. comportamento da formatação `pt-BR`;
5. teste isolado e suíte completa;
6. lint, typecheck, build e `git diff --check`;
7. backend tocado ou não;
8. divergências, bugs e decisões;
9. commit e push realizados ou não;
10. número da run do CI, ou “CI não verificado”.

O relatório deve deixar claro se a correção foi apenas preparada localmente ou se já foi publicada para disparar o CI.
