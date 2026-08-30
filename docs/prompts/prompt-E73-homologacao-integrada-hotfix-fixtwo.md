# Prompt E73 — homologação integrada de `hotfix` + `fixtwo`

## Papel

Você é o agente responsável pela homologação técnica e visual. Não faça push,
deploy ou alteração irreversível sem autorização explícita do Marcondes.
Relatórios anteriores são hipóteses: confira tudo na árvore, no histórico, nos
testes e no navegador.

Responda em português e produza o relatório final no formato obrigatório de
`AGENTS.md`, com evidência concreta para cada item.

## Objetivo

Montar uma branch temporária de homologação contendo, de forma rastreável, o
estado publicado em `main` mais as entregas das branches `hotfix` e `fixtwo`.
Validar conflitos, regressões, segurança, contrato, testes e aparência antes
de qualquer decisão de promoção para `main`.

Esta etapa é de integração e validação. Não transforme gaps identificados em
implementação improvisada. Se algo exigir decisão de produto, pare e registre
o ponto de parada.

## Preparação obrigatória

1. Leia integralmente, nesta ordem:
   - `AGENTS.md`;
   - `docs/13-estado-do-projeto.md`;
   - `docs/prompts/COMO-ESCREVER-PROMPTS.md`.
2. Confirme, antes de qualquer merge:
   - branch e worktree atuais;
   - `git status --short`;
   - `git log --oneline --decorate -12`;
   - `git branch -a --contains` dos commits envolvidos;
   - existência e ponta de `main`, `hotfix`, `fixtwo`, `origin/main` e
     `origin/fixtwo`.
3. Confirme os pontos de partida esperados, sem assumir que continuam iguais:
   - `main`: `43bf65e`;
   - `hotfix`: `74e528d`;
   - `fixtwo`: `79b7b71`;
   - `origin/fixtwo`: `e9cddf6`;
   - não presuma que exista `origin/hotfix`.
4. Se algum SHA divergir, registre a divergência e use os refs reais somente
   depois de confirmar que correspondem às entregas. Não force reset para
   fabricar o estado esperado.

## Branch e worktree de homologação

Use exatamente:

- branch temporária: `hmlgc`;
- worktree: `C:\Users\marcondes\Desktop\projeto_matriz-hmlgc`.

Crie `hmlgc` a partir da `main` confirmada. O worktree deve ser separado do
worktree de `hotfix` e do worktree de `fixtwo`. Não reutilize um worktree
existente sem conferir seu estado; não apague, resete ou sobrescreva trabalho
não relacionado. Se `hmlgc` já existir, pare antes de alterar e informe seu
HEAD, worktree associado e status.

As branches de origem são somente leitura nesta etapa. Não faça commit nelas,
não reescreva histórico e não transporte arquivos manualmente para simular
merge.

## Integração rastreável

Na `hmlgc`, integre usando merges explícitos e não fast-forward, preservando o
histórico:

```text
git merge --no-ff <ref-da-hotfix>
git merge --no-ff <ref-da-fixtwo>
```

Escolha a ordem que resulte em um histórico compreensível, documente-a e não
use squash. Se houver conflito, não aceite automaticamente a versão de um
lado. Para cada conflito:

- leia o histórico e o diff de ambas as branches;
- identifique qual requisito de cada etapa está sendo preservado;
- resolva mantendo o comportamento efetivo mais completo, sem reintroduzir
  placeholder onde já existe contrato real;
- execute os testes do caminho afetado;
- registre arquivos, decisão e risco no relatório.

Áreas que exigem revisão explícita:

- `sidebar` e testes: manter Novidades global, Administração restrita e
  Feedbacks, sem duplicar menus nem quebrar feature flags;
- diálogo global de Novidades: preservar o catálogo/visualização global, sem
  inventar editor administrativo de Novidades nesta integração;
- Administração: preferir a página administrativa real da `fixtwo` ao
  placeholder antigo da `hotfix`, preservando a proteção e os testes mais
  fortes de acesso;
- schema/textos: fazer a união sem strings de UI fora do catálogo e sem
  valores duplicados ou incompatíveis;
- ficha do lead e botões destructive: preservar E67b quando não houver
  conflito funcional;
- E65/E70: manter aviso dispensável, retração das barras, destaque de
  mensagens programadas e correção dos testes do agendador;
- E68: manter menu global de finalização e paridade do chat interno;
- E71: manter migration/contrato de Feedbacks, autenticação, RLS,
  autorização por papel e estados vazios reais.

Não marque a integração como concluída se um conflito for resolvido apagando
funcionalidade, relaxando autorização, inserindo dado mockado ou silenciando
um teste.

## Invariantes funcionais

Confirme no código e nos testes, não apenas na tela:

1. Atendimentos continua disponível e o caminho de envio/recebimento não ganha
   chamada síncrona externa bloqueante.
2. `RN-CRM-01` e `RN-CRM-06` continuam respeitadas; não há vazamento de lead
   entre atendentes.
3. Chat interno e atendimento de cliente continuam diferenciados por tipo,
   com seleção, ordenação, autoria e composer corretos.
4. Administração e Feedbacks não exibem dados mockados. Onde o endpoint não
   existe, o estado deve ser vazio ou a opção deve permanecer fora.
5. `POST /api/v1/feedbacks` continua autenticado para os papéis permitidos,
   com autoria no usuário da sessão; listagem continua restrita ao
   administrador no backend, com teste negativo de 403.
6. RLS, migrations, Problem Details, OpenAPI e contratos existentes não são
   enfraquecidos.
