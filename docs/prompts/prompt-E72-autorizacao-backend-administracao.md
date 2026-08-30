# Prompt E72 — fechar a autorização backend da Administração

> Leia por inteiro `AGENTS.md`, `docs/13-estado-do-projeto.md`, `docs/prompts/COMO-ESCREVER-PROMPTS.md` e este prompt antes de alterar qualquer arquivo.
>
> Esta etapa é uma correção de segurança derivada da auditoria da E67b/E70. Trabalhe somente na branch/worktree destinados à etapa atual; confirme branch, HEAD, `git status` e `git diff` antes de começar. Não faça cherry-pick, merge, reset, cópia manual de arquivos ou alteração de `main`/`hotfix` por conta própria. Não faça commit ou push sem autorização explícita do Marcondes, mesmo que o `AGENTS.md` contenha uma regra geral de publicação.
>
> O relatório anterior não é evidência. Toda afirmação abaixo deve ser conferida na árvore, nos casos de uso, nos controllers e em testes HTTP reais.

## Contexto confirmado

Na E67b/E70, a rota frontend `/administracao` continua sendo um `Placeholder` protegido apenas por `useEffect` no cliente. O próprio código declara que a proteção definitiva será backend quando surgirem endpoints reais.

Isso não deve ser “corrigido” criando um endpoint vazio apenas para responder se o usuário pode abrir uma tela sem dados. Uma rota de página Next.js não fica protegida por `@PreAuthorize`; a proteção efetiva precisa estar em cada caso de uso que lê ou altera dados administrativos. Quando a Administração passar a consumir dados reais, um usuário sem permissão deve receber `403` no endpoint, mesmo que descubra a URL ou chame a API sem usar o frontend.

O código atual deve ser confirmado, não presumido. No ponto de partida conhecido:

- `frontend/src/app/(shell)/administracao/page.tsx` permite `GESTOR` e `ADMINISTRADOR` apenas no cliente e renderiza `Placeholder`;
- `backend/crm-equipe/.../ListarUsuariosUseCase.java` já possui `@PreAuthorize("hasAnyRole('GESTOR', 'SUBGESTOR', 'ADMINISTRADOR')")`;
- `UsuarioController` expõe `GET /api/v1/usuarios` para a gestão da equipe;
- `UsuarioContext` e `UsuarioAutenticado` são as portas existentes para identidade, e casos de uso não devem acessar `SecurityContextHolder` diretamente;
- a cadeia Spring Security usa `@EnableMethodSecurity` e converte o claim `papel` em `ROLE_<PAPEL>`;
- não existe autorização backend específica de uma tela chamada Administração, e a Administração do E67b ainda não possui CRUD próprio.

Se a árvore tiver mudado, atualize este diagnóstico no relatório e siga o contrato real. Não transporte código da `hotfix`, de `main` ou de outra branch para “completar” a tarefa.

## Ponto de parada obrigatório — não inventar uma política de papel

Há uma divergência de produto que não pode ser resolvida silenciosamente:

- a E67b documenta Administração para `GESTOR` e `ADMINISTRADOR`;
- o Prompt E71, que prepara o módulo de Feedbacks/Administração, descreve Administração como exclusiva de `ADMINISTRADOR`;
- o endpoint existente de equipe aceita também `SUBGESTOR`, pois sua finalidade é gestão da equipe, não necessariamente o painel administrativo.

Antes de alterar uma permissão existente ou definir a permissão dos novos endpoints, confira se há decisão posterior explícita no repositório ou no handoff. Se não houver, **pare e informe a divergência**, sem escolher entre `GESTOR + ADMINISTRADOR` e `ADMINISTRADOR` sozinho.

Enquanto a decisão não existir:

- não estreite `GET /api/v1/usuarios` nem qualquer caso de uso de equipe já usado por outras telas;
- não amplie permissão para `SUBGESTOR` só porque ele aparece no grupo de gestão;
- não use o nome do usuário, e-mail ou estado da sidebar para autorizar;
- registre exatamente quais endpoints ficaram sem mudança e por quê.

Se houver decisão explícita, transcreva-a no relatório e aplique-a somente aos endpoints administrativos correspondentes.

## Objetivo

Eliminar a falsa sensação de segurança da Administração e garantir que todos os dados/operações administrativos reais tenham autorização no backend, no limite correto da camada de aplicação, sem criar endpoint de verificação sem efeito e sem transformar o frontend em autoridade.

## Bloco 1 — inventário antes da implementação

Faça uma busca reproduzível e registre os resultados:

1. liste todas as rotas sob `/api/v1/administracao`, `/api/v1/feedbacks`, `/api/v1/usuarios` e qualquer rota que o shell administrativo realmente consuma;
2. localize os casos de uso chamados por essas rotas;
3. localize cada `@PreAuthorize`, anotação de classe, configuração de rota e teste HTTP correspondente;
4. confira se a implementação está na branch atual ou somente em outra branch;
5. confirme se há dados sensíveis, mutações, upload de anexos ou apenas `Placeholder`.

