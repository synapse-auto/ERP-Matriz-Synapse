# Runbook — upgrade controlado da V73

## Objetivo e invariantes

A V73 `normalizar prefixo discagem leads` é imutável: a Estrutural já a aplicou com sucesso. O
arquivo `backend/crm-app/src/main/resources/db/migration/V73__normalizar_prefixo_discagem_leads.sql`
não pode ser reescrito, renumerado ou substituído; não usar `flyway repair`, alterar
`flyway_schema_history` manualmente ou executar limpeza/fusão fora da migration.

O backend normal valida checksums. Se V73 estiver pendente, o boot não a executa: um banco vazio ou
anterior à V72 pode avançar somente até V72, e um schema72 permanece intacto. A pendência é registrada
como `[FLYWAY_PENDENTE]`. A V73 só é executada pelo modo one-shot `--synapse.migrations.run-once`, num contexto
mínimo que não carrega API, JPA, schedulers, consumidores ou listeners do CRM. O runner adquire um
`pg_try_advisory_lock` sem espera, e o próprio Flyway mantém seu lock de schema. A segunda execução
concorrente falha antes de migrar. O processo usa limites finitos (`SYNAPSE_MIGRATION_LOCK_TIMEOUT`,
default `10s`; `SYNAPSE_MIGRATION_STATEMENT_TIMEOUT`, default `30m`) e não repete automaticamente.
O retry do lock interno do Flyway é zero: se ainda houver uma sessão Flyway antiga, a execução falha
sem aguardar e precisa ser reavaliada. Depois que V73 estiver aplicada, migrations posteriores (por
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
lead. O processo termina com código diferente de zero em falha, timeout ou lock ocupado.

Se aparecer “outra execução exclusiva”, identificar o processo que mantém o lock e aguardar/liberar
somente pelo encerramento normal dele. Não matar uma sessão PostgreSQL sem primeiro avaliar o estado
transacional. Se houver timeout, a V73 roda na transação padrão do Flyway: confirmar no banco que a
versão segue em 72 e que `success` não foi gravado, investigar a causa e obter nova aprovação antes
de tentar de novo.

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

## Correção histórica futura

A V73 imutável ainda contém a normalização/fusão histórica. O runner torna essa execução exclusiva
e fora do boot, mas não transforma a limpeza em manutenção paginada e interrompível. Uma futura
correção ou retomada dos dados precisa ser uma operação separada, com dry-run, lotes, checkpoint
observável, critérios de fusão comprovados e aprovação explícita. Não iniciar uma segunda limpeza
nem reprocessar dados como parte deste upgrade.

## Proteção da Estrutural

Marcondes confirmou que a Estrutural tem a linha V73 com `success = true`; esse checksum não será
alterado. Esta tarefa não consulta nem opera banco, Dokploy ou serviço de produção. Mesmo com V73 já
aplicada, a versão corrigida só pode ser liberada para a Estrutural depois da validação da migration
em homologação com cópia estrutural equivalente. Se uma consulta posterior mostrar V73 pendente,
tratar como risco de indisponibilidade e bloquear o deploy; não tentar executar durante o horário
protegido.
