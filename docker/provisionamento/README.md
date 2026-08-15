# Provisionamento de instancia

`provisionar-instancia.sql` reconcilia, sem duplicar: um administrador, o canal
de WhatsApp e sua credencial ativa, etapas, tags, a feature flag da Dashboard e
configuracoes da Automacao. Ele nao cria dados de um cliente especifico.

O canal usa `WHATSAPP_NUMERO` (Phone Number ID) e `WHATSAPP_PROVEDOR`, as mesmas
variaveis do deploy. O token nao e copiado: `canal_credencial.token_ref` guarda
somente `env://WHATSAPP_TOKEN`. Quando o Phone Number ID muda, a credencial
anterior e desativada e preservada para o historico.

Instancias provisionadas antes da E21b mantem o valor antigo da flag ate a
reexecucao do provisionamento. Para habilitar imediatamente, execute uma vez:

```sql
UPDATE feature_flag SET habilitado = TRUE WHERE chave = 'dashboard';
```

## Preparar os parametros

Copie `instancias/instancia.exemplo.env.example` para um arquivo local fora do
Git, preencha os valores do filho e carregue-o no terminal atual:

```bash
set -a
source /caminho/seguro/instancia.env
set +a
```

As etapas e tags devem ser confirmadas com a operacao do cliente. O exemplo e
apenas o formato de entrada, nao uma sugestao de funil. Cada etapa declara
`resultado` como `EM_ANDAMENTO`, `GANHO` ou `PERDIDO`; no maximo uma pode ser
`GANHO`, independentemente do nome escolhido pelo cliente.

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
  'SELECT nome, ordem, cor_visual, resultado FROM etapa_atendimento ORDER BY ordem;'
docker exec "$container" psql -U "$SYNAPSE_DB_USER" -d "$SYNAPSE_DB_NAME" -c \
  'SELECT chave, valor, valor_min, valor_max FROM configuracao_automacao ORDER BY chave;'
docker exec "$container" psql -U "$SYNAPSE_DB_USER" -d "$SYNAPSE_DB_NAME" -c \
  'SELECT c.nome, c.tipo, c.ativo, cc.identificador_externo, cc.token_ref FROM canal c JOIN canal_credencial cc ON cc.canal_id = c.id WHERE cc.ativo;'
```

Rode o mesmo executor uma segunda vez: as quantidades nao devem crescer.
Este e o teste operacional de idempotencia.

Os limites de midia do exemplo sao 5 MB (imagem), 16 MB (audio) e 100 MB
(documento), os valores atuais do seed. Confirme-os na documentacao atual da
Meta antes do primeiro dado real; o script exige que os tres sejam declarados
na configuracao da instancia.

## Seed de demonstracao (E17b)

`seed-demonstracao.sql` e `limpar-demonstracao.sql` sao um par separado de
`provisionar-instancia.sql` — servem para popular um ambiente de
**homologacao ou demonstracao** com leads, atendimentos, mensagens,
lembretes e mensagens programadas, para as telas terem conteudo real para
avaliar. Nao sao dado mockado: sao registros gravados no banco, lidos pelo
caminho normal da aplicacao — a diferenca e a origem, nao o tratamento.

Todo nome e obviamente falso ("Cliente Teste 1", "Obra Exemplo — Asa
Norte"). Os ids usam os prefixos fixos `de`/`da`/`dm`/`db`/`dp`
(leads/atendimentos/mensagens/lembretes/mensagens programadas), exclusivos
deste seed — e o que permite `limpar-demonstracao.sql` remover exatamente o
que foi criado, sem tocar em nada real.

Executar (depois do `R__seed_dev.sql`/seed de dev, do qual dependem os ids
fixos de etapa/usuario/canal/tag):

```bash
container="$(./docker/operacoes/resolver-postgres.sh)"
docker exec -i "$container" psql -U "$SYNAPSE_DB_USER" -d "$SYNAPSE_DB_NAME" \
  < docker/provisionamento/seed-demonstracao.sql
```

**`limpar-demonstracao.sql` e obrigatorio antes do go-live** — rode-o assim
que a homologacao terminar e antes do primeiro lead real entrar:

```bash
docker exec -i "$container" psql -U "$SYNAPSE_DB_USER" -d "$SYNAPSE_DB_NAME" \
  < docker/provisionamento/limpar-demonstracao.sql
```

Idempotente nos dois sentidos: rodar o seed de novo reconcilia (nunca
duplica); rodar a limpeza de novo com o seed ja removido nao falha, so
não encontra linhas para apagar.