O inventário deve separar:

- autorização de uma operação de negócio;
- visibilidade do item de menu;
- guarda client-side da página;
- autenticação da requisição;
- RLS, quando houver uma tabela nova com dados sensíveis.

Não aceite “a sidebar escondeu” como prova de autorização.

## Bloco 2 — autorização na camada correta

Para cada caso de uso administrativo real encontrado ou criado pela etapa de Feedbacks:

- declare a permissão no método do caso de uso com `@PreAuthorize`, conforme o papel decidido no ponto de parada;
- mantenha controllers finos, sem regra de papel duplicada;
- não coloque consulta ao banco em SpEL para decidir propriedade ou existência;
- use `UsuarioContext` para autoria e identidade quando a operação depender do usuário corrente;
- não aceite `autorId`, `usuarioId` ou papel do corpo da requisição como fonte de verdade;
- não consulte `SecurityContextHolder` no domínio ou na aplicação;
- preserve `@EnableMethodSecurity` e a conversão `ROLE_` existente;
- não crie um mapa central de rotas que duplique as permissões dos casos de uso;
- não altere a autorização de endpoints de equipe já existentes sem a decisão de negócio registrada.

Se o fluxo de Feedbacks for implementado junto da E71, a leitura administrativa deve ser protegida no caso de uso que lista os feedbacks. A criação feita por usuário autenticado deve ter sua própria regra: o usuário pode registrar o próprio feedback, mas não pode escolher o autor nem consultar feedbacks de terceiros, salvo a permissão administrativa definida.

Se ainda não houver endpoint/caso de uso administrativo real na branch, não crie `GET /api/v1/administracao/acesso`, `GET /api/v1/administracao/permissao` ou equivalente apenas para satisfazer esta etapa. Nesse cenário, faça a auditoria, adicione os testes que comprovem o comportamento atual do placeholder e pare com relatório de que a autorização de dados depende da implementação real da E71.

## Bloco 3 — contrato HTTP e erros

Para cada endpoint real protegido:

- mantenha REST em `/api/v1/...`;
- declare `@SecurityRequirement` e respostas de `401`/`403` na documentação OpenAPI quando o padrão do projeto exigir;
- confirme que requisição sem token resulta em `401`;
- confirme que token válido de papel não autorizado resulta em `403`;
- confirme que papel autorizado alcança somente os dados permitidos;
- preserve RFC 7807/Problem Details no erro, conforme o mecanismo já usado pelo projeto;
- não devolva dados do usuário não autorizado antes da decisão de autorização;
- não transforme `403` em `404` para esconder regra de papel sem requisito explícito.

Não altere CORS, JWT, refresh token ou filtro de senha provisória sem demonstrar que a correção exige isso. Não coloque credenciais, segredos ou token em código, teste versionado ou resposta administrativa.

## Bloco 4 — RLS e isolamento

O modelo de tenancy é Silo: não adicione `tenant_id` nem invente isolamento entre filhos.

Se esta etapa criar ou alterar tabela de Feedbacks:

- use migration Flyway nova, nunca edite migration aplicada;
- confirme se a política RLS é realmente necessária para o dado e se segue o padrão existente;
- teste com usuário de banco não privilegiado, não apenas com dono/superusuário;
- inclua teste negativo que prove que a política bloqueia leitura indevida, se RLS for criada;
- não declare RLS protetiva se o teste estiver rodando com proprietário que ignora a política;
- mantenha índices coerentes com os filtros reais, sem `SELECT *` em listagens.

Se não houver tabela, query ou dado administrativo novo nesta etapa, não crie migration ou RLS decorativa.

## Bloco 5 — frontend somente como consumidor

Não transforme esta correção backend em redesign da Administração.

No frontend:

- mantenha a ocultação do menu e o guard client-side como UX, não como segurança;
- trate `401` e `403` sem loop de redirecionamento nem chamadas infinitas;
- não mostre dados antes da resposta autorizada;
- preserve estado de carregamento, erro e vazio real;
- não adicione mock, array fixo, contagem inventada ou texto literal para simular autorização;
- se o backend responder `403`, mostre o estado catalogado compatível com o padrão atual ou mantenha a navegação bloqueada, sem mascarar o erro como sucesso;
- não crie controles administrativos que não tenham endpoint e autorização correspondentes.

Se a rota ainda renderizar apenas `Placeholder`, não invente consumo de uma API de autorização só para alterar o placeholder. Registre essa decisão.

## Testes obrigatórios

### Testes do caso de uso

Para cada caso de uso administrativo real:

- teste positivo com o papel autorizado;
- teste negativo com `ATENDENTE`;
- teste negativo com usuário autenticado de papel de gestão que não esteja autorizado, caso a política escolhida diferencie os papéis;
- teste sem contexto autenticado, quando o caso de uso puder ser chamado fora do controller;
- teste de autoria: o autor não pode ser substituído por valor recebido no request;
- teste de não vazamento: o caminho negado não chama o repositório nem retorna dados.

