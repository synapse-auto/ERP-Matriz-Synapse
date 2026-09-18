# Runbook — upgrade controlado da V73

## Objetivo e invariantes

A V73 `normalizar prefixo discagem leads` é imutável: a Estrutural já a aplicou com sucesso. O
arquivo `backend/crm-app/src/main/resources/db/migration/V73__normalizar_prefixo_discagem_leads.sql`
não pode ser reescrito, renumerado ou substituído; não usar `flyway repair`, alterar
`flyway_schema_history` manualmente ou executar limpeza/fusão fora da migration.

O backend normal valida checksums. A validação ignora apenas estados `pending`/`future` para que o
Flyway possa iniciar em bancos ainda não atualizados; migrations ausentes, checksum divergente e
falhas continuam sendo erros. A estratégia consulta a lista pendente e controla a execução: se V73
estiver pendente, o boot não a executa; banco vazio/anterior à V72 avança somente até V72, e schema72
permanece intacto. A pendência é registrada como `[FLYWAY_PENDENTE]`. Essa distinção é necessária
porque o `validate()` padrão do Flyway rejeita migrations pendentes antes que a estratégia possa
aplicar somente o prefixo seguro até V72. A V73 só é executada pelo modo
one-shot `--synapse.migrations.run-once`, num contexto mínimo que não carrega API, JPA, schedulers,
consumidores ou listeners do CRM. Nesse modo, o runner substitui somente a execução física da V73
por uma migration Java de lotes curtos; o SQL versionado continua sendo a fonte do checksum e não é
alterado. O runner adquire um
`pg_try_advisory_lock` sem espera, e o próprio Flyway mantém seu lock de schema. A segunda execução
concorrente falha antes de migrar. O processo usa limites finitos (`SYNAPSE_MIGRATION_LOCK_TIMEOUT`,
default `10s`; `SYNAPSE_MIGRATION_STATEMENT_TIMEOUT`, default `30m`; `SYNAPSE_MIGRATION_TOTAL_TIMEOUT`,
default `45m`) e não repete automaticamente. O timeout total cobre inclusive espera por conexão,
validação e descoberta de migrations; ao expirar, cancela consultas, fecha as conexões do advisory
lock e do Flyway, encerra o pool e retorna erro sem manter sessão `idle in transaction`.
O retry do lock interno do Flyway é zero: se ainda houver uma sessão Flyway antiga, a execução falha
sem aguardar e precisa ser reavaliada. Cada fusão/normalização é confirmada em uma transação curta,
com reserva idempotente em `synapse_v73_runner_checkpoint`; uma interrupção deixa o histórico em 72
e pode ser retomada pelo mesmo runner, respeitando lease e limite de tentativas. Depois que V73 estiver aplicada, migrations posteriores (por
exemplo V74) voltam ao fluxo normal do Flyway; com V73 pendente, nenhum alvo posterior executa antes
dela.

Não há condição por cliente no código. O runner decide pelo estado de migrations do próprio banco.
O bridge é deliberadamente estrito: só aceita schema atual `72` com exatamente V73 pendente ou
schema atual `73` já aplicado (no-op). O Flyway do runner tem alvo `73`; qualquer outro estado é
recusado antes de iniciar migrations. Não depende de V74 ou de uma migration posterior.

## Estado operacional conhecido (sem acesso live deste workspace)

| Instância | Última evidência de schema fornecida | SHA/tag atualmente implantada |
|---|---|---|
| Fêmina | A V73 foi abortada/revertida; última versão observada: 72. | Não confirmada. `7462937` e `e3324f5` contêm V73; `78c4e53` é a última imagem publicada antes dela. |
| Estrutural | Marcondes confirmou V73 com `success = true`. | Não confirmada. A confirmação de schema não prova qual imagem está no runtime. |

Antes de executar ou liberar versão, consultar o Dokploy/runtime dos dois serviços e registrar SHA
de backend e frontend, além da última linha bem-sucedida do histórico Flyway. Não há credencial de
produção neste workspace; não inventar nem inferir esses valores.

## Antes de executar na Fêmina

1. Registrar para Fêmina e Estrutural o SHA/tag de backend e frontend realmente em execução e a
   última versão/checksum bem-sucedida do `flyway_schema_history`. Isso exige consulta de runtime e
   Dokploy; não inferir a implantação atual a partir de tags publicadas. O incidente identificou
   `7462937` e `e3324f5` como imagens que contêm V73 e `78c4e53` como a última imagem publicada antes
   dela, mas isso não determina qual tag está em execução agora. Marcondes confirmou V73 aplicada na
   Estrutural.
