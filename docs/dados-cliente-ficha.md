# Dados do cliente na ficha de Atendimentos

## Origem comprovada no codigo

| Campo | Coleta / contrato | Persistencia | Por que faltava na ficha |
|---|---|---|---|
| Nome | Nome sugerido do contato no webhook do canal, cadastro/manual e aplicacao Darwin confirmada; novo `nome` opcional no preenchimento EV05 | `lead.nome` | Ja aparece no topo, editavel conforme permissao; nao e prova de nome civil coletado pelo bot |
| CPF | `POST /internal/v1/ev05/leads/{leadId}/preenchimento`, cadastro/manual, importacao ou Darwin confirmada | `lead.cpf` | Sua exibicao estava acoplada ao componente de consulta Darwin e a permissao de editar |
| Nascimento | `POST /internal/v1/leads/{leadId}/data-nascimento`, ou ficha manual de campos customizados | `lead.dados_customizados.data_nascimento`, metadado `campo_customizado` tipo `DATA` | A ficha de Atendimentos nao renderizava o campo reservado (a Agenda ja possui editor generico) |
| Telefone | Identidade normalizada recebida do canal ou cadastro/edicao manual | `lead.telefone` | Ja aparece; permanece o destino WhatsApp, nunca substituido por divergencia Darwin |

Nao foi consultado banco/workflow produtivo: a existencia de mensagens do bot perguntando esses
dados nao comprova que o workflow chamou os contratos de persistencia. Nao ha extracao do historico.
Darwin medica (`developers.darwin.med.br`) e opcional; ausencia/falha dela nao altera dados locais.
Sua busca por CPF conhecido nao e descoberta de CPF. Nenhum HTTP externo ocorre ao abrir a ficha.

## Apresentacao e capacidade

Flag `dados_cliente_ficha`, OFF em novas e demais instancias (V116). Quando ON: nome e telefone
existentes sao reutilizados, CPF e nascimento aparecem imediatamente abaixo do telefone, com
rotulos e `Nao informado` para ausencia. CPF em mascara e nascimento civil DD/MM/AAAA, sem
conversao de fuso. Nao se duplica nome/telefone em outro bloco. A consulta manual Darwin sai do
fluxo dessa ficha; APIs existentes sao preservadas para compatibilidade. OFF preserva a interface anterior.
O endpoint de ficha continua exigindo `clientes.acessar` e Specification/RLS; a exibicao local nao
exige permissao de editar. A flag e de apresentacao, nao uma nova autorizacao para ler carteiras.

## Automacao: payloads existentes que devem ser conectados

Rede interna apenas; `X-Synapse-Token` configurado e `Idempotency-Key` estavel por comando.
Nunca usar UUID/telefone/CPF deduzidos de outro paciente.

```http
POST /internal/v1/ev05/leads/{leadId}/preenchimento
Content-Type: application/json
X-Synapse-Token: <segredo interno>
Idempotency-Key: <id estavel do comando>

{"cpf":"52998224725","nome":"Pessoa Sintetica"}
```

EV05 exige atendimento humano em andamento e so preenche CPF valido e vazio. Demais campos opcionais
existentes: `email`, `empresa`, `localizacao`. Campo divergente preenchido: `IGNORADO_JA_PREENCHIDO`;
invalido: `IGNORADO_INVALIDO`. `nome` e uma extensao opcional/aditiva: so troca nome vazio ou fallback
exatamente igual ao telefone do proprio lead. Nome valido existente (inclusive nome de perfil)
nao e substituido silenciosamente; retorna `nome: IGNORADO_JA_PREENCHIDO` e permanece corrigivel
manualmente. CPF divergente/invalidado impede aplicar tambem o nome. Nome invalido retorna
`nome: IGNORADO_INVALIDO`. Payload antigo sem `nome` continua funcionando e replays antigos
mantem o hash anterior; resposta acrescenta apenas o campo de resultado `nome`.

```http
POST /internal/v1/leads/{leadId}/data-nascimento
Content-Type: application/json
X-Synapse-Token: <segredo interno>
Idempotency-Key: <outro id estavel do comando>

{"dataNascimento":"1990-05-21"}
```

Grava data ISO na chave reservada; exige metadado DATA cadastrado. Ausente/incompativel: 404;
data invalida: 422; chave reutilizada para outro payload: 409. Valor existente nao e substituido
(`IGNORADO_JA_PREENCHIDO`). A leitura para decidir/escrever agora e protegida por lock de linha,
assim como a edicao manual. Replays nao duplicam alteracoes. Nao ha backfill.

## Atualizacao e seguranca

Alteracoes de nome/telefone/CPF/nascimento produzem somente IDs no sinal `DADOS_CLIENTE_ATUALIZADOS`
do contrato `atendimento.estado.v1`, na outbox e na MESMA transacao da escrita (JPA geral ou JDBC chat).
O worker existente publica no Redis fora da transacao. Entrega WebSocket revalida autorizacao;
o browser invalida somente a ficha do lead e busca novamente pelo REST autorizado. Reconexao
rele a ficha selecionada; chaves por lead impedem mistura na troca de conversa. Sem polling novo.
Nenhum CPF, nascimento ou nome e incluido no sinal. Auditoria de nascimento nao captura valores.
Uma leitura de permissoes pode vincular tambem uma conexao passiva do outro pool ao thread.
Sinal e outbox identificam a conexao transacional ja vinculada (autocommit desligado), sem
abrir outra conexao nem alterar RLS/autocommit. Dois vinculos nao significam duas transacoes;
zero ou duas conexoes de escrita efetivas continuam recusadas. Testes cobrem ambos os pools,
vinculo passivo e atomicidade de commit/rollback com PostgreSQL real.
O preenchimento EV05 tambem registra ator AUTOMACAO, acao e leadId sem capturar valores pessoais.
O sinal cobre atendimentos abertos; consultas historicas continuam lendo o cadastro atual via REST.

Evidencias visuais sinteticas e condicoes de captura: [ficha real](evidencias/dados-cliente-ficha/README.md).

## Operacao (somente autorizada na Femina, nao executada por esta task)

Apos aplicar a imagem com V116, backup e janela operacional adequada, execute o provisionamento
no banco correto com `psql -v ON_ERROR_STOP=1 -v banco_esperado=fmna_db -f docker/provisionamento/habilitar-dados-cliente-ficha.sql`.
O script valida banco/tipo, preserva metadados existentes, cadastra apenas o que falta e habilita
a capacidade; nao altera leads nem importa pacientes. Conferir via GET `/api/v1/config/features`
e GET autorizado da ficha. Nenhuma variavel nova no Dokploy.

Dependencia da automacao: conectar os dois POSTs acima aos valores estruturados ja coletados,
sempre ao leadId correto. Sem essa integracao nao se declara preenchimento automatico produtivo completo.