Use mocks de porta apenas no teste unitário do caso de uso. Não use `JwtAuthenticationToken` construído de forma que resulte em `authenticated=false`; o teste HTTP deve autenticar pelo mesmo caminho que a aplicação usa.

### Testes HTTP com Testcontainers

Atualize ou crie teste de integração no módulo correto, usando Postgres real e o helper de autenticação existente:

- sem token: `401`;
- token de `ATENDENTE`: `403`;
- cada papel autorizado pela decisão: `200`/status de sucesso;
- autor não autorizado não lê, altera ou exclui dado administrativo;
- token expirado ou inválido não é aceito;
- resposta negada segue Problem Details e não contém dados sensíveis;
- se houver RLS, o teste roda com papel de banco que realmente sofre a política;
- controller e OpenAPI ficam coerentes com o contrato real.

O teste precisa chamar o controller/HTTP como o runtime chama. Não basta instanciar o método interno ou testar somente a expressão SpEL.

### Testes de arquitetura

Confirme que:

- domínio não importa Spring, JPA ou SecurityContextHolder;
- casos de uso não dependem de `JpaRepository`;
- repositories JPA continuam confinados à infraestrutura;
- controllers não acumulam regra de autorização ou consulta direta;
- não foi criada dependência invertida entre módulos.

## Validação obrigatória

Execute e registre a saída real:

1. `cd frontend && npm run lint`;
2. `cd frontend && npm run typecheck`;
3. `cd frontend && npm test -- --run`;
4. `cd frontend && npm run build`;
5. `cd backend && ./mvnw clean verify` com Java 21 e Docker/Testcontainers;
6. `git diff --check`;
7. inspeção do diff para confirmar que nenhuma permissão de endpoint existente foi alterada silenciosamente.

Não declare CI verde sem número de run remoto. Se não houver push, escreva `CI não verificado`.

## Definição de pronto

- [ ] Branch, worktree, HEAD, `git status` e base foram confirmados.
- [ ] O inventário separou página, menu, autenticação, autorização e RLS.
- [ ] A divergência `GESTOR + ADMINISTRADOR` versus `ADMINISTRADOR` foi resolvida por decisão documentada ou acionou ponto de parada.
- [ ] Nenhum endpoint vazio foi criado para dar aparência de proteção à página.
- [ ] Todo endpoint administrativo real possui autorização no caso de uso.
- [ ] `401`, `403`, papel autorizado, papel não autorizado e token inválido estão cobertos por teste HTTP real.
- [ ] A autoria vem da sessão/contexto, nunca do corpo enviado pelo cliente.
- [ ] Não houve alteração silenciosa na permissão de `GET /api/v1/usuarios` ou de outros endpoints existentes.
- [ ] RLS foi criada somente se necessária e, se criada, teve teste negativo com papel de banco efetivo.
- [ ] Frontend não trata ocultação do menu como segurança e não exibe dados antes da autorização.
- [ ] Não há mock, segredo, dado fictício, `SELECT *` ou cor/texto de UI fora do padrão.
- [ ] lint, typecheck, testes frontend, build e `clean verify` foram executados com resultado real.
- [ ] `git diff --check` passou.
- [ ] CI foi informado com número da run ou declarado não verificado.
- [ ] Nenhum commit ou push foi feito sem autorização explícita.

## Fora de escopo

- Implementar CRUD completo de Administração sem contrato de produto.
- Implementar Novidades & Em Breve; está excluída da E71.
- Criar impersonação/“Entrar como”.
- Alterar JWT, refresh, CORS ou RLS sem necessidade comprovada.
- Criar endpoint de “pode acessar” sem proteger um dado/operação real.
- Transportar E67b/E70 da `hotfix` para `fixtwo` sem autorização.
- Commit, push, deploy ou alteração no Dokploy sem autorização explícita.

## No relatório final

Siga os sete itens do `AGENTS.md` e acrescente:

1. branch, worktree, SHA-base/final, estado do worktree e confirmação de push;
2. inventário das rotas administrativas reais e dos casos de uso protegidos;
3. política de papéis aplicada, decisão que a autorizou e divergências encontradas;
4. evidência exata dos testes `401`, `403`, positivo, autoria e RLS;
5. confirmação de que nenhum endpoint vazio foi criado para proteger o placeholder;
6. alterações de migration, RLS, OpenAPI e contratos, ou declaração explícita de que não houve;
7. números de lint, typecheck, testes frontend, build, `clean verify` e CI;
8. qualquer limitação restante da rota client-side `/administracao`;
9. variáveis novas no Dokploy — expectativa: nenhuma; se surgir alguma, atualizar `.env.example`, `README.md` e informar a ação operacional.
