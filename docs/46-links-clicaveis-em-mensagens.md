# Links clicáveis em mensagens

## Contrato atual do histórico

O histórico de atendimento devolve o texto em `MensagemResposta.conteudo`. Mensagens de mídia
devolvem também `midiaUrl` e `midiaMetadados`. Os tradutores Meta Cloud e UZAPI normalizam o corpo de
texto recebido e os metadados próprios do anexo; hoje não extraem nem persistem um objeto de prévia
de link com destino, título e imagem. A resposta REST e o evento WebSocket também não possuem um
campo específico para essa prévia.

`midiaUrl` identifica o arquivo anexado e pode ser uma URL assinada temporária. Ela não é o destino
de um link citado no texto. O CRM não infere destino a partir do arquivo, nome, legenda ou endereço
da imagem.

## Comportamento do frontend

- Corpos textuais `TEXTO`, `BOTOES` e `LISTA` tornam clicáveis somente URLs explícitas e absolutas
  `http://` ou `https://` que passem pela validação do navegador.
- Esquemas diferentes, credenciais embutidas, URLs inválidas e texto semelhante a domínio sem
  esquema permanecem texto simples.
- O texto é renderizado como nós React; não há interpretação de HTML recebido na mensagem.
- Links abrem nova aba com `noopener noreferrer` e rótulo acessível vindo do catálogo atual.
- Imagens anexadas continuam abrindo o visualizador de mídia. Não são tratadas como prévias de link.

## Limitação de prévia com imagem

Não há hoje uma prévia de link real no contrato de mensagens do CRM. Portanto, não há imagem de
prévia que possa ser ligada ao destino nem um teste ponta a ponta dessa interação. Quando Meta,
UZAPI ou outro canal fornecerem metadado estruturado que associe explicitamente destino, título e
imagem à mensagem, o histórico poderá expor esse metadado e a interface poderá tornar a prévia
clicável. Até lá, a URL textual é o único destino conhecido.

O CRM não busca Open Graph ao receber ou exibir mensagem. Uma busca desse tipo exigiria análise
separada de SSRF, validação de redirecionamentos e endereços privados, limites de resposta, timeout,
cache e isolamento do caminho síncrono de Atendimento.
