# Pendência pós-E88 — Download/visualização de mídia retorna 401

## Observação

Na seção **Mídias e documentos** da ficha lateral do lead, clicar na ação de download/visualização retorna uma página do navegador com **HTTP 401**.

Evidência: captura de 29/08/2026, após clicar em um item listado na seção (documento, imagem ou áudio).

## Hipótese a investigar depois

O endpoint criado para mídia exige Bearer token, mas a ação atual provavelmente navega por `href`/nova aba. Navegações nativas não propagam o token que o cliente HTTP do CRM coloca nas requisições autenticadas.

## Critérios para a futura correção

- Confirmar a URL e a resposta exatas no DevTools, sem logar token nem URL assinada.
- Manter autorização no backend por lead/atendimento/mensagem; não transformar o arquivo em público e não aceitar ID arbitrário.
- Fazer download e visualização funcionarem para imagem, áudio e documento no browser autenticado.
- Preservar RLS: atendente A continua sem acessar mídia do lead de B.
- Cobrir 401/403/404 e o fluxo real de clique no navegador, além de testes de API.
- Não reutilizar `window.location`/`<a href>` diretamente para endpoint que depende de Authorization, a menos que exista mecanismo seguro de sessão/cookie especificamente validado.
