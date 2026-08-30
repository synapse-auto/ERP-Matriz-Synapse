# Prompt E71 — Feedbacks dos usuários e painel de Administração

> Leia `AGENTS.md`, `docs/13-estado-do-projeto.md` e `docs/prompts/COMO-ESCREVER-PROMPTS.md` antes de alterar qualquer arquivo. Entrega em 25/08.
>
> Esta etapa deve ser executada exclusivamente na branch `fixtwo`, no worktree dedicado de `C:\Users\marcondes\Desktop\projeto_matriz-fixtwo`. Antes de começar, confirme branch, HEAD, `git status` e `git diff`. Não faça merge, rebase, cherry-pick ou cópia de arquivos da `main`/`hotfix` por conta própria. Não transporte a E67/E67b nesta etapa.
>
> Commite por bloco na `fixtwo`, mas não faça `git push` sem autorização explícita do Marcondes. Ao encerrar, rode `npm run lint`, `npm run typecheck`, `npm test -- --run` e `npm run build`. Como esta etapa atravessa backend, banco e contrato HTTP, rode também `cd backend && ./mvnw clean verify` com Java 21 e Testcontainers. Sem push, escreva `CI não verificado`; só chame CI de verde com o número da run.

---

## Contexto confirmado no repositório

O ponto de partida esperado é a E68 na `fixtwo`:

```text
HEAD esperado: e9cddf6 — feat: menu global e paridade do chat interno
Base pública: 43bf65e
Branch de trabalho: fixtwo
```

Se a branch não for `fixtwo`, se o HEAD tiver código de outra etapa ou se houver alterações locais que você não consiga distinguir das suas, pare e reporte antes de alterar qualquer arquivo.

Na `fixtwo`, a sidebar ainda possui apenas estes itens de gestão:

```tsx
// frontend/src/components/shell/sidebar.tsx
const ITENS_GESTAO: ItemDeMenu[] = [
  { chave: "equipe", rota: "/equipe", icone: Users },
  { chave: "campanhas", rota: "/campanhas", icone: Megaphone, flag: "campanhas" },
  { chave: "automacao", rota: "/automacao", icone: Bot },
  { chave: "horarios", rota: "/horarios", icone: CalendarClock, flag: "horarios" },
  { chave: "relatorios", rota: "/relatorios", icone: BarChart3, flag: "relatorios" },
];
```

Não existe item, rota ou componente de Administração. A rota curinga `frontend/src/app/(shell)/[...slug]/page.tsx` leva áreas não construídas para `frontend/src/components/shell/placeholder.tsx`; não use esse placeholder para fingir que a Administração funciona.

Os contratos reais de equipe existentes são:

```ts
// frontend/src/lib/equipe/api.ts
export const listarEquipe = () => apiFetch<UsuarioEquipe[]>("/api/v1/usuarios");
export const obterMeuUsuario = () => apiFetch<MeuUsuario>("/api/v1/me");
export function criarUsuario(...) { ... }
export function editarUsuario(...) { ... }
export const desativarUsuario = (...) => ...;
```

`UsuarioEquipe` tem nome, e-mail, papel, presença, ativo, cargo e foto, mas não tem último acesso. Não invente horários para imitar o protótipo.

Não há ocorrência de `feedback`, `feedback_usuario`, rota `/feedbacks`, caso de uso ou migration correspondente na `fixtwo`. A E65 só tratou aviso/sidebar/programadas; a E66 tratou nova conversa; a E67 tratou ficha do lead, ícones e um placeholder de Administração; a E68 tratou finalização e paridade do chat interno. Nenhuma dessas etapas implementou envio ou recebimento de feedback.

O HTML de referência é `C:\Users\marcondes\Downloads\CRM_EstruturalVidros_App (1).html`. O componente `Admin` contém:

