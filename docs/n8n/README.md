# Workflows n8n versionados

`resumo-ia-sob-demanda.json` é um template de importação do fluxo iniciado pelo CRM.
Ele não contém credenciais, IDs de Data Table, token ou URL de provedor.

Antes de ativar:

1. Crie uma Data Table persistida no banco interno do n8n com chave `solicitacaoId` e as colunas
   `leadId`, `atendimentoId`, `status`, `solicitadoEm`, `atualizadoEm`, `erroCodigo` e `erroMensagem`.
2. Substitua `CONFIGURE_DATA_TABLE_ID` pelo ID dessa tabela. O template consulta a chave antes de
   inseri-la: uma chave existente para o mesmo par lead/atendimento responde `200` e não executa IA;
   a mesma chave com outro par responde `409`. Mantenha a tabela persistida e a execução do webhook
   em modo de fila para que a reserva seja serializada pelo n8n.
3. Garanta no ambiente do n8n `SYNAPSE_API_URL`, `SYNAPSE_TOKEN_INTERNO`, `AUTOMACAO_TOKEN` e
   `RESUMO_IA_PROVEDOR_URL`. O último é o endpoint do provedor escolhido pela operação e não é
   enviado ao CRM.
4. O template já valida o token, UUIDs e evento e contém as ramificações `202` (nova), `200`
   (replay) e `409` (mesma chave com dados incompatíveis). Não remova a consulta/classificação antes
   da inserção: é ela que impede gerar outro resumo para uma reentrega.
5. Configure retry somente em rede/5xx, com backoff limitado. Não habilite retry automático para
   400, 401, 403, 404, 409 ou 422.

O nó de IA deve receber apenas o contexto limitado retornado pelo CRM. Não grave histórico completo,
telefone, token, URL temporária ou resposta bruta nos logs. O CRM rejeita resultado tardio e preserva
o resumo anterior.
