# Backup horario

`executar-backup.sh` faz um `pg_dump` no formato custom e comprimido, grava o
tamanho no log, envia para um bucket S3 compativel e somente considera o
backup valido depois de conferir o tamanho remoto. A retencao minima e de sete
dias; o expurgo tambem encerra o job com erro se falhar.

O script deve rodar em um **Dokploy Server Job** (nao Compose Job), porque ele
precisa acessar o socket Docker para localizar a unica task Swarm `postgres.1`.
O resolvedor comum falha se encontrar zero ou mais de um container compativel;
ele nunca usa `docker ps -qf name=postgres`.

## Variaveis do job

Configure estas variaveis no ambiente seguro do job/Dokploy, nunca no comando
nem em arquivo versionado:

| Variavel | Uso |
| --- | --- |
| `SYNAPSE_DB_NAME` | Banco da instancia. |
| `SYNAPSE_DB_USER` | Usuario que executa o `pg_dump` pelo socket local. |
| `BACKUP_INSTANCIA_CODIGO` | Identificador neutro da instancia, usado no prefixo S3. |
| `BACKUP_S3_ENDPOINT` | Endpoint HTTPS do provedor S3 compativel. |
| `BACKUP_S3_BUCKET` | Bucket externo dedicado aos backups. |
| `BACKUP_S3_REGION` | Regiao exigida pelo provedor. |
| `AWS_ACCESS_KEY_ID` | Credencial S3, secreta. |
| `AWS_SECRET_ACCESS_KEY` | Credencial S3, secreta. |
| `BACKUP_RCLONE_IMAGE` | Imagem rclone com tag imutavel, por exemplo `rclone/rclone:1.x.y`; `latest` e recusada. |
| `BACKUP_RETENCAO_DIAS` | Opcional; minimo `7`, padrao `7`. |
| `BACKUP_S3_PREFIX` | Opcional; padrao `synapse-crm`. |

As credenciais nunca sao interpoladas na linha de comando: o processo rclone
recebe apenas os nomes das variaveis de ambiente. Use uma chave S3 exclusiva
para backup, limitada ao bucket/prefixo e sem permissao de leitura/escrita nos
objetos de midia do CRM.

## Execucao manual

No checkout operacional do repositorio no VPS, com as variaveis acima ja
exportadas pelo ambiente seguro:

```bash
./docker/backup/executar-backup.sh
```

O sucesso termina com `upload confirmado` seguido de `retencao aplicada`.
Qualquer outro fim deve ser tratado como backup inexistente e investigado nos
logs do job.

## Dokploy Schedules

1. Disponibilize o checkout versionado em um caminho legivel pelo Server Job,
   por exemplo `/opt/synapse-crm`, e atualize-o por SHA revisado antes de mudar
   o job. Nao use `curl | bash` para executar a branch `main` sem revisao.
2. No Dokploy, crie um **Server Job** no servidor que e manager do Swarm.
   Configure as variaveis acima no ambiente protegido da instancia/job.
3. Use `0 * * * *`, timezone `America/Sao_Paulo`, shell `bash` e comando:

   ```bash
   /opt/synapse-crm/docker/backup/executar-backup.sh
   ```

4. Rode **Run manually** uma vez e confira no log o tamanho do dump, o
   `upload confirmado` e a retencao. So entao habilite a recorrencia horaria.

O Dokploy documenta que Server Jobs executam no host e que Dokploy Server Jobs
possuem socket Docker; este roteiro usa o primeiro para que o checkout e as
variaveis do ambiente tenham um local controlado. Consulte tambem
`RESTAURAR.md` e teste a recuperacao antes de existir dado real.
