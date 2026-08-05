# Restauracao verificada

Um backup so esta pronto quando este procedimento tiver sido executado em um
banco novo e a aplicacao tiver subido sobre ele. Nunca restaure sobre
`SYNAPSE_DB_NAME`: o script recusa esse destino e tambem recusa banco ja
existente.

## Pre-requisitos

- Pare qualquer Stack temporaria que use o banco de destino.
- Tenha as mesmas variaveis S3 do `README.md`, incluindo
  `BACKUP_RCLONE_IMAGE` com tag imutavel, no ambiente seguro do Dokploy.
- Escolha a chave do objeto, por exemplo
  `synapse-crm/cliente-hml/cliente-hml-20260805T120000Z.dump`.
- Escolha um banco vazio, por exemplo `synapse_restauracao_20260805`.

O dump custom inclui todas as tabelas filhas da tabela particionada `mensagem`.
O procedimento tambem recria a janela corrente de particoes e falha se a
particao de seguranca `mensagem_default` tiver linhas.

## Restaurar em banco novo

Exporte apenas valores nao secretos no terminal; as credenciais S3 devem vir
do ambiente seguro do job. Em seguida, defina o objeto e o banco de destino:

```bash
export BACKUP_OBJECT_KEY='synapse-crm/cliente-hml/cliente-hml-AAAAmmddTHHMMSSZ.dump'
export RESTAURAR_DB_NOME='synapse_restauracao_aaaammdd'
./docker/backup/restaurar-backup.sh
```

O script executa esta ordem, que e obrigatoria:

1. baixa o dump e valida que ele nao esta vazio;
2. cria a role global `synapse_app` se faltar e concede-a ao usuario da
   aplicacao; roles globais nao fazem parte de `pg_dump`;
3. cria um banco novo e roda `pg_restore --no-owner --exit-on-error`;
4. reaplica os grants de `synapse_app`, confirma as particoes de `mensagem` e
   garante a janela atual mais tres meses a frente;
5. falha se `mensagem_default` tiver qualquer linha, pois isso impediria a
   criacao segura de uma particao mensal futura.

Se o `pg_restore` falhar, o banco parcial e preservado para diagnostico. Nao o
aponte para a aplicacao; descarte-o somente depois de identificar o erro.

## Confirmar antes de subir a aplicacao

No container Postgres resolvido pelo script, execute:

```sql
SELECT rolname FROM pg_roles WHERE rolname = 'synapse_app';
SELECT has_table_privilege('synapse_app', 'lead', 'select') AS pode_ler_lead;
SELECT inhrelid::regclass AS particao
  FROM pg_inherits
 WHERE inhparent = 'mensagem'::regclass
 ORDER BY 1;
SELECT mensagens_na_particao_default() AS linhas_na_particao_de_seguranca;
SELECT version, success
  FROM flyway_schema_history
 ORDER BY installed_rank DESC
 LIMIT 20;
```

Esperado: role presente, `pode_ler_lead = true`, particoes mensais existentes,
zero linhas na particao de seguranca e todas as migrations bem-sucedidas.

## Teste ponta a ponta

1. Crie uma Stack temporaria no Dokploy usando as mesmas imagens e variaveis da
   homologacao, trocando somente `POSTGRES_DB` para `RESTAURAR_DB_NOME`.
2. Suba backend e frontend nessa Stack temporaria e espere `200` em
   `/health/liveness` e `/health/readiness`.
3. Autentique com um usuario restaurado e abra um atendimento com mensagens
   antigas. Isso prova simultaneamente schema, dados, particoes e permissao da
   role da aplicacao.
4. Anote o horario de inicio e fim no log do Dokploy; essa diferenca e o tempo
   real de restauracao para o runbook do proximo filho.
5. Depois da aprovacao, derrube a Stack temporaria e remova explicitamente o
   banco de teste. Nunca remova o banco da homologacao por prefixo ou wildcard.

Sem o passo 2 e o passo 3, a restauracao e somente uma verificacao de SQL, nao
uma recuperacao comprovada.
