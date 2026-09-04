# E120 — colar print direto na conversa

## O pedido

Card: *"Envio de imagem e print da área de transferência na conversa"*. A operação tira print de um
orçamento e quer colar no chat com Ctrl+V, como faz no WhatsApp Web.

Hoje não funciona: não existe nenhum `onPaste` no `composer.tsx` nem na `zona-soltar-arquivos.tsx`.
Arrastar arquivo funciona; colar não. Nunca foi implementado.

## O que fazer

Acrescente o tratamento de colagem no composer, **reutilizando o caminho de anexo que já existe** —
o mesmo que a zona de arrastar usa. Não crie um segundo fluxo de upload, não duplique validação de
tipo nem de tamanho: a colagem é só mais uma forma de escolher o arquivo.

Pontos que decidem se fica bom:

- **Imagem colada não tem nome.** O `File` que vem do clipboard costuma vir sem nome ou como
  `image.png`. Gere um nome legível e com a extensão certa para o mimetype real — o cliente vê esse
  nome quando o arquivo chega como documento.
- **Só intercepte quando houver arquivo no clipboard.** Colar texto tem de continuar colando texto no
  campo de mensagem, sem efeito colateral. Este é o jeito mais fácil de quebrar o composer.
- **Mostre a prévia antes de enviar**, igual ao arrastar. Colar e disparar direto é irrecuperável: a
  pessoa cola o print errado e ele já foi para o cliente.
- **Respeite os limites que já existem** — tipo permitido e tamanho máximo vêm da configuração da
  instância, não do componente.
- **Vários arquivos**, se o suporte a múltiplos anexos já existir no composer.
- **Celular não tem colar.** A mudança não pode quebrar nem poluir o composer no mobile.

## Testes

1. Colar imagem adiciona anexo com prévia, sem enviar sozinho.
2. Colar texto continua inserindo texto, sem criar anexo.
3. Arquivo acima do limite é recusado com a mesma mensagem do arrastar.
4. Tipo não permitido é recusado com a mesma mensagem do arrastar.
5. O nome gerado tem a extensão correspondente ao mimetype.
6. O composer no mobile continua íntegro.

## Escopo

Frontend apenas. Nenhuma mudança de backend, contrato ou configuração. Se descobrir que precisa de
algo do backend, **relate em vez de mudar** — outra etapa está no caminho de mídia.

## Entrega

Branch própria, Conventional Commit, push, PR contra `main`. Relatório nos sete itens do `AGENTS.md`.
