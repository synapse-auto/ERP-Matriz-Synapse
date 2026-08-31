# Provisionamento de instancia

`provisionar-instancia.sql` reconcilia, sem duplicar: um administrador, o canal
de WhatsApp e sua credencial ativa, etapas, tags, a feature flag da Dashboard e
configuracoes da Automacao. Ele nao cria dados de um cliente especifico.

O canal usa `WHATSAPP_NUMERO` (Phone Number ID numerico, nao o telefone exibido
nem o WABA ID) e `WHATSAPP_PROVEDOR`, as mesmas variaveis do deploy. O executor
e o SQL recusam valor ausente, vazio ou nao numerico. O token nao e copiado:
`canal_credencial.token_ref` guarda somente `env://WHATSAPP_TOKEN`. Quando o
Phone Number ID muda, a credencial anterior e desativada e preservada para o
historico.

Na homologacao da Estrutural, o valor exigido e `1307417749115229`. A inscricao
do app na Meta e feita por WABA e entrega eventos dos demais numeros da mesma
conta; o backend aceita somente o ID persistido em
`canal_credencial.identificador_externo`. No go-live, reexecute o
provisionamento com o Phone Number ID oficial antes de trocar a inscricao.

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

## Importar leads de um CSV externo

`importar-leads-csv.sh` importa uma carteira para uma unica instancia. O arquivo real e passado por
caminho absoluto, fica fora do repositorio e precisa conter as colunas `nome` e `telefone` em UTF-8;
virgula e ponto e virgula sao aceitos como delimitador. Colunas extras sao ignoradas.

O comando compila e usa diretamente o `TelefoneCanonico` de `crm-core`, com o mesmo
`TELEFONE_DDI_PADRAO` do deploy. Nao existe uma segunda normalizacao em SQL. Linhas com nome ou
telefone vazio, telefone curto, letras, CSV malformado e telefone duplicado no arquivo sao
recusadas individualmente. Telefone segue a mesma regra do dominio: celular brasileiro de oito
digitos ganha o nono; fixo fica como esta. O log informa apenas linha e motivo, nunca o telefone
completo.

Carregue primeiro as variaveis da instancia e execute em homologacao. Simulacao e o modo padrao e
termina com `ROLLBACK`:

```bash
./docker/provisionamento/importar-leads-csv.sh \
  --arquivo /caminho/seguro/fora-do-git/leads.csv \
  --simular
```

O resumo informa linhas validas, ja existentes, inseridas e recusadas. Depois de revisar as
contagens em homologacao, repita a simulacao em producao e mostre o resultado ao responsavel pela
operacao. Nada deve ser aplicado antes da autorizacao dele.

O modo real exige um caminho novo para backup e cria o `pg_dump` antes de abrir a transacao de
importacao:

```bash
./docker/provisionamento/importar-leads-csv.sh \
  --arquivo /caminho/seguro/fora-do-git/leads.csv \
  --aplicar \
  --backup /caminho/seguro/backups/antes-importacao-$(date +%Y%m%d-%H%M%S).dump
```

O insert usa `ON CONFLICT (telefone) ... DO NOTHING`: um lead existente preserva nome, status,
dono, historico e contadores. Leads novos nascem em `IA`, sem dono e sem atendimento. Por isso eles
aparecem na Agenda para todos os papeis conforme a RLS vigente, mas nao criam cartao em Atendimentos.
Rodar o mesmo arquivo novamente insere zero linhas.

Os limites de midia do exemplo sao 5 MB (imagem), 16 MB (audio) e 100 MB
(documento), os valores atuais do seed. Confirme-os na documentacao atual da
Meta antes do primeiro dado real; o script exige que os tres sejam declarados
na configuracao da instancia.

## Corrigir so o funil (`funil-padrao.sql`)

