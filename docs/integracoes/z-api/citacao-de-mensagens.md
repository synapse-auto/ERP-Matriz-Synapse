# Resposta citada na Z-API

Complemento: [recebimento de referências e respostas interativas](./respostas-interativas.md).
A #330 permanece como base das proteções de saída; o complemento resolve `referenceMessageId`
recebido pela identidade tipada do canal e conserva botão/lista no histórico e no repasse.

## Diagnostico e contrato (26/10/2026)

Relato: recebida 10:36, resposta 10:41 na Femina, erro Z_API_CITACAO_SEM_CORRELACAO.
Sem os IDs/consulta produtiva nao foi confirmada a origem dessa mensagem especifica.
Nao se presume Uzapi pela existencia de historico antigo. O agente nao acessou a VPS.

Causa comprovada no codigo anterior: ZApiAdapter recusava incondicionalmente todo contexto
de citacao. O recebimento ja gravava messageId bruto em mensagem_correlacao_canal, namespace
WHATSAPP, e chave tecnica de deduplicacao em mensagem_id_externo. A saida passava essa
chave tecnica ao adaptador. Na transicao, comparava ainda essa chave com o ID bruto e a
correlacao estava invisivel para usuarios normais pela RLS exclusiva de servico.
Mensagens enviadas com aceite em fila tinham correlacao, mas nao indice legado. Agora o
fluxo le esse alias comprovado quando o indice nao existe, sem backfill nem novas gravacoes.

Fonte oficial: [responder mensagem](https://developer.z-api.io/message/reply-message),
conferida no site e em Z-API/z-api-docs, commit 0b8d420f1f592a7b5e55d28f3e9412ed785880d6.
POST send-text recebe phone, message e messageId original: do receive para recebidas,
do resultado de envio para enviadas. Nao usar zaapId (PROCESSAMENTO), UUID CRM nem hash.
Imagem, video e documento tambem documentam messageId. Audio nao documenta esse campo
na fonte consultada: envio de audio COM citacao retorna409 antes da intencao de envio;
audio sem citacao e resposta textual CITANDO um audio recebido continuam suportados.

## Fluxo entregue

1. Webhook autenticado e duravel -> tradutor -> mensagem e alias bruto no mesmo commit,
   com provedor/instancia/namespace separados. Nao se cria alias para historico legado.
2. Responder continua enviando apenas UUID/timestamp da mensagem CRM autorizada.
3. Ao enfileirar, a referencia Z-API precisa resolver um unico alias WHATSAPP nao conflitante,
   na instancia ativa, ligado ao UUID/horario da origem na mensagem_referencia, para o mesmo
   lead e recursos visiveis. Sem alias:409 antes de persistir
   intencao, sem assumir responsavel ou enviar HTTP. Orientacao para remover a citacao.
4. O compositor revalida no worker, em transacao curta, e entrega identidade comprovada ao
   adaptador. A transacao termina antes do POST. Alteracao/conflito apos o clique e recusado
   sem descartar a citacao ou emitir HTTP. Outros provedores preservam seus caminhos.
5. O adaptador exige identidade compativel e serializa somente seu valor bruto em messageId.
   Outbox/historico conservam as referencias originais; idempotencia/aceite ambiguo continuam
   sem redisparo. Nao reprocessar mensagens ja esgotadas ao aplicar a release.

V114 adiciona somente SELECT de aliases WHATSAPP nao conflitantes de mensagens visiveis,
com RLS de mensagem/atendimento/lead e acesso a clientes. INSERT/UPDATE/DELETE continuam
exclusivos de SERVICO. Sem backfill, nova tabela/coluna, indice ou varredura de dados.
Rollback de politica exige nova migration; nunca editar V112/V114 aplicada. A consulta usa
PK de mensagem_referencia e indice existente da mensagem na correlacao; nao foi medido
plano/latencia com dados produtivos. O teste negativo de RLS usa synapse_app nao superusuario.

## Interface e operacao

Verificacao dirigida: ZApiCitacaoIT28/28 (HTTP e PostgreSQL/MinIO reais, transporte externo
simulado), ZApiAdapterTest47/47 e ZApiMidiaSaidaTest42/42. Inclui recebida/enviada, recarga,
ciclo migrado, participante consentido, ID legado coincidente, instancia/namespace divergentes,
dedup/idempotencia e RLS negativa com synapse_app (leitura de terceiro, acesso-clientes OFF,
INSERT de alias ligado a mensagem propria e UPDATE/DELETE proibidos). Frontend1410/1410;
typecheck/lint/build aprovados, quatro avisos preexistentes fora do diff. Clean verify Java21
e CI do ultimo SHA sao registrados no fechamento da PR, sem antecipar seu resultado.

Codigo tecnico de falha de citacao nao e exibido como mensagem ao usuario; textos do catalogo
orientam remover a referencia e reenviar explicitamente. Defaults do schema preservam catalogos
antigos. Cancelar a resposta mantem texto/anexos; nao ha cancelamento/reenvio automatico.

Para confirmar o caso real, o operador executa [SQL somente leitura](../../diagnosticos/diagnosticar-citacao-z-api.sql)
no banco correto, com janela e atendimento informados. O exemplo13:30-13:45UTC corresponde
a10:30-10:45Brasilia; confirmar fuso antes de usar. Resultado apenas com IDs/horarios/estados,
sem telefone, conteudo, token ou URL. Compartilhar somente em canal privado. Se atingir100
linhas, reduzir janela ou informar atendimento; nao tratar como lista completa.

Homologacao real pendente: no proprio contato, receber mensagem Z-API e responde-la citada;
recarregar e citar uma enviada; repetir num ciclo transicionado. Conferir messageId, retorno e
callback real sem usar pacientes. Testes HTTP simulados com PostgreSQL/MinIO nao comprovam
o comportamento da conta real. Sem deploy/envio real pelo agente. Nenhuma env nova; no Dokploy,
apenas futura release revisada, cujo runner aplica V114 pelo fluxo normal.