7. Novidades global permanece visualizável sem criar editor/CRUD administrativo
   não solicitado.
8. Java permanece em release 21; não aceite atualização automática para Java
   25.

## Validação backend

Execute o ciclo completo a partir de `backend`, com Docker/Testcontainers
disponível:

```text
./mvnw clean verify
```

Não substitua por `mvn test`, `test-compile` ou um módulo isolado. Registre:

- versão do Java e confirmação de release 21;
- execução de Spotless, ArchUnit e Testcontainers;
- quantidade exata de testes, falhas, erros e skips por módulo relevante;
- resultado final `BUILD SUCCESS` ou falha com nome do teste e causa;
- testes específicos de Feedbacks, Administração, autorização/RLS, chat
  interno, E65/E70/E68 e scheduler quando existirem.

Se o build falhar por um conflito da integração, corrija somente se a causa
for objetivamente desta etapa e deixe a correção testada. Se for uma falha
pré-existente ou exigir decisão de contrato, pare e informe.

## Validação frontend

Em `frontend`, execute e registre separadamente:

```text
npm run lint
npm run typecheck
npm test -- --run
npm run build
```

O lint deve ter zero erros. Diferencie warnings preexistentes de regressões e
não use `eslint-disable`, `any` indiscriminado ou alteração de configuração
para esconder erro. Confira que o build inclui as rotas reais esperadas e que
os testes cobrem estados de carregamento, vazio, erro, permissão e sucesso.

## Validação visual no navegador

Faça validação com backend acessível e dados reais de teste, sem inventar
conteúdo no frontend. Registre screenshots e console para desktop em
`1440x1000` e mobile em `390x844`, cobrindo:

- `/atendimentos`: lista, chat de cliente, chat interno, menu global e barras
  retraídas/reabertas;
- `/administracao`: visão geral e proteção para papel não autorizado;
- `/administracao/acessos`: tabela, ações e estado sem permissão;
- `/administracao/feedbacks`: filtros Todos/Sugestões/Erros, cards, autor,
  área, data, anexo quando houver e estado vazio;
- `/feedbacks`: envio de feedback por usuário autorizado, validação, sucesso,
  erro e limpeza do formulário;
- diálogo global `Novidades & Em Breve`, se a rota/menu estiver habilitada.

Verifique especificamente fidelidade ao modelo, sem overflow horizontal,
contraste, foco/teclado, fechamento por botão e `Escape`, responsividade,
ícones destructive em vermelho via token, sidebar principal e painel do lead.
Não declare a validação visual concluída com base somente em Vitest.

## Auditoria final da integração

Antes do relatório, execute:

```text
git diff --check main...HEAD
git diff --stat main...HEAD
git diff --name-status main...HEAD
git status --short
```

Revise se não entraram:

- segredos, credenciais, dumps, outputs de Playwright ou artefatos temporários;
- prompts históricos ou scripts não relacionados sem justificativa;
- migrations duplicadas ou alteração de migration já aplicada;
- strings/cor literal de UI fora dos catálogos/tokens;
- endpoints sem autorização, teste de contrato ou Problem Details;
- dados mockados apresentados como se fossem reais.

Confira também o OpenAPI gerado/servido e os endpoints de Feedbacks e
Administração. A existência de uma rota frontend não prova que o backend está
protegido.

## Pontos de parada obrigatórios

Pare e peça decisão, sem commit ou push, se ocorrer qualquer um destes casos:

- `hmlgc` já existir com estado não limpo;
- `main`, `hotfix` ou `fixtwo` não corresponderem às entregas sem explicação;
- conflito exigir escolher entre duas regras de negócio não documentadas;
- Administração ficar protegida somente no cliente quando houver dados
  sensíveis ou mutações;
- Feedbacks perder autoria, RLS, 401/403 ou isolamento por papel;
- um teste falhar por comportamento ambíguo e a correção exigir novo contrato;
- build completo não puder ser executado por Docker, Java ou outro bloqueio;
- for necessário alterar produção, Dokploy, imagem ou variável de ambiente.

## Definição de pronto

- [ ] `hmlgc` criada em worktree próprio a partir da `main` confirmada.
- [ ] `hotfix` e `fixtwo` integradas com `merge --no-ff`, sem alteração nas
      branches de origem.
- [ ] Todo conflito documentado e validado; nenhum comportamento foi apagado
      silenciosamente.
- [ ] Backend `./mvnw clean verify` completo com resultado e contagens exatas.
- [ ] Frontend lint, typecheck, suíte completa e build executados.
- [ ] OpenAPI, autenticação, autorização, RLS e contratos conferidos.
- [ ] Validação visual desktop/mobile e console registradas para as rotas
      previstas.
- [ ] `git diff --check` limpo e artefatos temporários fora do commit.
- [ ] Nenhum commit, push ou deploy sem autorização explícita do Marcondes.

## Relatório final obrigatório

Entregue nesta ordem:

1. Commit e estado: branch, worktree, HEAD, origem, merges, status, quantidade
   de arquivos e confirmação explícita de que não houve commit/push/deploy.
2. Definição de pronto: cada checkbox acima com ✅/⚠️/❌ e evidência concreta.
3. Decisões tomadas na resolução de conflitos, com justificativa.
4. Divergências entre documentação e repositório.
5. Bugs pré-existentes ou encontrados, inclusive os que testes não capturam.
6. O que ficou de fora e por quê.
7. Decisões necessárias para promoção da `hmlgc` a `main`.

Não escreva “CI verde” sem número de run. Como esta etapa não autoriza push,
registre CI remoto como “não verificado” até que o proprietário autorize a
publicação.
