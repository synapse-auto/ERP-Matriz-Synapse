# Habilitar `data_nascimento` na Estrutural — operação controlada

**Estado desta entrega:** procedimento preparado e testado; **não aplicado** em `erp-matriz-hml-oxj4cd` nem em qualquer banco real. A leitura operacional informada antes desta entrega encontrou zero linhas para a chave; repita a leitura imediatamente antes da aplicação. Não há deploy, variável nova no Dokploy nem migration Flyway.

## Contrato existente

- `GET /api/v1/campos-customizados` lista o metadado; a Agenda de Contatos mostra **Data de nascimento** na parte inferior da ficha lateral, seção **Campos**, abaixo de **Notas**, quando o cadastro existe. O rótulo é o metadado `rotulo`, não uma string fixa do React; o tipo `DATA` usa `SeletorData`.
- `PUT /api/v1/leads/{id}` salva `dadosCustomizados.data_nascimento` pela permissão existente `contatos.editar` e visibilidade do lead. Outros campos cadastrados devem seguir no mesmo mapa enviado pela ficha.
- `POST /internal/v1/leads/{id}/data-nascimento` usa `X-Synapse-Token` e `Idempotency-Key`; grava a mesma chave somente quando ela está vazia. Metadado ausente ou de outro tipo é recusado.
- `GET /internal/v1/fidelizacao/aniversariantes-hoje` só lista datas válidas quando há metadado `DATA` e a configuração de aniversário está habilitada. Sem campo, data válida ou configuração ativa, a lista permanece vazia. Criar o metadado **não ativa envios** nem preenche leads antigos.

## Pré-condições e leitura sem escrita

Execute no host Docker da stack **`erp-matriz-hml-oxj4cd`**, com o checkout deste repositório contendo `docker/provisionamento/habilitar-data-nascimento.sql`. Confirme a identidade do serviço e do banco; não use o Postgres do próprio Dokploy. Não execute entre **08:00 e 18:30** nem durante indisponibilidade de Atendimentos. Faça backup/snapshot conforme a operação normal da instância antes de autorizar a escrita.

```bash
PG="$(docker ps -q --filter 'label=com.docker.swarm.service.name=erp-matriz-hml-oxj4cd_postgres')"
test -n "$PG" && test "$(printf '%s\n' "$PG" | wc -l)" -eq 1 || { echo 'Postgres da Estrutural não identificado de modo único'; exit 1; }
DB="$(docker exec "$PG" printenv POSTGRES_DB)"
DB_USER="$(docker exec "$PG" printenv POSTGRES_USER)"
test "$DB" = matriz_hml && test -n "$DB_USER" || { echo 'Banco inesperado; pare'; exit 1; }
docker inspect "$PG" --format '{{.Name}} {{index .Config.Labels "com.docker.swarm.service.name"}}'
docker exec "$PG" psql -X -v ON_ERROR_STOP=1 -U "$DB_USER" -d "$DB" -c \
  "SELECT chave, rotulo, tipo, opcoes, obrigatorio, filtravel, ordem FROM campo_customizado ORDER BY ordem, chave;"
docker exec "$PG" psql -X -v ON_ERROR_STOP=1 -U "$DB_USER" -d "$DB" -c \
  "SELECT chave, rotulo, tipo, opcoes, obrigatorio, filtravel, ordem FROM campo_customizado WHERE chave = 'data_nascimento';"
```

Se já houver `data_nascimento`, compare `rotulo`, `tipo`, `opcoes`, `obrigatorio` e `filtravel` com o contrato abaixo. **Pare e investigue** qualquer diferença; nunca use `ON CONFLICT DO UPDATE`. Uma ordem existente, ainda que diferente da que o script escolheria hoje, permanece intacta. Confira os demais campos para identificar a ordem atual e qualquer dado sensível antes de executar.

## Aplicação explícita, uma instância só

Somente após a pré-verificação e aprovação operacional, rode **uma vez** no mesmo shell e host:

```bash
docker exec -i "$PG" psql -X -v ON_ERROR_STOP=1 -U "$DB_USER" -d "$DB" \
  < docker/provisionamento/habilitar-data-nascimento.sql
```

O SQL usa `LOCK ... NOWAIT`: disputa de escrita faz a operação falhar imediatamente, sem esperar no caminho de Atendimentos. O bloco é atômico. Na ausência da chave, insere `data_nascimento`, `Data de nascimento`, `DATA`, `opcoes=NULL`, `obrigatorio=false`, `filtravel=false`, com `ordem = MAX(ordem)+1` (ou 1 se vazia). Não altera linhas existentes de `campo_customizado` nem qualquer linha de `lead`. Se a chave já for compatível, a repetição não grava nada; se for incompatível, aborta sem alteração. **Não** execute este arquivo como Flyway ou em outra instância.

## Verificação posterior

```bash
docker exec "$PG" psql -X -v ON_ERROR_STOP=1 -U "$DB_USER" -d "$DB" -c \
  "SELECT chave, rotulo, tipo, opcoes, obrigatorio, filtravel, ordem FROM campo_customizado WHERE chave = 'data_nascimento';"
```

Esperado: exatamente uma linha, com os metadados acima e ordem posterior à dos campos vistos antes. Com um usuário **autorizado** da Estrutural, abra **Agenda de Contatos → lead → ficha lateral → Campos**, selecione uma data de teste em um lead apropriado, salve e recarregue. Confirme pela API de leitura do mesmo lead ou, se houver autorização operacional para consultar seu registro, por `dados_customizados ->> 'data_nascimento'`. Confira que outros campos continuam preenchidos. Não use um lead de cliente real só para testar nem dispare mensagens de aniversário. Leads antigos sem data continuam sem aniversário; não há backfill.

Se a operação falhar, **não** use `flyway repair`, não altere `flyway_schema_history` e não repita cegamente. Investigue a mensagem e refaça a pré-verificação. Reverter o cadastro após usuários terem preenchido datas exigiria decisão separada: remover o metadado tornaria esses dados invisíveis/inválidos na ficha. Não há rollback automático nem exclusão de dados de lead neste procedimento.
