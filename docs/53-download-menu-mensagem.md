# Download pelo menu da mensagem de atendimento

A ação **Baixar** fica após **Encaminhar** no menu da mensagem de áudio,
vídeo ou documento, recebida ou enviada. O botão de download dentro da bolha
foi removido; reprodução, abertura de documentos e visualizador permanecem.

A ação requer identificação do lead e referência de mídia. Texto, imagem e
mídia explicitamente indisponível não recebem essa ação. Abrir o menu não
baixa arquivo nem consulta Meta ou Uzapi/Autotic.

## Contrato e segurança

O clique reutiliza `GET /api/v1/leads/{leadId}/midias/{mensagemId}/download`,
com JWT pelo cliente HTTP central. Não há endpoint novo nem mudança de
contrato, autorização, RLS, armazenamento ou integração com provedores.
O backend valida acesso ao lead e associação da mensagem, responde 404 sem
bytes para recursos inexistentes/inacessíveis e transmite o storage privado.
Preserva MIME, `Content-Disposition: attachment`, nome seguro e `nosniff`.

O navegador usa o fluxo binário existente: recebe um Blob **somente no clique**,
cria uma URL local temporária e a revoga após iniciar o download. Esse fluxo
ainda mantém o arquivo em memória no navegador; não foi criado outro buffer,
conversão Base64 ou acesso a URLs/tokens do provedor.

O nome retornado pelo backend tem precedência; na ausência, usa o nome da
mídia e o fallback catalogado. Cliques repetidos são bloqueados enquanto a
requisição está ativa. Falha apresenta alerta catalogado dentro da bolha e
permite tentar novamente, sem interromper a conversa.

## Limites

- Chat Interno mantém seu endpoint e suas ações existentes. A capacidade de
  download no componente compartilhado é opcional, fornecida pela bolha externa.
- Arquivos não recebidos pelo storage não são recuperados por esse menu.
  O incidente de JPG/PNG como documento na Uzapi continua separado.
- Testes locais não comprovam homologação em contas reais dos provedores.
- Nenhuma variável nova ou ação no Dokploy é necessária. Sem deploy.
