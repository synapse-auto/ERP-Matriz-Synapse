# Provisionamento de instancia

`provisionar-instancia.sql` reconcilia, sem duplicar: um administrador,
etapas, tags e configuracoes da Automacao. Ele nao cria canal, credencial de
WhatsApp ou dados de um cliente especifico.

## Preparar os parametros

Copie `instancias/instancia.exemplo.env.example` para um arquivo local fora do
Git, preencha os valores do filho e carregue-o no terminal atual:

```bash
set -a
source /caminho/seguro/instancia.env
set +a
```

As etapas e tags devem ser confirmadas com a operacao do cliente. O exemplo e
apenas o formato de entrada, nao uma sugestao de funil.

### Gerar o BCrypt sem senha no historico

O comando abaixo pede a senha sem eco, produz BCrypt com custo 12 e guarda
somente o hash na variavel de ambiente do processo atual. A senha e o hash nao
entram no historico nem na linha de comando:

```bash
read -rsp 'Senha inicial do administrador: ' senha; echo
export SYNAPSE_ADMIN_BCRYPT_HASH="$(printf '%s' "$senha" | docker run --rm -i httpd:2.4-alpine htpasswd -niBC 12 administrador | cut -d: -f2)"
unset senha
```

Confirme que a variavel comeca por `$2`; nunca a imprima no terminal ou a
commite. O executor valida o formato BCrypt e envia o hash pelo stdin para o
`psql`, jamais por argumento ou `docker exec -e`.

## Executar e conferir

No VPS, a partir de um checkout do repositorio:

```bash
./docker/provisionamento/executar-provisionamento.sh
```

O executor exige `SYNAPSE_DB_NAME` e `SYNAPSE_DB_USER`, encontra somente a
task Swarm `_postgres.1.` e falha se houver zero ou mais de um candidato. Os
demais valores sao lidos do ambiente. Nenhum segredo e passado por argumento.

Depois, confira sem revelar hash ou tokens:

```bash
container="$(./docker/operacoes/resolver-postgres.sh)"
docker exec "$container" psql -U "$SYNAPSE_DB_USER" -d "$SYNAPSE_DB_NAME" -c \
  "SELECT email, papel, ativo FROM usuario WHERE email = '$SYNAPSE_ADMIN_EMAIL';"
docker exec "$container" psql -U "$SYNAPSE_DB_USER" -d "$SYNAPSE_DB_NAME" -c \
  'SELECT nome, ordem, cor_visual FROM etapa_atendimento ORDER BY ordem;'
docker exec "$container" psql -U "$SYNAPSE_DB_USER" -d "$SYNAPSE_DB_NAME" -c \
  'SELECT chave, valor, valor_min, valor_max FROM configuracao_automacao ORDER BY chave;'
```

Rode o mesmo executor uma segunda vez: as quantidades nao devem crescer.
Este e o teste operacional de idempotencia.

Os limites de midia do exemplo sao 5 MB (imagem), 16 MB (audio) e 100 MB
(documento), os valores atuais do seed. Confirme-os na documentacao atual da
Meta antes do primeiro dado real; o script exige que os tres sejam declarados
na configuracao da instancia.
