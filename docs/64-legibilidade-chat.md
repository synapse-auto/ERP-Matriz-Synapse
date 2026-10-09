# Legibilidade do chat e da inbox

## Alcance

`--chat-texto-forte` deriva de `--texto-forte` no tema claro e de `--foreground`
no tema escuro. O alias Tailwind `text-chat-texto-forte` é usado apenas no corpo
recebido, nos nomes da inbox e nos cabeçalhos do atendimento e Chat Interno.
Não modifica a família tipográfica, o `foreground` global ou outras telas.
Temas existentes não precisam adicionar campos ao JSON.

Os nomes usam semibold; o corpo continua normal. Prévias usam `foreground` opaco.
Nomes de leads sem atendimento aberto mantêm a distinção `muted-foreground`.
Horários e autoria enviados não reduzem a opacidade do texto sobre a cor primária.
O player compartilhado mantém sua cor herdada, sem diminuir a opacidade do tempo.
Badges de não lidas, seleção, metadados secundários e status de entrega continuam distintos.

## Contraste e limites da evidência

Na paleta padrão, cálculo WCAG em sRGB:

| Conteúdo | Antes | Depois |
| --- | ---: | ---: |
| Corpo recebido sobre fundo sutil | 8,58:1 | 14,15:1 |
| Horário enviado sobre primária | abaixo de 4,5:1 (texto a 70%) | 4,53:1 |
| Corpo enviado sobre primária | 4,53:1 | 4,53:1 |

Esses valores são cálculos dos tokens, **não comprovação do contraste efetivo da tela**.
Para validar a renderização, abrir a rota real `/atendimentos` com dados sintéticos,
aguardar `document.fonts.ready`, confirmar `font-family` computado e fonte carregada,
e comparar antes/depois com a mesma viewport, zoom, tema e conversa.
Compor transparências e fundos selecionados ao medir; repetir no tema alternativo e escuro.
Uma paleta personalizada pode exigir calibragem de seus próprios tokens.

A primeira captura isolada foi descartada: não carregava a variável da Inter fornecida
pelo `next/font` e caiu em serif. Não representa o produto nem serve como aprovação visual.
A fonte do produto permanece a Inter carregada pelo layout original.

Sem alterações de backend, APIs, permissões, consultas, polling ou processamento por mensagem.
Nenhuma variável nova ou ação no Dokploy. Não realizar deploy nesta etapa.
