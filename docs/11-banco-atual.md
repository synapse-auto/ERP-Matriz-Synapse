# 11. Banco de Dados — Estado Atual

Documentação do schema **como está implementado**, extraída das migrations Flyway. Diferente do `03-modelo-dados-postgres.md`, que é o documento de *projeto* — onde os dois divergirem, este vence.

**Estado:** 47 migrations · schema vigente em `backend/crm-app/src/main/resources/db/migration/`
**Última migration:** `V47__lead_codigo.sql`

---

## 1. Histórico de migrations

| Migration | Conteúdo |
|---|---|
| `V1__extensoes_e_tipos` | `pg_trgm` + 16 ENUMs |
| `V2__equipe` | `usuario`, `avaliacao`, rotinas, disponibilidade para IA, horários |
| `V3__configuracao_base` | `canal`, `canal_credencial`, `etapa_atendimento` |
| `V4__crm_core` | `lead`, `tag`, `lead_tag`, `lembrete`, `mensagem_programada`, `mensagem_rapida`, `evento_timeline`, `preferencia_usuario`, `arquivo_banco` |
| `V5__atendimento` | `atendimento`, `mensagem` (particionada) + funções de partição |
| `V6__campanhas` | `filtro_modular`, `campanha`, `campanha_mensagem`, `campanha_mensagem_metrica` |
| `V7__automacao_config` | `configuracao_automacao`, regras, festivas, resumo IA, telemetria |
| `V8__chat_interno` | conversa, participante, mensagem |
| `V9__infra_transversal` | `audit_log`, `feature_flag`, `outbox_evento` |
| `V10__indices` | Índices de otimização (os de regra de negócio ficam com suas tabelas) |
| `V11__refresh_token` | Sessão revogável |
| `V12__rls_isolamento_lead` | Políticas RLS nas 4 tabelas sensíveis |
| `V13__role_da_aplicacao` | Role `synapse_app` sem privilégio de dono |
| `V14__lead_ultima_interacao` | Coluna denormalizada para `semRetornoDias` |
| `V15__entrega_pendente_e_falhou` | `PENDENTE` e `FALHOU` no ciclo de entrega |
| `V16__outbox_e_webhook` | Retry da outbox + `webhook_entrada` |
| `V17__webhook_payload_texto` | `payload` de JSONB → **TEXT** (reverificação de HMAC) |
| `V18__campos_customizados` | `campo_customizado` + `lead.dados_customizados` |
| `V19__mensagem_rapida_chave_case_insensitive` | Unicidade de palavra-chave sem diferenciar maiúsculas/minúsculas |
| `V20__ator_estruturado_timeline` | `evento_timeline.ator_id` + `dados` JSONB |
| `V21__resultado_etapa` | ENUM `resultado_etapa`, coluna em etapa e unicidade parcial de `GANHO` |
| `V22__indice_historico_etapa` | Índice `(tipo, criado_em)` para métricas e início explícito do histórico de transições |
| `V37__chat_interno_leitura_e_rls` | `lido_ate`, índice temporal e RLS do chat interno |
| `V43__avaliacao_unica_por_atendimento` | UK `atendimento_id` + índice `criado_em` em `avaliacao` |
| `V45__reacoes_de_mensagem` | `mensagem_reacao` (FK composta da partição) e `chat_interno_mensagem_reacao` (RLS de participação) |
| `V46__referencia_e_id_externo_de_mensagem` | `mensagem_id_externo` (wamid) e `mensagem_referencia` (citação/encaminhamento) |
| `V47__lead_codigo` | `lead.codigo VARCHAR(20)`, CHECK somente dígitos, nullable |

> `pgcrypto` foi removida na E01b — Postgres 13+ tem `gen_random_uuid()` nativo. **A única extensão exigida é `pg_trgm`.**

---

## 2. Tipos enumerados

| Tipo | Valores |
|---|---|
| `papel_usuario` | ATENDENTE, SUBGESTOR, GESTOR, ADMINISTRADOR |
| `status_presenca` | ONLINE, AUSENTE, OFFLINE |
| `status_basico_lead` | IA, EM_ATENDIMENTO, FINALIZADO |
| `status_atendimento` | EM_IA, EM_ATENDIMENTO, FINALIZADO |
| `remetente_tipo` | LEAD, ATENDENTE, SISTEMA, IA |
| `tipo_mensagem` | TEXTO, AUDIO, IMAGEM, DOCUMENTO |
| `status_entrega` | PENDENTE, ENVIADO, ENTREGUE, LIDO, FALHOU |
| `status_lembrete` | PENDENTE, CONCLUIDO |
| `status_msg_prog` | AGENDADA, ENVIADA, CANCELADA |
| `status_campanha` | RASCUNHO, ATIVA, PAUSADA, ENCERRADA |
| `contexto_filtro` | ATENDIMENTOS, AGENDA, CAMPANHA |
| `tipo_rotina` | PLANTAO, FECHADO |
| `dia_semana` | SEG…DOM |
| `origem_evento` | SISTEMA, AUTOMACAO, USUARIO |
| `gatilho_resumo` | A_CADA_X_MENSAGENS, AO_FINALIZAR, AMBOS |
| `tipo_conversa_chat` | DIRETA, GRUPO |
| `resultado_etapa` | EM_ANDAMENTO, GANHO, PERDIDO |