- cabeçalho `Administração`, selo `ACESSO RESTRITO` e indicador de saúde;
- navegação interna `Visão geral`, `Acessos` e `Feedbacks`;
- tabela de acessos;
- lista de feedbacks com filtros `Todos`, `Sugestões` e `Erros`;
- as áreas `Novidades & Em Breve` e `Tutoriais & Documentação`, explicitamente fora desta etapa.

As imagens anexadas são referência de layout, não fonte de dados. Os arrays de demonstração do HTML (`ACESSOS`, `FEEDBACKS`, `NOVIDADES`, `INTEG`) não podem ser copiados para React, seed de produção ou `textos.json`.

> **Integração com outra branch.** Se, quando esta etapa for executada, outra branch já tiver criado o contrato de envio/persistência de Feedbacks, não duplique domínio, migration ou endpoint. Confira o código e consuma/reutilize o contrato existente. Se o contrato for incompatível com os requisitos abaixo, pare e reporte a divergência.

---

## Escopo de produto

### Incluído

1. Página para qualquer usuário autenticado enviar um feedback.
2. Persistência segura do feedback, com autoria derivada da sessão.
3. Área `Feedbacks` dentro da Administração para o administrador consultar o que foi enviado.
4. Shell administrativo fiel ao modelo, com Visão geral, Acessos e Feedbacks.
5. Acessos baseado nos dados reais da equipe, sem `Entrar como`.
6. Filtros de feedback por `Todos`, `Sugestões` e `Erros`.
7. Estados reais de carregamento, erro, lista vazia e paginação.

### Explicitamente fora desta etapa

- `Tutoriais & Documentação`, imagens-tutorial, manual PDF e changelog manual.
- `Novidades & Em Breve`: não criar menu, rota, schema, endpoint, CRUD, preview, placeholder ou conteúdo nesta etapa. Será uma etapa futura após definição das regras de publicação.
- `Entrar como`, impersonação, troca de sessão ou acesso como outro usuário.
- Feedback de cliente externo; o autor é um usuário interno autenticado do CRM.
- Workflow de triagem, resposta administrativa, SLA ou status de resolução, salvo se já existir contrato real em outra branch.
- Dados mockados, arrays fixos e contagens copiadas do HTML.
- Alterações em `main` ou `hotfix`, e transporte automático de commits dessas branches.

---

## Bloco 1 — shell e proteção da Administração

Crie rotas explícitas, sem usar a rota curinga:

```text
/feedbacks                         formulário de envio do usuário
/administracao                     visão geral
/administracao/acessos             acessos reais
/administracao/feedbacks           feedbacks recebidos
```

Na sidebar principal:

- `Feedbacks` deve ser encontrável por todos os papéis autenticados;
- `Administração` pertence ao grupo de gestão, usa ícone de escudo e selo visual restrito;
- somente `ADMINISTRADOR` vê e acessa Administração;
- a rota deve rejeitar o acesso indevido mesmo que alguém digite a URL diretamente;
- o frontend pode esconder a entrada por UX, mas não substitui `@PreAuthorize` no backend.

O shell administrativo deve seguir o HTML:

- sidebar principal do CRM à esquerda;
- cabeçalho claro com título, subtítulo, selo restrito e estado do sistema;
- navegação interna branca com item ativo em roxo;
- canvas claro com cartões brancos, bordas discretas, tipografia e espaçamento equivalentes às referências;
- layout responsivo sem sobrepor sidebar, subnavegação ou conteúdo.

O indicador `Sistema operacional` somente pode ser mostrado se houver health check real e apropriado. Não confunda “o componente carregou” com saúde da aplicação. Se a fonte não existir, omita-o ou exiba estado neutro do catálogo.

> **Não faça o caminho curto errado.** Não implemente Administração como um componente monolítico condicionado por `pathname`, não esconda autorização com CSS e não crie uma segunda sidebar independente do shell.

