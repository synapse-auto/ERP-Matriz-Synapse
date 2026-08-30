# Prompt E70 — corrigir auditoria da E67b e fechar a entrega

> Leia `AGENTS.md`, `docs/13-estado-do-projeto.md` e `docs/prompts/COMO-ESCREVER-PROMPTS.md` antes de alterar qualquer arquivo.
> Esta correção pertence à E67b e deve ser executada exclusivamente na branch `hotfix`, no worktree `C:\Users\marcondes\Desktop\projeto_matriz`.
> A branch `fixtwo` foi criada a partir da `main` para outra etapa e não contém a E67b; não a use nesta correção. Não faça commit ou push sem autorização explícita do Marcondes.
> Ao finalizar a validação: execute `cd backend && ./mvnw clean verify`. Só chame CI de verde se houver número de run remoto.

---

## Contexto — o relatório da E67b não corresponde integralmente à árvore
 
O relatório da E67b declarou frontend verde, mas a conferência reproduzível na árvore `hotfix` em `56ede13` encontrou:

```text
frontend/src/app/(shell)/administracao/page.test.tsx
  linhas 28, 35 e 42 — dois usos de any por teste, total de 6 erros
  @typescript-eslint/no-explicit-any

npm run lint
  ✖ 9 problems (6 errors, 3 warnings)
```

Os três warnings são os já conhecidos em `lista-mensagens.tsx`, `sidebar.tsx` e `http-client.ts`; os seis erros são novos e bloqueiam a definição de pronto. `npm run typecheck`, `npm test -- --run` (47 arquivos, 187 testes) e `npm run build` passaram, mas isso não substitui o lint.

Também foi reproduzido:

```text
git diff 43bf65e..56ede13 --check
```

O comando aponta linhas em `frontend/src/components/shell/novidades-dialog.test.tsx`, `frontend/src/components/shell/novidades-dialog.tsx` e linhas finais dos prompts E65, E66, E67, E67b, E68 e E69.

O commit `56ede13` está em `hotfix`, enquanto `main`/`origin/main` e `fixtwo` estão em `43bf65e`. Além do código da E67b, o commit adicionou prompts E58–E69 e scripts utilitários. Portanto, o relatório não pode afirmar que os prompts “permaneceram não rastreados”: isso é falso para essa árvore. Não reescreva o histórico existente sem autorização; registre a divergência no relatório.

## Ponto de parada obrigatório — base da `hotfix`

Antes de codar, confirme:

```text
git branch --show-current
git rev-parse HEAD
git show --no-patch --oneline 56ede13
```

O trabalho só pode começar se a branch atual for `hotfix` e `56ede13` estiver no histórico atual. Se a branch ou a base forem diferentes, pare e informe. Não faça cherry-pick, merge, reset, cópia manual de arquivos ou alteração de `main`/`fixtwo` por conta própria. A `fixtwo` deve permanecer reservada para a etapa que foi criada para receber.

## Bloco 1 — eliminar os seis erros reais de lint

Arquivo confirmado:

```text
frontend/src/app/(shell)/administracao/page.test.tsx
```

Corrija os mocks do `useAuthStore` sem `any` explícito e sem mascarar o contrato com cast duplo indiscriminado. O teste deve continuar exercitando os três caminhos reais:

- `ATENDENTE`: chama `router.replace("/")` e não renderiza o placeholder;
- `GESTOR`: não redireciona e renderiza o placeholder;
- `ADMINISTRADOR`: não redireciona e renderiza o placeholder.

Use o tipo real ou um tipo de teste mínimo compatível com o estado selecionado pelo store. Não relaxe a configuração do ESLint, não adicione `eslint-disable`, não substitua as asserções por snapshots e não remova o teste negativo.

## Bloco 2 — limpar o diff verificável

Corrija somente whitespace introduzido pelos arquivos envolvidos na entrega. O resultado deve passar:

```text
git diff --check
```