O funil e montado por `SYNAPSE_ETAPAS_JSON` dentro do provisionamento. Ate
23/08 o arquivo de exemplo entregava tres etapas, enquanto o seed exige no
minimo cinco — uma instancia provisionada pelo exemplo nascia incapaz de
receber o seed, e o erro so aparecia la na frente:

```
ERROR:  funil incompleto; provisione pelo menos cinco etapas antes do seed
```

O exemplo foi corrigido. Para uma instancia **ja provisionada** com o funil
curto, `funil-padrao.sql` aplica exatamente o mesmo bloco de etapas do
provisionamento, sem tocar em canal, credencial, tags ou configuracao:

```bash
docker exec -i "$container" psql -U "$SYNAPSE_DB_USER" -d "$SYNAPSE_DB_NAME" \
  < docker/provisionamento/funil-padrao.sql
```

`ON CONFLICT (ordem) DO UPDATE` **renomeia** as etapas que ja ocupam as
ordens 1..6: um lead que estava na ordem 2 passa a exibir o nome novo da
ordem 2. Em homologacao isso e o desejado. Contra dados de cliente real,
confira antes qual nome ocupa cada ordem.

## Seed de demonstracao (E17b)

`seed-demonstracao.sql` e `limpar-demonstracao.sql` sao um par separado de
`provisionar-instancia.sql` — servem para popular um ambiente de
**homologacao ou demonstracao** com quatro atendentes, leads, atendimentos,
mensagens, lembretes e mensagens programadas, para as telas terem conteudo
real para avaliar. Nao sao dado mockado: sao registros gravados no banco,
lidos pelo caminho normal da aplicacao — a diferenca e a origem, nao o
tratamento.

Todo nome e obviamente falso ("Cliente Teste 1", "Obra Exemplo - Asa
Norte", "Zelia Demonstracao"). Os ids usam prefixos UUID hexadecimais fixos
e exclusivos (`de`, `da`, `d1`, `db`, `d3`, `d4`, `d5`, `d6`), permitindo
que `limpar-demonstracao.sql` remova exatamente o que foi criado, sem tocar
em nada real. A senha publica das contas de atendente e `atendente123`.

Execute depois do provisionamento. O script resolve o canal, a credencial e
as etapas existentes por seus dados, sem depender do `R__seed_dev.sql` nem
dos UUIDs de desenvolvimento:

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

## Simular a fusao do nono digito (E111, antes do deploy da V50)

A V50 **funde e apaga leads**. Antes de autorizar o deploy que a leva, rode a
simulacao: ela mostra exatamente o que a migration faria com os dados desta
instancia, sem gravar nada (tudo dentro de `BEGIN ... ROLLBACK`).

```bash
docker exec -i "$container" psql -U "$SYNAPSE_DB_USER" -d "$SYNAPSE_DB_NAME" \
  -v ddi="${TELEFONE_DDI_PADRAO:-55}" \
  < docker/provisionamento/simular-fusao-nono-digito.sql
```

O que conferir na saida, nesta ordem:

1. **Secao 1** tem de estar toda vazia. Qualquer linha ali derruba o deploy, de
   proposito: a migration aborta em vez de adivinhar.
2. **Secao 2** lista os pares e quem sobrevive. Sobrevive quem tem a conversa —
   confira se o dono resultante e quem de fato vem atendendo.
3. **Secao 3** e o **unico** registro do que sera descartado: o nome do lead
   apagado e todo campo preenchido nos dois lados. **Guarde a saida**; depois do
   deploy ela nao existe mais em lugar nenhum.
4. **Secao 4b** lista, por par, o atendimento que fica aberto e os que serao
   finalizados (id, atendente, mensagens, `iniciado_em`). E a lista que a gestao
   aprova junto com os nomes. Nada e apagado: o historico do atendimento fechado
   continua no cliente.

A saida tambem serve de lista de trabalho para a operacao: o nome nao e fundido
de proposito, entao os pares da secao 3 com `nome_do_campo = nome` sao os leads
cujo nome pode precisar de ajuste pela tela depois do deploy.
