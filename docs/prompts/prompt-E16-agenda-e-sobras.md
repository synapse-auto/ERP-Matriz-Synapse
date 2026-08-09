# Prompt E16 — Agenda sobre o filtro modular, e as sobras da E15b

> Leia `AGENTS.md`. Continuação da E15b.
> **Pré-requisito operacional:** os testes de integração precisam de Docker. Se `docker info` falhar, **pare e avise** — a E15b já entregou cinco ITs que nunca rodaram, e não vamos empilhar um sexto.
> Commite e faça push ao final de cada bloco.

---

## Bloco 1 — `/agenda` (o grande)

### O diagnóstico

A tela hoje é uma lista vertical reaproveitando `CartaoConversa`. O backend, por baixo, é muito maior do que ela usa:

- `POST /api/v1/leads/filtrar` — critérios compostos, construídos na E03b
- `POST /api/v1/leads/filtrar/contagem` — o total sem trazer as linhas
- `CampoFiltravel` com 15+ campos: `NOME`, `EMPRESA`, `EMAIL`, `TELEFONE`, `CPF`, `LOCALIZACAO`, `STATUS`, `ETAPA`, `CANAL_ORIGEM`, `ATENDENTE_RESPONSAVEL`, `TAG` (operadores de conjunto), `NUM_ATENDIMENTOS`, `NUM_MENSAGENS`, `CRIADO_EM`, `SEM_RETORNO_DIAS` (janela)

O motor de filtro está pronto e sem consumidor. Esta é a maior lacuna funcional que sobrou da primeira entrega — e é a única que não exige backend novo.

### O que construir

**Tabela**, não lista de cards. Colunas conforme `design/componentes/Agenda.html`: lead, telefone, cidade, etapa, tags, responsável, último contato.

**Barra de filtros** montada a partir do `CampoFiltravel` — **não escreva a lista de campos no frontend.** Se não existir endpoint que descreva os campos filtráveis e seus operadores, crie um `GET /api/v1/leads/filtrar/campos`. Campo novo no enum tem que aparecer na tela sem ninguém tocar no React; é o mesmo princípio dos campos customizados da E06b.

**Chips de filtro ativo, removíveis.** Cada critério aplicado vira um chip; remover o chip refaz a consulta.

**Contador "Exibindo X de Y"** usando `/contagem` — não conte no cliente, e não peça a página inteira só para saber o total.

**Paginação ou virtualização**, no padrão que a lista de conversas já usa. Uma base real de leads não cabe numa tela.

### O que NÃO construir

- **Toggle Lista↔Kanban** — sem endpoint de agrupamento por etapa com contagem; seria consulta N+1 disfarçada de UI
- **Import/export CSV** — não existe endpoint nenhum, dos dois lados
- Nada de mock para preencher qualquer um dos dois

### O ponto de segurança, e é o mais importante do bloco

O filtro deixa o usuário escolher `ATENDENTE_RESPONSAVEL`. Um atendente filtrando pelo colega **não pode** receber os leads do colega — nem por essa via, nem por `TAG`, nem por `LOCALIZACAO`, nem por qualquer combinação.

As `RN-CRM-01/02/06` são comerciais: atendente trabalha por comissão, e vazamento entre atendentes é incidente com o cliente, não bug técnico.

**Escreva os testes negativos antes da tela:**

- atendente filtrando por `ATENDENTE_RESPONSAVEL = colega` recebe **lista vazia**, não erro e não dado
- o mesmo para filtro composto que tente alcançar lead alheio por outro caminho
- `/contagem` devolve o total **já restrito** ao que o usuário pode ver — um contador que revela "existem 340 leads" para quem só enxerga 12 é vazamento de informação, mesmo sem devolver uma linha
- gestor continua vendo tudo

Se qualquer um desses falhar, **pare e me avise antes de construir a tela.**

## Bloco 2 — Flags que anunciam o que não existe

- `FEATURE_CHAT_INTERNO`: default `true` em `docker/dokploy-stack.yml` e `.env.example`. **Não existe módulo `crm-chat-interno`** — só a `V8__chat_interno.sql` e menções em testes de schema. Troque o default para `false` nos dois arquivos.
- `dashboard` e `chat_interno` no `R__seed_dev.sql`: também `false`. Impacto é só em dev, mas seed que mente treina todo mundo a ignorar flag.
- `fidelizacao` **fica como está.** Tem domínio, repositório, entity, use case de listagem e está exposta no `AutomationConfigInternalController`. Falta CRUD humano, não o módulo. Não desligue — a Automação do Dylan pode depender dessa leitura.

Registre em `docs/09` o mesmo tipo de nota que Horários recebeu.

## Bloco 3 — O teste que falta na telemetria

`RegistrarEventoDeAutomacaoUseCase` (`POST /internal/v1/eventos`) escreve telemetria real e **não tem teste de integração nenhum**. Encontrado pela E15b ao procurar evidência do RF-CRM-76.

Cubra no padrão do `ContratoAutomacaoIT`:

- evento válido persiste em `status_automacao_telemetria`
- sem `X-Synapse-Token`, ou com token errado, **401/403** — e nada gravado
- payload inválido não grava nada
- e o negativo que importa: JWT de usuário humano **não** abre `/internal/v1`

## Definição de pronto

- [ ] `/agenda` como tabela, filtros montados a partir do backend, chips removíveis, contador via `/contagem`, paginação
- [ ] **Testes negativos de isolamento passando** — inclusive no `/contagem`
- [ ] Nenhum campo filtrável escrito à mão no frontend
- [ ] Kanban e CSV ausentes, sem casca
- [ ] `FEATURE_CHAT_INTERNO=false` nos dois arquivos; seed corrigido; `fidelizacao` intocada
- [ ] `POST /internal/v1/eventos` coberto, incluindo os negativos
- [ ] **Testes de integração executados de verdade**, não só compilados
- [ ] CI verde
- [ ] Commit e push

Commit: `feat: agenda sobre o filtro modular e cobertura da telemetria`.

No relatório: me diga se o `/contagem` já vinha restrito por visibilidade ou se você precisou corrigir. Se precisou, é a décima vez que uma proteção deste projeto existia no papel e não no caminho real — e eu quero registrar.