Não faça reformatação global, não reescreva prompts históricos apenas para reduzir ruído e não altere conteúdo funcional ao limpar espaços. Se algum prompt já estiver rastreado na base da branch, preserve-o; apenas remova whitespace inválido do diff que a própria etapa produzir.

## Bloco 3 — preservar as decisões funcionais da E67b sem inventar escopo

Depois de resolver o bloqueio do lint, reconfirme no código e nos testes, sem aceitar o relatório como evidência:

- `PainelLateralLead` não renderiza seção de oportunidade mockada;
- telefone real usa destino `tel:` e o teste verifica o href como link acessível;
- `GESTOR` e `ADMINISTRADOR` veem Administração no sidebar; `ATENDENTE` não vê;
- a rota `/administracao` permanece um placeholder e a guarda atual é client-side;
- novidades são agrupadas por data real decrescente, usam `novo` do catálogo e não têm cores literais proibidas;
- ícones de Em Breve vêm de mapa seguro, com fallback conhecido;
- ações destrutivas usam tokens semânticos `destructive`.

Não transforme a guarda client-side em alegação de autorização backend. Como a tela ainda não possui operações administrativas concretas, mantenha a limitação documentada. Se forem encontradas operações, endpoints ou dados sensíveis nessa rota, pare antes de decidir uma proteção nova e relate o contrato necessário.

## Testes — a proteção nasce com um teste que a viola

Execute na `fixtwo`, nesta ordem ou equivalente:

1. `cd frontend && npm run lint` — zero erros; os três warnings preexistentes devem ser identificados nominalmente no relatório.
2. `cd frontend && npm run typecheck`.
3. `cd frontend && npm test -- --run` — manter os 47 arquivos e 187 testes, ou relatar exatamente qualquer variação.
4. `cd frontend && npm run build`.
5. `cd backend && ./mvnw clean verify` — ciclo completo com Spotless, ArchUnit e Testcontainers; não substituir por `mvn test`.
6. `git diff --check`.

O negativo obrigatório do teste da Administração continua sendo: atendente não renderiza placeholder e é redirecionado. Não aceite somente o caminho positivo de gestor/admin.

## Definição de pronto

- [ ] A branch e a base foram confirmadas; nenhuma outra branch foi alterada.
- [ ] `page.test.tsx` não contém `any` explícito nem `eslint-disable` para esconder erro.
- [ ] `npm run lint` termina sem erros.
- [ ] Typecheck, suíte frontend e build passam.
- [ ] `git diff --check` passa.
- [ ] `./mvnw clean verify` termina com sucesso, incluindo os 365 testes esperados ou a contagem real explicada.
- [ ] As afirmações funcionais da E67b foram reconferidas no código, não inferidas do relatório.
- [ ] A limitação da proteção client-side de `/administracao` permanece explícita.
- [ ] Nenhum prompt histórico, script utilitário ou alteração de outra etapa foi removido sem autorização.
- [ ] Nenhum commit ou push foi feito sem autorização explícita.
- [ ] CI remoto só é declarado verde com o número da run; sem push, declarar “não verificado”.

## No relatório

1. Branch `hotfix`, HEAD, estado do worktree, arquivos alterados e confirmação de que não houve push.
2. Resultado e evidência de cada item da definição de pronto.
3. Decisões tomadas sozinho, especialmente a tipagem dos mocks.
4. Divergência entre o relatório original e a árvore: lint falhando e prompts/scripts incluídos em `56ede13`.
5. Warnings preexistentes e qualquer novo defeito encontrado.
6. O que permaneceu fora, incluindo a autorização backend da Administração.
7. SHA final e confirmação de que nenhuma alteração foi transportada para `fixtwo` ou `main`.
8. Variáveis novas no Dokploy — expectativa: nenhuma.

---

## Fora desta etapa

- Não reescrever `56ede13`.
- Não fazer cherry-pick ou merge entre `hotfix`, `main` e `fixtwo` sem autorização.
- Não implementar CRUD, endpoints ou autorização backend da Administração.
- Não transformar `novo` em cálculo temporal ou criar backend editorial.
- Não corrigir os três warnings preexistentes de lint nesta etapa.
