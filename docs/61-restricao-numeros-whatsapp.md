# Restrição de números do WhatsApp

## Uso

O recurso permite limitar, por ambiente/instância, quais telefones individuais podem trocar mensagens com o CRM. Vale para Meta, Uzapi/Autotic, Z-API e Evolution; não depende do nome do cliente e não altera o Chat Interno.

No Dokploy configure:

```text
WHATSAPP_RESTRICAO_NUMEROS_HABILITADA=true
WHATSAPP_NUMEROS_PERMITIDOS=555491082498,556196791022,556198430401,558398779442
```

Os telefones da lista são valores de configuração da instância, não constantes do código. Informe apenas dígitos e separe por vírgula. O default é `false` com lista vazia, preservando o comportamento atual.

## Fail closed e normalização

Com a restrição ligada, lista vazia ou qualquer item inválido bloqueia todos os telefones. A lista inteira precisa ser válida; nenhum item inválido é ignorado silenciosamente. Não há fallback para sufixo, comparação parcial ou liberação automática.

Entradas da lista e números de telefone completos passam pelo `TelefoneCanonico` já usado pelo CRM. Assim, a equivalência brasileira com/sem nono dígito existe somente nos casos previstos e testados por esse normalizador. Identificadores JID, grupos, LID opaco e valores não compostos só por dígitos não são telefones elegíveis. Na Evolution, o tradutor usa o telefone explícito do JID ou `remoteJidAlt`; não extrai dígitos do LID.

## Entrada e saída

- O processador de webhook aplica statuses/correlações antes de filtrar mensagens de remetentes, preservando confirmações das mensagens autorizadas.
- Remetente não permitido vira descarte terminal e rastreável na linha de entrada; o webhook autenticado é consumido, sem retry do fornecedor, lead, atendimento, mensagem ou repasse ao n8n.
- Em envelopes em lote Meta/Uzapi, somente itens de remetentes autorizados e individuais são repassados; itens bloqueados/grupos são removidos. O envelope Meta filtrado é assinado novamente com o mesmo App Secret.
- No Z-API/Evolution, que chegam como callback individual no contrato integrado, resposta interativa/mensagem não autorizada não é repassada.
- O despachante comum verifica o destinatário imediatamente antes de marcar o envio como iniciado. Uma linha antiga já enfileirada para número não permitido termina como falha permanente, com motivo claro, sem construir payload nem chamar provedor. Isso cobre mensagens manuais, automação, campanhas, programadas, retry e encaminhamento.
- Restrição desligada deixa os fluxos existentes inalterados.

## Ativação e rollback

1. Em uma instância de testes, configure as duas variáveis no serviço backend pelo Dokploy e faça o deploy normal da aplicação.
2. Confirme no painel de ambiente apenas os nomes/estado da flag e presença da lista; não copie telefone de paciente para logs.
3. Teste cada telefone de homologação autorizado nos dois sentidos e um telefone controlado fora da lista. Confirme que a entrada bloqueada não cria lead/mensagem nem chega ao n8n; e que o envio bloqueado permanece sem chamada ao provedor.
4. Para restaurar o comportamento anterior, desligue `WHATSAPP_RESTRICAO_NUMEROS_HABILITADA`. Não é necessário alterar banco ou histórico.

Não habilite a restrição com lista vazia se a instância precisa atender tráfego: nesse estado todo tráfego de mensagem é bloqueado intencionalmente. Confirmações de entrega continuam sendo correlacionadas no CRM.