2. Antes de executar V73 em produção, validar esta versão em homologação com uma cópia da Estrutural
   estruturalmente equivalente. A validação também é condição para liberar esta versão à Estrutural;
   esta tarefa não autoriza deploy nela. Se V73 estiver pendente na Estrutural, bloquear o deploy e
   tratar como risco de indisponibilidade.
3. Confirmar versão/checksum atuais com consulta somente leitura:

   ```sql
   SELECT installed_rank, version, description, success, installed_on, checksum
     FROM flyway_schema_history
    ORDER BY installed_rank DESC
    LIMIT 5;
   ```

4. Se a versão corrente for `73` e `success = true`, não há upgrade a executar; o runner deve sair
   sem migrações. Se estiver em `72` com V73 pendente, continuar. Qualquer outro estado, falha de
   migration ou checksum divergente é parada obrigatória para diagnóstico — não reparar o histórico.
5. Fazer backup consistente do banco e validar que existe procedimento de restauração testado.
6. Rodar a simulação somente leitura e guardar a saída em local operacional restrito (ela pode
   conter nomes e telefones; não copiar para logs públicos/tickets sem proteção):

   ```bash
   psql "$SYNAPSE_DB_URL" -v ddi="${TELEFONE_DDI_PADRAO:-55}" \
     -f docker/provisionamento/simular-limpeza-prefixo-discagem.sql
   ```

   Comparar UPDATE/FUSAO/REVISAO MANUAL com a aprovação operacional. A simulação não substitui
   backup nem executa a migration.
7. Confirmar que os tasks antigos que iniciavam Flyway no boot foram drenados e que a versão atual
   pausa antes da V73. Usar SHA explícito, com versões de frontend/backend coerentes; nunca misturar
   imagens de SHAs diferentes. Executar em janela aprovada fora de `08:00–18:30`. A V73 varre e pode fundir
   leads, mover referências e finalizar atendimentos vazios; locks e espera por transações são um
   risco real. Não iniciar se o impacto não foi aprovado.

## Incidente que motivou o bridge

Na Fêmina, as imagens `7462937` e `e3324f5` iniciaram V73 automaticamente no startup. O schema
continuava em 72; o healthcheck encerrava o backend como unhealthy depois de mais de 30 minutos, e
os restarts iniciavam novas sessões JDBC da migration. As sessões se bloqueavam em cadeia por locks
de transação/tupla, causando HTTP 503. As tentativas abortadas não constavam como V73 bem-sucedida
no histórico. `78c4e53` é a última imagem publicada antes de V73; é uma referência histórica, não
uma instrução de rollback automático.

## Execução exclusiva

Usar a imagem já validada e as mesmas variáveis de conexão do serviço. O processo iniciado via
`docker exec` herda o ambiente do container; o modo solicitado sobe somente a composição mínima de
Flyway e datasource:

```bash
docker exec <container-backend> java -jar /application/application.jar --synapse.migrations.run-once
```

Executar uma vez. Não configurar restart automático, loop, retry de shell ou dois operadores em
paralelo. O runner recusa qualquer banco diferente de 72 com apenas V73 pendente antes de migrar;
em 73 aplicado, conclui sem trabalho. Registra `[FLYWAY_CONTROLADO] inicio`, resultado, versão,
quantidade de migrations e duração; em falhas técnicas, omite detalhes potencialmente sensíveis para
que a operação consulte o estado do banco de forma restrita. Não registra conteúdo da mensagem nem
dados dos leads. `NOTICE`
do PostgreSQL é suprimido no processo de migration porque a V73 antiga emite notices com dados de
lead. O processo termina com código diferente de zero em falha, timeout ou lock ocupado. O timeout
total também cobre travamento na descoberta/validação antes da V73, cancela o worker e fecha as
conexões rastreadas; se o encerramento do worker não for observado em até cinco segundos, não tente
novamente: preserve os logs e confirme os locks no banco antes de qualquer decisão.

Se aparecer “outra execução exclusiva”, identificar o processo que mantém o lock e aguardar/liberar
somente pelo encerramento normal dele. Não matar uma sessão PostgreSQL sem primeiro avaliar o estado
transacional. Se houver timeout, confirmar no banco que a versão segue em 72 e que `success` não foi
gravado. As transações de lote são revertidas individualmente; o checkpoint permite retomar somente
itens que não chegaram a `CONCLUIDO`/`IGNORADO`. Investigar a causa e obter nova aprovação antes de
tentar de novo. Não apagar o checkpoint para “forçar” uma repetição.

### Diagnóstico de timeout e sessões antigas

O comando abaixo lista apenas sessões do banco do CRM; filtre pelo PID do processo one-shot e não
encerre conexões em massa:

```sql
SELECT pid, state, wait_event_type, wait_event, query_start, query
  FROM pg_stat_activity
 WHERE datname = current_database()
   AND usename = current_user
 ORDER BY query_start NULLS LAST;
```

Depois de um timeout, confirme que não existe sessão do runner em `idle in transaction` e que uma
nova conexão consegue adquirir o advisory lock de forma não bloqueante:

```sql
SELECT pg_try_advisory_lock(5462350, 73);
SELECT pg_advisory_unlock(5462350, 73);
```

Se ainda houver um PID comprovadamente pertencente ao runner, encerre somente esse PID, após
registrar o estado e com aprovação operacional:

```sql
SELECT pg_terminate_backend(<pid_do_runner>);
```

Não use `pg_terminate_backend` em lote, não mate conexões do CRM normal e não altere
`flyway_schema_history`. Se houver qualquer dúvida sobre o PID, pare a operação e peça análise.

## Validações pós-upgrade

1. Confirmar no log um único resultado de sucesso com `schemaFinal=73` e `migrationsExecutadas=1`.
2. Repetir a consulta de `flyway_schema_history`: V73 deve estar `success = true`; checksum é
   validado pelo Flyway contra o arquivo imutável.
3. Confirmar as funções sem alterar dados:

   ```sql
   SELECT app_telefone_com_ddi('061999999999', '55'),
          app_telefone_canonico('061999999999', '55');
   ```

4. Comparar contagens agregadas e decisões do relatório de simulação; verificar que não ficaram
   pares inesperados, atendimentos duplicados ou filas de dados órfãs. Não imprimir conteúdo ou
   telefone individual em logs compartilhados.
5. Confirmar readiness do backend, abrir Atendimentos, Agenda e ficha de lead; validar importação
   com um contato sintético em homologação. Não usar envio real de WhatsApp como validação desta
   migration.
6. Não reprocessar webhooks nem repetir limpeza histórica automaticamente.

## Rollback

- **Falha/timeout antes do commit:** interromper novas tentativas, verificar `flyway_schema_history`
  e comparar a simulação/contagens. Se V73 não foi aplicada, identificar causa e replanejar. Não usar
  `repair` nem editar a tabela de histórico.
- **V73 concluída:** não existe downgrade seguro para os leads fundidos; a migração foi confirmada
  no banco. Rollback de aplicação só pode apontar para uma imagem previamente validada com schema
  73. Restaurar backup integral pré-upgrade é uma ação excepcional, exige decisão operacional,
  janela e avaliação explícita de perda de gravações posteriores ao backup.
- Não remover/editar função, dados ou linha de histórico manualmente para simular uma reversão.

## Checkpoint e retomada

O runner usa `synapse_v73_runner_checkpoint` apenas durante a execução controlada. Cada item é
reservado com lease, contador de tentativas e estado (`PROCESSANDO`, `CONCLUIDO`, `IGNORADO` ou
`FALHOU`). O lote padrão é 25 itens, configurável por `SYNAPSE_MIGRATION_BATCH_SIZE`; leases e
tentativas também são finitos (`SYNAPSE_MIGRATION_BATCH_LEASE` e
`SYNAPSE_MIGRATION_MAX_BATCH_ATTEMPTS`). O processo não faz retry automático no shell: depois de
uma falha, validar locks, estado e causa antes de iniciar novamente. Uma nova execução retoma itens
com lease expirado e tentativas disponíveis, sem repetir itens concluídos. A migration V77 remove a
tabela auxiliar depois que a V73 foi registrada com sucesso; não a remova manualmente.

Para acompanhar progresso, filtre os logs por `[FLYWAY_CONTROLADO]` e, se necessário, consulte
somente contagens da tabela auxiliar:

```sql
SELECT fase, estado, count(*)
  FROM synapse_v73_runner_checkpoint
 GROUP BY fase, estado
 ORDER BY fase, estado;
```

Essa consulta não retorna telefone, nome ou conteúdo de lead. O runner não registra os valores dos
itens, e o SQL legado não é executado no caminho controlado (seus `NOTICE` ficam suprimidos).

## Proteção da Estrutural

Marcondes confirmou que a Estrutural tem a linha V73 com `success = true`; esse checksum não será
alterado. Esta tarefa não consulta nem opera banco, Dokploy ou serviço de produção. Mesmo com V73 já
aplicada, a versão corrigida só pode ser liberada para a Estrutural depois da validação da migration
em homologação com cópia estrutural equivalente. Se uma consulta posterior mostrar V73 pendente,
tratar como risco de indisponibilidade e bloquear o deploy; não tentar executar durante o horário
protegido.
