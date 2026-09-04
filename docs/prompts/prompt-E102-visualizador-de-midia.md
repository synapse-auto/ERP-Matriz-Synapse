# Prompt E102 — Visualizador de mídia dentro da aba

> Leia `AGENTS.md` e `CLAUDE.md`. Branch própria (`feat/visualizador-de-midia`) e PR.
> **Sem merge, sem deploy.** Só `frontend/` se a E101 tiver deixado o acesso aos bytes resolvido —
> confira; se precisar tocar o backend, suba o degrau de verificação e diga por quê.
>
> **Depende da E101 (`fix/midias-401`), que precisa estar mergeada em `main` antes.** Faça a branch
> a partir de `main` já com ela dentro. Se não estiver, **pare e avise** — construir visualizador
> sobre URL que devolve 401 é trabalho perdido.

---

## O pedido

Três coisas, todas sobre "ver sem sair da tela":

1. Abrir imagem, vídeo e documento **dentro da própria aba**, não numa aba nova do navegador.
2. O mesmo vale para os itens de **Mídias e documentos** na ficha do lead.
3. Clicar na **foto do lead** na ficha abre a foto ampliada.

## Bloco 1 — Um visualizador, não três

Hoje a imagem no painel abre com `<a target="_blank">` e o resto não abre de jeito nenhum. O
resultado esperado é um **overlay único** — o mesmo componente servindo os três pontos de entrada
(bolha do chat, painel de mídias, foto do lead). Se você escrever dois, eles vão divergir; foi assim
que a foto do lead e o painel de mídias acabaram com dois mecanismos de URL diferentes, que a E101
teve que unificar.

Comportamento do overlay:

- **Imagem** — ampliada, cabendo na tela, sem estourar o viewport.
- **Vídeo** — `<video controls>`, com a URL assinada da E101 (é o motivo daquela decisão: `<video>`
  precisa de Range, e blob em memória não dá seek).

> ### A URL é emitida ao ABRIR o overlay. Sempre.
>
> Isto não é detalhe de implementação, é requisito. Para **todo** ponto de entrada — inclusive a
> bolha do chat — o overlay pede uma URL nova em `GET /api/v1/leads/{leadId}/midias/{mensagemId}/url`
> no momento em que abre. **Não reaproveite** o `mensagem.midiaUrl` que veio na listagem de
> mensagens.
>
> O motivo: aquela URL é assinada quando a conversa carrega e vale **5 minutos**
> (`synapse.midia.expiracao-leitura`). Um atendente deixa a conversa aberta a manhã inteira. Se o
> overlay reusar a URL da listagem, abrir um anexo numa conversa que está aberta há dez minutos
> mostra tela vazia — e o bug parece do visualizador, quando é da assinatura vencida.
>
> Teste obrigatório: com a URL da listagem **expirada**, abrir a mídia pela bolha funciona, porque
> o overlay emitiu uma nova. Sem esse teste a etapa não está pronta.
- **PDF** — embutido. Se o navegador não conseguir, **caia para o download** com uma mensagem clara;
  não deixe um retângulo cinza sem explicação.
- **Outros documentos** (docx, xlsx e afins) — não tente renderizar. Mostre nome, tipo, tamanho e o
  botão de baixar. Fingir que abre é pior que dizer que não abre.
- **Áudio** — já existe `player-audio.tsx`. Reaproveite; não escreva um segundo player.

## Bloco 2 — O básico que costuma faltar

- Fecha com `Esc` e com clique fora.
- O foco vai para o overlay ao abrir e **volta para o elemento que o abriu** ao fechar.
- O overlay tem `role="dialog"`, rótulo acessível, e a página atrás não rola enquanto ele está
  aberto.
- Botão de baixar dentro do overlay, usando o **mesmo caminho** que a E101 padronizou.
- Nome do arquivo e data visíveis — quem abre uma mídia velha precisa saber de quando ela é.

O projeto já usa `Dialog` (Base UI) em vários lugares — `dialogo-transferir`, `dialogo-avaliacao`,
`NovidadesDialog`. **Use o mesmo**, com as convenções que já existem: `data-active:`, nunca
`data-[state=active]:`. Não instale biblioteca de lightbox.

## Bloco 3 — Navegar entre as mídias

No painel de **Mídias e documentos** o overlay deve permitir ir para a anterior e a próxima, com as
setas do teclado, sem fechar e reabrir. É a diferença entre revisar onze anexos e desistir no
terceiro.

Na bolha do chat isso não se aplica: abre aquele arquivo e pronto.

## Bloco 4 — A foto do lead

Na ficha, a foto do lead vira clicável e abre no mesmo overlay. Dois cuidados:

- **Lead sem foto não abre nada.** Hoje o avatar cai nas iniciais coloridas; iniciais não são mídia,
  e um overlay com um quadrado colorido ampliado é constrangedor. Sem foto, nem cursor de clique.
- A foto do lead vem por **caminho autenticado** (`/api/v1/leads/{id}/foto`, E97), não por URL
  assinada — o `AvatarIniciais` já busca com `apiFetchBlob`. O overlay precisa aceitar as duas
  origens sem que cada chamador saiba qual é. Resolva isso **dentro** do visualizador, com a mesma
  regra única que o `avatar-iniciais.tsx` já tem (caminho relativo → autenticado; URL absoluta →
  `urlSegura`). Não espalhe o `if`.

## Bloco 5 — O que não pode acontecer

- Nenhum `src`/`href` novo apontando para `/api/` protegido. A E101 acabou de tirar todos; não
  reintroduza.
- Nada de `target="_blank"` para mídia. É literalmente o que o pedido está removendo.
- Sem `localStorage` para estado do overlay.
- Sem biblioteca nova. Se você achar que precisa de uma, **pare e explique** em vez de instalar.

## Bloco 6 — Testes

- Abre e fecha por `Esc`, por clique fora e pelo botão; o foco volta para quem abriu.
- Imagem, vídeo, PDF e documento não renderizável: cada um cai no ramo certo, e o documento mostra o
  botão de baixar em vez de um vazio.
- Setas navegam entre as mídias do painel e param nas pontas sem quebrar.
- Foto do lead: com foto, abre; **sem foto, não abre e não vira clicável** — este é o teste que
  costuma faltar.
- Nenhum teste existente de mídia precisou ser editado. Se precisou, diga qual e por quê.

## Verificação

```
npm run lint && npm run typecheck && npm run test && npm run build   # em frontend/
```

Se tocou backend, `./mvnw verify` no reator também, e justifique no relatório.

## Relatório

1. Onde ficou o componente único e quais três pontos de entrada o usam.
2. Como o overlay decide entre URL assinada e caminho autenticado, e onde essa decisão mora.
3. O que acontece com PDF quando o navegador não embute.
4. Confirmação de que nenhum `src`/`href` novo aponta para `/api/`.
5. Qualquer coisa que você quis instalar e não instalou.