> **Ponto de parada.** Se a regra de autorização existente indicar que `GESTOR` ou `SUBGESTOR` também devem administrar esta área, pare antes de ampliar o acesso. A referência marca Administração como restrita; essa decisão não deve ser inferida somente pelo protótipo.

---

## Bloco 2 — contrato e persistência de Feedbacks

Como a etapa inclui envio e recebimento, implemente o contrato completo na fronteira de módulo correta. Avalie primeiro `crm-equipe`, pois o autor é um usuário e a Administração é uma capacidade de gestão. Se a análise arquitetural apontar outra fronteira, documente o motivo; não crie dependência invertida.

Modelo mínimo:

```text
id              UUID
autor_id        UUID NOT NULL REFERENCES usuario(id)
tipo            SUGESTAO | ERRO
area_chave      VARCHAR(...) NOT NULL
descricao       TEXT NOT NULL
criado_em       TIMESTAMPTZ NOT NULL DEFAULT now()
```

Regras:

- `autor_id` vem de `UsuarioContext`; nunca aceite autor no JSON como fonte de verdade;
- `tipo` aceita somente `SUGESTAO` ou `ERRO`;
- descrição obrigatória, trim, limite validado no backend e no frontend;
- `area_chave` guarda chave estável, não texto de UI; inclua opção equivalente a “Nenhuma aba específica”;
- ordenação `criado_em DESC` e desempate `id DESC`;
- índice compatível com data e filtro por tipo;
- não adicionar `tenant_id`, pois o isolamento é Silo;
- criar migration nova e nunca editar migration aplicada;
- não criar status de resolução, comentário administrativo ou SLA sem requisito real.

Contrato mínimo:

```text
POST /api/v1/feedbacks       ATENDENTE, SUBGESTOR, GESTOR e ADMINISTRADOR
GET  /api/v1/feedbacks       somente ADMINISTRADOR
```

O `GET` deve aceitar filtro de tipo e paginação seguindo o padrão real do projeto. Use DTOs estreitos; a leitura administrativa não pode devolver senha, hash, token, dados customizados ou dados pessoais desnecessários.

Documente o controller com OpenAPI, use RFC 7807/`ProblemDetail` e cubra o contrato HTTP.

### Anexo opcional

O modelo mostra um card com imagem anexada, mas o texto é obrigatório e o anexo é opcional.

- procure uma porta genérica de storage antes de criar uma nova;
- o browser nunca fala diretamente com MinIO/S3;
- não acople `crm-equipe` ao fluxo de mídia de `crm-atendimento` somente para imitar a imagem;
- se o anexo exigir novo bucket, variável do Dokploy, retenção ou política de segurança, pare e reporte antes de escolher;
- sem storage aprovado, não exiba botão de anexo fantasma; entregue fluxo textual e registre a mídia como fora.

> **Não publique feedback em fila.** A gravação local não precisa de outbox. Se for encontrada uma reação assíncrona real, use Transactional Outbox e não publique diretamente no broker.

---

## Bloco 3 — página de envio para usuários

Crie `frontend/src/components/feedbacks/pagina-feedbacks.tsx` e os hooks/API necessários.

Composição:

- título e subtítulo catalogados;
- cartão com seletor `Erro`/`Sugestão`;
- seletor de área do sistema;
- textarea de descrição;
- anexo opcional somente se aprovado no Bloco 2;
- botão com loading, sucesso e erro;
- limpar o formulário somente depois de sucesso;
- em falha, preservar valores e eventual arquivo;
- não exibir feedbacks de terceiros;
- não permitir escolher ou alterar o autor;
- acessibilidade com labels, foco, teclado e mensagens associadas aos campos.

Todos os textos, placeholders, áreas, erros e sucessos entram em `backend/crm-app/src/main/resources/textos.json` e `frontend/src/lib/config/schema.ts`. Não adicione string literal de UI no JSX.

---

## Bloco 4 — lista administrativa de Feedbacks

Em `/administracao/feedbacks`, reproduza a referência com dados reais:

- cabeçalho `Feedbacks` e subtítulo;
- filtro segmentado `Todos`, `Sugestões`, `Erros`;
- card com avatar/foto real, nome, papel, tipo, área, data/hora e descrição;
- diferenciação semântica entre sugestão e erro;
- anexo e nome somente quando houver anexo real autorizado;
- loading, erro com retry, lista vazia e paginação;
- contagem de menu somente se vier do backend;
- alteração de filtro refletida na consulta;
- atualização após novo envio sem duplicação.

O endpoint deve estar protegido no backend. Um usuário comum não pode consultar a lista global nem inferir dados de outros autores por uma resposta de erro.

> **Teste o negativo.** `ATENDENTE`, `SUBGESTOR` e `GESTOR` tentando `GET /api/v1/feedbacks` recebem 403 e nenhum item. Um usuário não pode forjar `autor_id` de outro usuário.

---

## Bloco 5 — Visão geral e Acessos

### Visão geral

Reproduza a composição visual do modelo sem repetir números fictícios:

- use `/api/v1/usuarios`, `/api/v1/config/features`, health check e telemetria somente se cada endpoint realmente representar o dado;
- não chame feature flag de integração ativa;
- não exiba `128 acessos`, `99,9%`, `3 feedbacks` ou `4/6 ativas` sem fonte real;
- remova cards sem fonte ou use estado neutro catalogado;
- dados do cliente, versão, commit e provedor só podem vir de configuração/endpoint real.

### Acessos

Reutilize os contratos e hooks reais de equipe:

- tabela com avatar, nome, e-mail, papel, presença/ativo e ações existentes;
- manter criar, editar, gerar senha provisória e desativar somente onde o contrato atual permitir;
- **não renderizar `Entrar como` em nenhum estado, teste ou texto**;
- não mostrar “último acesso” se o DTO não o fornecer;
- não duplicar CRUD de usuário se `PaginaEquipe` puder ser extraída/reutilizada sem mudar regras;
- ações destrutivas com confirmação e variante destrutiva.

---

## Fronteira explícita — Novidades & Em Breve

Apesar de aparecer no HTML de referência, `Novidades & Em Breve` está fora da E71. Não transportar a implementação estática da E67, não adicionar item de menu, rota, placeholder, schema, migration, endpoint, CRUD, prévia ou conteúdo neutro para essa área. A capacidade será especificada em etapa própria, quando houver decisão sobre publicação, edição, exclusão, auditoria e permanência.

Se outra branch já tiver uma implementação real, apenas registre a dependência no relatório para posterior integração na branch de homologação. Não faça cherry-pick, merge, cópia manual ou alteração de `main`/`hotfix` por conta própria.

---

## Segurança e arquitetura

- frontend protege a UX; casos de uso protegem o dado com `@PreAuthorize`;
- autor sempre vem de `UsuarioContext`;
- aplicar RLS conforme as convenções existentes quando a tabela nova contiver dados sensíveis;
- teste negativo com papel de banco real se houver política RLS;
- `JpaRepository` somente em `infrastructure.persistencia`;
- domínio sem Spring/JPA;
- nenhuma chamada externa síncrona para montar Administração;
- nenhum novo parâmetro de configuração sem necessidade;
- se surgir variável, default seguro, `.env.example`, `README.md` e ação necessária no Dokploy;
- Java 21 fixo;
- nenhum texto/cor/número de demonstração hardcoded no frontend.

---

## Testes — a proteção nasce com um teste que a viola

### Backend

- cria sugestão e erro com autor do contexto;
- rejeita tipo desconhecido;
- rejeita descrição vazia, só espaços e acima do limite;
- rejeita área inválida/ausente conforme regra definida;
- ignora ou rejeita autor enviado no request;
- administrador lista todos;
- filtros e paginação sem duplicação;
- não administrador recebe 403 no GET;
- não autenticado recebe 401;
- RLS: usuário A não lê feedback de B, se a política for criada;
- controller HTTP real com Testcontainers e Problem Details;
- OpenAPI contém operação, segurança, respostas e DTOs corretos.

