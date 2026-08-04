# Prompt E02 — Auth, RBAC e Specification de visibilidade

> Pré-requisito: E01 + E01b concluídas e commitadas.
> **Esta é a etapa mais sensível do projeto. Não tenha pressa aqui.**

---

**Etapa E02 — Autenticação, RBAC e visibilidade de lead.**

Leia `CLAUDE.md` (seção "Regras de negócio sensíveis") e `docs/01-arquitetura-geral.md` §7.

## Por que esta etapa é crítica

Os atendentes da Estrutural Vidros trabalham por comissão e disputam leads entre si. Um atendente conseguir ver ou tocar o lead de outro **não é bug técnico, é incidente comercial na casa do cliente**. Trate o isolamento como requisito de segurança, não como filtro de conveniência.

## Contexto herdado

- Esta é a primeira etapa que cria **classes de domínio**. Volte `archunit.properties` para `failOnEmptyShould=true` — as regras do `ArquiteturaTest` passam a ter o que reprovar, e é agora que elas ganham valor.
- O `usuario` já existe no schema com `papel_usuario` e senhas BCrypt no seed da E01.
- Módulo: `crm-equipe`. Pacote de controllers é `interfaces` (plural).

## O que construir

### 1. Autenticação (`crm-equipe`)

- Login com e-mail e senha, BCrypt
- JWT: access token curto (15 min) + refresh token (7 dias), **refresh rotativo**
- `POST /api/v1/auth/login`, `/refresh`, `/logout`
- Stateless — nada em memória de instância; precisamos escalar horizontalmente
- Usuário inativo não autentica
- Segredo do JWT vem de variável de ambiente, com o bloco `synapse:` da E00

### 2. RBAC

Papéis: `ATENDENTE`, `SUBGESTOR`, `GESTOR`, `ADMINISTRADOR`.

Autorização declarada **por caso de uso**, não por padrão de rota. Um caso de uso sabe quem pode executá-lo; uma configuração central de rotas não sabe, e sempre fica desatualizada.

### 3. `UsuarioContext`

Expõe de forma tipada o usuário autenticado da requisição (id, papel) para a camada de aplicação, sem que os casos de uso conheçam `SecurityContextHolder`. O domínio não deve saber que Spring Security existe — e o `ArquiteturaTest` vai cobrar isso.

### 4. `VisibilidadeLeadSpecification` — o núcleo desta etapa

Traduz `RN-CRM-01`:

- `ATENDENTE` → leads onde `atendente_responsavel_id = usuarioAtual`, **mais** os leads em `status_basico = 'IA'` (grupo "Potenciais", visível a todos)
- `SUBGESTOR`, `GESTOR`, `ADMINISTRADOR` → todos os leads

Requisitos de design:

- **Componível** com outras Specifications — o filtro modular da E03 vai compor por cima
- Aplicada em **toda** consulta de lead, atendimento, lembrete e mensagem programada; não só nas listagens
- **Torne o erro impossível, não improvável.** Projete o repositório de forma que esquecer a Specification não compile ou não passe no teste de arquitetura. Ex.: o repositório não expõe `findAll()`/`findById()` cru; só métodos que exigem a Specification como parâmetro obrigatório. Se a única proteção for "lembrar de aplicar", com 60+ casos de uso e prazo curto alguém vai esquecer.

Considere adicionar uma regra ao `ArquiteturaTest` que reprove acesso direto ao repositório JPA de `lead` fora do adaptador que aplica a Specification. Isso transforma a disciplina em falha de build.

### 5. Testes — obrigatórios

Testes que **provem**, não apenas exercitem:

- Atendente A não acessa lead do atendente B — por listagem, por busca, por filtro e **por id direto** (esse é o vetor que mais escapa em revisão)
- Atendente vê leads em status `IA`
- Gestor e subgestor veem todos
- Usuário inativo não autentica
- Token expirado é rejeitado
- Refresh rotativo invalida o token anterior (usar o antigo duas vezes falha)
- Caso de uso restrito a gestor rejeita atendente com 403 (não 404, não 500)

## Restrições

- Nenhuma filtragem de visibilidade no frontend. O servidor é a única autoridade.
- Não coloque a regra de propriedade em SpEL de `@PreAuthorize` consultando o banco — ela vai para o caso de uso, onde é testável.
- Nada de Spring ou JPA nos pacotes `domain`.

## Definição de pronto

- [ ] Login, refresh e logout funcionando
- [ ] Todos os testes de isolamento passando
- [ ] `failOnEmptyShould=true` restaurado e `ArquiteturaTest` verde
- [ ] Nenhuma consulta de lead no código sem a Specification aplicada
- [ ] CI verde

Commit: `feat: autenticação, RBAC e isolamento de agenda`.

Ao terminar, me explique **como** você garantiu que uma consulta futura de lead não possa esquecer a Specification. Se a resposta for "por convenção", quero repensar o design junto com você antes de seguir para a E03.
