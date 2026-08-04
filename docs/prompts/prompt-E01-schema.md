# Prompt E01 — Schema e migrations

> Pré-requisito: E00 concluída e commitada (`1549651`).
> Calibrado com o que foi construído na E00.

---

**Etapa E01 — Schema e migrations.**

Leia `CLAUDE.md` e `docs/03-modelo-dados-postgres.md` — o documento contém o DDL completo já revisado. Sua tarefa é transformá-lo em migrations Flyway versionadas e testadas.

## Contexto herdado da E00

- Postgres do container roda na **porta 55432** (há um Postgres nativo ocupando a 5432). Use `POSTGRES_PORT` do `.env`; não assuma 5432 em lugar nenhum.
- Existem dois DataSources (`generalDataSource` é `@Primary`, `chatDataSource` é a reserva do caminho crítico). **O Flyway deve migrar pelo `generalDataSource`** — migration não pode consumir a reserva do chat.
- `ArquiteturaTest` (ArchUnit) está em `crm-app` com `failOnEmptyShould=false` porque os pacotes de domínio estão vazios. Esta etapa não cria classes de domínio, então mantenha como está — a flag volta a `true` na E02.
- Os módulos são **8**: `chat_interno_*` pertence a `crm-equipe` e `arquivo_banco` a `crm-core` (ver `CLAUDE.md`). Não afeta o SQL, mas oriente os comentários das migrations.

## O que construir

### 1. Migrations Flyway

Em `crm-app/src/main/resources/db/migration`, quebre o DDL em migrations ordenadas por assunto — nem uma migration gigante, nem uma por tabela:

```
V1__extensoes_e_tipos.sql          pgcrypto, pg_trgm + todos os ENUMs
V2__equipe.sql                     usuario, avaliacao, rotina_disponibilidade(_atendente),
                                   disponibilidade_atendente_ia, horario_trabalho
V3__configuracao_base.sql          canal, canal_credencial, etapa_atendimento
V4__crm_core.sql                   lead, tag, lead_tag, lembrete, mensagem_programada,
                                   mensagem_rapida, evento_timeline, preferencia_usuario,
                                   arquivo_banco
V5__atendimento.sql                atendimento, mensagem (particionada) + partições iniciais
V6__campanhas.sql                  filtro_modular, campanha, campanha_mensagem,
                                   campanha_mensagem_metrica
V7__automacao_config.sql           configuracao_automacao, regra_follow_up, regra_fidelizacao,
                                   mensagem_festiva, configuracao_resumo_ia,
                                   status_automacao_telemetria
V8__chat_interno.sql               chat_interno_conversa / participante / mensagem
V9__infra_transversal.sql          audit_log, feature_flag, outbox_evento
V10__indices.sql                   todos os índices, juntos para facilitar revisão
```

Inclua **todos** os índices do documento, inclusive os parciais, GIN e BRIN. Não são otimização prematura — o §3 do documento justifica cada um com o requisito que ele atende.

Dois pontos do DDL fáceis de transcrever errado:

- `atendimento.canal_credencial_id` aparece no documento como `ALTER TABLE` posterior. Como `canal_credencial` está na V3 e `atendimento` na V5, declare a coluna já na criação da tabela e dispense o `ALTER`.
- `idx_canal_credencial_ativa` é índice **único parcial** (`WHERE ativo`). É regra de negócio garantida pelo banco, não otimização.

### 2. Partições de `mensagem`

Crie as partições iniciais e um mecanismo de criação das futuras: função PL/pgSQL + `@Scheduled` mensal no Spring. Não introduza pg_partman nesta fase.

**Isto é crítico:** um `INSERT` numa faixa sem partição falha, e isso derruba o envio de mensagens — exatamente o que a regra de precedência do `CLAUDE.md` proíbe. Portanto:

- Crie partições com **pelo menos 3 meses** de antecedência
- O job mensal garante a janela inteira, não apenas o próximo mês
- Adicione uma verificação na inicialização que **falha o boot** se faltar a partição do mês corrente ou do próximo. Falhar no start é muito melhor do que falhar no primeiro envio às 9h da manhã.

### 3. Seed de desenvolvimento

Migration repetível (`R__seed_dev.sql`) ou `data.sql` por perfil, com:

- Etapas do funil ("Novo contato" → "Pós-venda"), com `ordem` sequencial
- Canal WhatsApp + uma `canal_credencial` de exemplo (`token_ref` fictício, **nunca** token real)
- Usuário administrador, um gestor, um subgestor e dois atendentes (senhas com BCrypt)
- Tags com cor e ícone
- Feature flags iniciais: `campanhas`, `chat_interno`, `fidelizacao`, `relatorios`, `dashboard`
- Parâmetros de `configuracao_automacao` com `tipo`, `unidade` e faixas `min`/`max` preenchidos

O seed **não pode rodar em produção**. Garanta por perfil Spring (`@Profile("dev")` ou `spring.flyway.locations` condicional), não por convenção.

### 4. Testes com Testcontainers

Testcontainers sobe o próprio Postgres em porta aleatória — não depende do compose nem do conflito da 5432.

Escreva testes que provem:

- Todas as migrations rodam do zero em banco limpo
- Tabelas e ENUMs esperados existem (compare com lista explícita, não com `count(*)`)
- Índices críticos existem: `idx_lead_atendente`, `idx_canal_credencial_ativa`, `idx_outbox_pendente`, `idx_lead_nome_trgm`
- **Inserir uma segunda `canal_credencial` ativa no mesmo canal falha** — regra de negócio no banco, vale provar
- Inserir em `mensagem` numa data coberta funciona; a verificação de partição faltante dispara quando esperado

## Restrições

- Nenhuma tabela com `tenant_id`
- Nunca edite migration já aplicada — se estiver errada, nova migration
- `canal_credencial.token_ref` guarda **referência** ao secret manager, jamais o token
- Nenhuma classe de domínio nesta etapa: é schema e testes

## Definição de pronto

- [ ] `flyway migrate` roda do zero sem erro
- [ ] Seed popula o ambiente de dev e não roda fora de `dev`
- [ ] Testes com Testcontainers passam, incluindo o do índice único parcial
- [ ] Boot falha com mensagem clara se faltar partição de `mensagem`
- [ ] CI verde

Commit: `feat: schema inicial e migrations`.

Ao terminar, liste as divergências entre o DDL do documento e o que foi necessário na prática — vou atualizar a documentação.