### Frontend

- formulário envia e limpa somente após sucesso;
- falha preserva dados;
- todos os papéis autenticados veem a entrada de envio;
- não administrador não vê/acessa Administração;
- administrador vê shell e navegação interna;
- lista filtra, pagina e mostra vazio real;
- retry funciona;
- autor real aparece;
- ausência de anexo não cria caixa falsa;
- `Entrar como` e `Tutoriais & Documentação` não aparecem em DOM, catálogo ou rotas;
- foco, teclado, `aria-current`, labels e diálogos;
- alternar páginas não cria chamadas infinitas ou loops de renderização.

### Validação visual

Faça capturas usando backend real ou fixture de API composta somente por dados criados pelo teste:

1. sidebar com Administração ativa;
2. Visão geral;
3. Acessos sem `Entrar como`;
4. formulário de envio vazio, preenchido, sucesso e erro;
5. lista administrativa com sugestão e erro reais;
6. lista vazia;
7. usuário não administrador bloqueado.

Compare hierarquia, largura, espaçamento, cartões, bordas, tipografia, ícones, estados e responsividade com as imagens anexadas. Renderizar não é prova de fidelidade.

---

## Definição de pronto

- [ ] Branch/HEAD confirmados e nenhuma alteração de `main`/`hotfix` transportada.
- [ ] Usuário autenticado consegue enviar Feedbacks.
- [ ] Feedback persiste com autor da sessão, tipo, área, descrição e timestamp.
- [ ] `GET /api/v1/feedbacks` é administrativo, paginado, filtrável e protegido.
- [ ] Usuário comum não consulta feedbacks de terceiros.
- [ ] Administração tem rota real e autorização backend.
- [ ] Shell reproduz o modelo sem Tutoriais/Documentação.
- [ ] Acessos usa dados reais e não contém `Entrar como`.
- [ ] Nenhum dado fictício foi adicionado.
- [ ] Novidades & Em Breve não foi incluída nesta etapa: sem menu, rota, schema, endpoint, CRUD, preview ou conteúdo.
- [ ] Sem strings/cor literal nova no frontend.
- [ ] Testes negativos de autenticação, autorização, autoria, validação, vazio e RLS cobertos.
- [ ] lint sem erros, typecheck, testes frontend e build aprovados.
- [ ] `cd backend && ./mvnw clean verify` aprovado com Java 21/Testcontainers.
- [ ] `git diff --check` aprovado.
- [ ] Capturas visuais anexadas com viewport, rota e origem dos dados.
- [ ] CI informado com número da run; sem push, `CI não verificado`.

---

## No relatório

Siga os sete itens do `AGENTS.md` e acrescente:

1. SHA, branch, worktree e confirmação de push;
2. quantidade de arquivos, migrations e contratos criados;
3. evidência dos testes negativos de autorização, autoria e RLS;
4. endpoint e formato do contrato usado pelo frontend;
5. se anexo foi implementado, porta/bucket utilizado; se não, declarar fora;
6. variáveis novas no Dokploy — expectativa: nenhuma; se houver, nome, exemplo, arquivos atualizados e ação necessária;
7. screenshots, viewport, rotas e origem dos dados;
8. números exatos de testes, lint/typecheck/build, backend e CI;
9. divergência encontrada caso outra branch já tenha implementado parte de Feedbacks/Administração.

---

## Fora desta etapa

- Tutoriais & Documentação.
- Entrar como/impersonação.
- Resposta administrativa, workflow de resolução e SLA.
- Integrações ou métricas sem fonte real.
- Novidades & Em Breve; será tratada em etapa futura com contrato e regras de publicação definidos.
- Deploy, alteração de `main`/`hotfix` e push sem autorização explícita.
