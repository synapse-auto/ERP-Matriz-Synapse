# Histórico de agendamentos da integração — passagem da PR #291

## Fronteira e implantação

A V100 proposta na PR #291 não foi aplicada em nenhum banco, conforme confirmação da operação.
Foi retirada do Flyway: o histórico específico da integração clínica não pertence ao modelo Base PAI
nem deve conceder ao n8n acesso SQL ao banco do CRM (docs/16 e docs/21).

O artefato versionado é [sql/001-agendamento-evento.sql](sql/001-agendamento-evento.sql), destinado
**exclusivamente ao banco próprio da integração/n8n**, no schema privado `automacao_agendamentos`.
Não modifica tabelas internas do n8n. Requer PostgreSQL 15+, incluindo a view com `security_invoker`.
Sua instalação deve fazer parte do provisionamento versionado/controlado da integração, com revisão,
backup e credencial apropriada. **Não executar SQL manual em produção.** Merge/deploy do CRM não
instala esse artefato. O script recusa banco com marcadores do CRM e falha se já estiver instalado;
não tenta corrigir divergências silenciosamente. Nenhuma migration já aplicada foi editada.

## O que Dylan deve adaptar no n8n

| Antes, na proposta | Agora |
| --- | --- |
| Banco do CRM, tabela `public.agendamento_evento` | Banco próprio da integração, `automacao_agendamentos.agendamento_evento` |
| INSERT direto | Chamada parametrizada de `automacao_agendamentos.registrar_evento($1::jsonb, $2)` |
| `clinica_id` omitido, default 1 | Identificador real da integração obrigatório, positivo; nunca inferido pelo nome do cliente |
| `lead_id`/`atendimento_id` TEXT | UUIDs opcionais, provenientes do CRM; atendimento exige lead correspondente |
| `darwin_payload` integral | Removido; enviar somente campos permitidos |
| `atualizado_em` | Removido: eventos não são sobrescritos pela função |
| `idempotency_key` única global, sem contrato de replay | Chave preservada, escopada pelo `clinica_id`, com replay validado pela função |
| View pública | `automacao_agendamentos.vw_cancelamentos_agendamento`, privada |

SQL do nó Postgres, **com parâmetros vinculados**, não interpolação de expressão em SQL:

```sql
SELECT evento_id, repetido
FROM automacao_agendamentos.registrar_evento($1::jsonb, $2);
```

Parâmetro 1: objeto com `clinica_id`, `schedule_id`, `acao` obrigatórios; opcionais `lead_id`,
`atendimento_id`, `paciente_nome`, `telefone`, `profissional_nome`, `tipo_agendamento`,
`agendado_para` (ISO 8601 com offset), `descricao`, `resumo`. Outros campos são recusados.
Parâmetro 2: chave estável do evento de origem. Não usar apenas schedule, horário de execução ou
conteúdo: um mesmo agendamento pode receber eventos diferentes. Preservar a chave nos retries.

Ações existentes preservadas: `CONFIRMADO`, `CANCELADO`, `REAGENDAMENTO_SOLICITADO`.
Mesmo evento e mesmos dados retornam o ID original com `repetido=true`; a primeira gravação retorna
`false`. Mesma chave com dados diferentes gera SQLSTATE `23505` e preserva o evento anterior.
Clínicas externas distintas podem usar a mesma chave. Isso não é multi-tenancy do CRM.
Campos inválidos falham por validação/tipagem/constraints; não repetir automaticamente erro de dados.

As referências UUID são correlação entre bancos, **não foreign keys**. O workflow deve preservar o
par canônico retornado pelo CRM e validar a relação pelas APIs existentes antes de usá-lo. Sem
correlação confiável, deixar ambos ausentes; nunca fabricar UUID ou assumir relação por telefone.

O artefato não cria usuário/senha nem concede acesso a PUBLIC. A operação deve atribuir uma
credencial restrita ao banco da integração. A função é SECURITY INVOKER: exige permissões explícitas
de uso do schema, SELECT/INSERT na tabela e uso da sequência; não eleva privilégios. Uma credencial
proprietária ainda pode modificar diretamente a tabela: não afirmar que há imutabilidade contra o
owner. Não conceder UPDATE/DELETE à credencial do workflow. Leitura de nomes/telefones é sensível:
restringir acesso e configurar retenção conforme a política operacional. Não copiar payload bruto,
tokens ou mensagens completas para este histórico, para logs ou para execuções persistidas do n8n.
Diagnósticos compartilhados devem conter somente IDs técnicos, etapa e SQLSTATE sanitizado; não
registrar o texto bruto de erros SQL que possa incluir valores recebidos.

## Reflexo no CRM: somente API existente

Gravar o histórico externo não cria lembrete na ficha do CRM. Para criar um lembrete, o contrato já
existente em docs/21 é:

```http
POST /internal/v1/atendimentos/{id}/lembretes
X-Synapse-Token: <referência segura da credencial existente>
Idempotency-Key: <chave estável desta operação>
Content-Type: application/json
```

Body: `{ "texto": "...", "dataHora": "<ISO 8601 com offset>" }`.
O atendimento deve existir e ter responsável humano atual; sem responsável o contrato retorna 409.
Preservar a chave durante retry: mesmo corpo/atendimento reproduz a resposta; mudança conflita.
Usar chave específica da operação CRM, derivada do evento estável e da clínica, sem dados pessoais.
Não finalizar atendimento, enviar mensagem ou mudar classificação por efeito deste histórico.
Não inferir prazo/texto do lembrete sem regra aprovada para o workflow. As duas gravações são em
sistemas diferentes: não há transação distribuída; o n8n deve acompanhar o resultado de cada etapa
e repetir somente a etapa pendente, preservando suas respectivas chaves.

## Evidências e limites

`AgendamentoIntegracaoIT` executa o SQL versionado em PostgreSQL 15 real: replay, conflito,
concorrência, campos inválidos, ações/view, acesso negado, reinstalação e bloqueio do banco CRM.
`SchemaMigracoesIT` mantém o catálogo oficial e prova que esse histórico não entra nas migrations.
O SQL é recurso **de teste**, não recurso de produção do JAR. Nenhum endpoint ou permissão CRM mudou.

A validação do workflow n8n real e seu provisionamento continuam a cargo da integração; os testes
do CRM não comprovam uma instalação externa nem homologação com pacientes. Não houve deploy.
**Dokploy do CRM: nenhuma variável nova ou alteração necessária.** Ação operacional externa:
provisionar o artefato pelo processo aprovado e adaptar o workflow/credencial de banco antes de usá-lo.