---

## 3. Tabelas por domínio

### 3.1 Equipe

**`usuario`** — `id`, `nome`, `email` (UK), `senha_hash`, `papel`, `status_presenca`, `ativo`, `criado_em`, `senha_alterada_em` (V28, E29 — `NULL` = senha provisória, nunca trocada pelo dono)
**`refresh_token`** — `id`, `usuario_id`, `token_hash` (UK, SHA-256), `familia`, `expira_em`, `revogado_em`, `criado_em`
**`avaliacao`** — `id`, `atendimento_id` (UK), `atendente_id`, `nota` (CHECK 1–5), `comentario`, `criado_em`
**`horario_trabalho`** — `id`, `aplicavel_a`, `dia_semana`, `inicio`, `fim` (CHECK `fim > inicio`)
**`rotina_disponibilidade`** / **`rotina_disponibilidade_atendente`** — plantão e fechado por dia
**`disponibilidade_atendente_ia`** — `atendente_id` (PK), `disponivel_para_ia`, `atualizado_em`

### 3.2 Configuração base

**`canal`** — `id`, `nome` (UK), `tipo`, `ativo`
**`canal_credencial`** — `id`, `canal_id`, `numero`, `identificador_externo`, `token_ref`, `ativo`, `vigente_desde`, `vigente_ate`

> `token_ref` é **referência** ao secret manager, nunca o token.
> `idx_canal_credencial_ativa` (único parcial `WHERE ativo`) garante no banco uma credencial ativa por canal.

**`etapa_atendimento`** — `id`, `nome`, `ordem` (UK), `cor_visual`, `resultado` (`EM_ANDAMENTO` por default; no máximo uma `GANHO` por índice único parcial)

### 3.3 CRM Core

**`lead`** — 20 colunas:

`id`, `nome`, `foto_url`, `telefone`, `email`, `cpf`, `empresa`, **`codigo`** (V47, somente dígitos), `localizacao`, `canal_origem_id`, `status_basico`, `etapa_atendimento_id`, `atendente_responsavel_id`, `notas`, `resumo_ia`, `num_atendimentos`, `num_mensagens`, `criado_em`, **`ultima_interacao_em`** (V14), **`dados_customizados`** JSONB (V18)

> Contadores e `ultima_interacao_em` são **denormalizados**, escritos na mesma transação que registra mensagem/atendimento. `ultima_interacao_em` usa `GREATEST` para não retroceder em reentrega de webhook.
> `notas`, `resumo_ia` e `dados_customizados` **nunca entram em projeção de listagem**.
> `codigo` entra no card da lista de Atendimentos (`leadCodigo` na inbox). **Não** entra em `LeadResumo` (Agenda). Sem unique e sem índice de busca — o campo não é critério de filtro.
> Constraint `lead_codigo_somente_digitos`: `NULL` ou `^[0-9]+$`. A aplicação normaliza string vazia para `NULL` (`CodigoDoLead`).

**`tag`** · **`lead_tag`** · **`lembrete`** · **`mensagem_programada`** · **`mensagem_rapida`** · **`evento_timeline`** (append-only; `ator_id` identifica quem executou e `dados` JSONB guarda, em `ETAPA_ALTERADA`, etapas anterior/nova e `responsavel_id` comercial) · **`preferencia_usuario`** · **`arquivo_banco`**

**`campo_customizado`** (V18) — `chave` (PK, CHECK de identificador seguro), `rotulo`, `tipo` (CHECK), `opcoes`, `obrigatorio`, `filtravel`, `ordem`

### 3.4 Atendimento

**`atendimento`** — `id`, `lead_id`, `canal_id`, `canal_credencial_id`, `atendente_id`, `status`, `iniciado_em`, `finalizado_em`

**`mensagem`** — **particionada por `RANGE (enviado_em)`**, PK composta `(id, enviado_em)`:

`id`, `atendimento_id`, `remetente_tipo`, `remetente_id`, `tipo`, `conteudo`, `midia_url`, `midia_metadados`, `status_entrega`, `enviado_em`

Partições geridas por função, com janela relativa a `now()` — **não** há partições literais no SQL. Existe `mensagem_default` como rede de segurança de último recurso, com alarme diário se receber qualquer linha.

### 3.5 Campanhas *(fora da primeira entrega — tabelas existem, UI não)*

`filtro_modular` (critérios JSONB + índice GIN), `campanha`, `campanha_mensagem`, `campanha_mensagem_metrica`

### 3.6 Automação — configuração

`configuracao_automacao` (chave-valor tipado com faixa min/max), `regra_follow_up`, `regra_fidelizacao`, `mensagem_festiva`, `configuracao_resumo_ia` (singleton), `status_automacao_telemetria` (singleton)

### 3.7 Chat interno *(E44 — fase direta de texto)*

`chat_interno_conversa`, `chat_interno_participante` (com `lido_ate`), `chat_interno_mensagem`. A V37 aplica RLS por participação e mantém o índice temporal da V10 de forma idempotente. A função booleana de participação usa a role `synapse_chat_rls` (NOLOGIN/BYPASSRLS) apenas para evitar recursão da própria política; a aplicação continua assumindo `synapse_app`.

### 3.8 Infraestrutura transversal

**`audit_log`** — `id` BIGINT identity, `ator_id`, `ator_tipo`, `acao`, `entidade_tipo`, `entidade_id`, `lead_id`, `dados_antes`, `dados_depois`, `ip`, `criado_em`
**`feature_flag`** — `chave` (PK), `habilitado`, `descricao`
**`outbox_evento`** — `id`, `tipo`, `payload`, `criado_em`, `publicado_em`, `tentativas`, `proxima_tentativa_em`, `ultimo_erro`
**`webhook_entrada`** — `id_externo` (PK, idempotência), `provedor`, **`payload` TEXT** (V17 — byte a byte, para reverificar HMAC), `recebido_em`, `processado_em`, `tentativas`, `ultimo_erro`, `esgotado_em`

---

## 4. Segurança no banco

### 4.1 Row-Level Security

Ativo em **`lead`**, **`atendimento`**, **`lembrete`** e **`mensagem_programada`**, com `FORCE ROW LEVEL SECURITY`.

Cada transação executa, antes de qualquer consulta:

```sql
SET LOCAL ROLE synapse_app;      -- role sem privilégio de dono (V13)
SET LOCAL app.usuario_id = '…';
SET LOCAL app.papel      = '…';
```

> **`SET LOCAL ROLE` é o que faz a proteção funcionar.** Dono de tabela ignora RLS; superusuário ignora inclusive com `FORCE`. Sem a troca de role, as políticas existem e ninguém está protegido.

| Contexto | Comportamento |
|---|---|
| Requisição autenticada | Conforme papel |
| Serviço (fila, jobs, outbox, migrations) | Vê tudo |
| Sem contexto | **Zero linhas** — falha fechado |

`lembrete` e `mensagem_programada` são mais restritivos que `lead`: não têm o escape de "lead em IA", porque são pessoais (`RN-CRM-04`).

### 4.2 Funções auxiliares

`app_usuario_id()`, `app_papel()`, `app_e_servico()`, `app_enxerga_todos_os_leads()`

---

## 5. Funções de manutenção

| Função | Papel |
|---|---|
| `criar_particao_mensagem(data)` | Cria a partição do mês |
| `garantir_particoes_mensagem(n)` | Garante n meses à frente (chamada com 3) |
| `particoes_mensagem_faltantes()` | Usada pela verificação de boot — **a aplicação não sobe sem partição do mês corrente e do próximo** |
| `mensagens_na_particao_default()` | Alarme se a rede de segurança receber linha |
| `outbox_esgotadas()` | Alarme de eventos que estouraram o retry |

---

## 6. Índices notáveis

**Regra de negócio (não são otimização):**

- `idx_canal_credencial_ativa` — único parcial, uma credencial ativa por canal
- `idx_etapa_ordem` — único, ordem do funil
- `idx_msg_rapida_atendente_chave` — único, palavra-chave por atendente

**Busca e filtro:**

- `idx_lead_nome_trgm` — GIN trigram, busca por nome (`ILIKE '%…%'`)
- `idx_lead_dados_customizados` — GIN, campos customizados
- `idx_filtro_criterios` — GIN, árvore de critérios

**Parciais** (menores e mais rápidos): `idx_lead_status_basico`, `idx_msg_prog_envio`, `idx_outbox_pendente`, `idx_outbox_a_publicar`, `idx_webhook_a_processar`, `idx_refresh_token_familia_ativa`, `idx_usuario_papel`

**BRIN:** `idx_audit_criado_em` — barato para dados append-only cronológicos

---

## 7. Pré-requisitos de ambiente

Verificar **antes** do primeiro deploy (ver `docs/10` §2):

1. **`pg_trgm` disponível.** Exige privilégio elevado; em Postgres gerenciado pode precisar ser habilitada fora da migration, ou a V1 falha.
2. **Usuário das migrations pode `CREATE ROLE`** e conceder `synapse_app` a si mesmo. Se o usuário das migrations for diferente do da aplicação, o `GRANT` vai para o errado e **o RLS deixa de funcionar** — faça um teste de fumaça logando como atendente.
3. **Pool em modo transaction** se usar PgBouncer. O RLS depende de `SET LOCAL`; `SET` de sessão vazaria contexto entre usuários.
4. **Partições cobertas.** O boot falha se faltar a do mês corrente ou próximo — por desenho.

---

## 8. Multi-tenancy

**Não existe `tenant_id` em nenhuma tabela.** O modelo é instância isolada por cliente: deploy e banco próprios por filho, isolamento físico.

O RLS aqui é **entre atendentes do mesmo cliente**, não entre clientes.

Extensibilidade por filho vive em `lead.dados_customizados` + `campo_customizado` — **nunca** em coluna nova específica do ramo do cliente.
