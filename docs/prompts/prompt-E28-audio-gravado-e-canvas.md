# Prompt E28 — Gravar áudio no composer e corrigir o canvas

> Leia `AGENTS.md`. Entrega em 25/08.
> Blocos em ordem de gravidade. Commite e faça push por bloco.
> Ao encerrar: `cd backend && ./mvnw clean verify` e o **número da run** do CI.

**Os blocos 1 e 2 são pequenos e independentes. Entregue e publique os dois antes de começar o
bloco 3** — há apresentação ao cliente hoje às 14h e essas duas correções são o que ela precisa.

---

## Bloco 1 — A tela não rola como página, rola por dentro

Hoje o CRM inteiro desce quando se dá scroll, como uma landing page: a sidebar sai da tela e o
cabeçalho da conversa vai embora. O protótipo é um layout de aplicação — colunas fixas, scroll
interno.

A causa está no `src/app/layout.tsx`:

```tsx
<body className="min-h-full flex flex-col bg-background text-foreground font-sans">
```

`min-h-full` permite que o body cresça além da viewport. Quando isso acontece, o
`flex min-h-0 flex-1 overflow-hidden` do `(shell)/layout.tsx` não segura nada, porque o pai já
cresceu, e o `overflow-y-auto` do `main` nunca entra em ação.

O body precisa ficar preso à altura da tela. **Cuidado: o mesmo root layout serve a tela de
login** — ela não pode ficar cortada em viewport baixa; se precisar rolar, o scroll é dela,
interno, nunca do body.

Teste que prova, com Playwright, em viewport de 1280×800:

- Com a lista de Atendimentos mais alta que a tela, a sidebar e o cabeçalho da conversa
  permanecem visíveis após rolar até o fim da lista.
- `document.scrollingElement.scrollTop` continua `0` — a página não rolou; quem rolou foi o
  contêiner interno.
- A tela de login permanece inteiramente acessível em 1280×720.

## Bloco 2 — `caption` não existe em áudio

No `MetaCloudApiAdapter`, a legenda é anexada a qualquer tipo de mídia:

```java
midiaNo.put("id", mediaId);
if (midia.legenda() != null && !midia.legenda().isBlank()) {
    midiaNo.put("caption", midia.legenda());
}
```

A Meta aceita `caption` em `image`, `video` e `document` — **não em `audio`**. Enviar áudio com
qualquer texto junto faz a API recusar a mensagem. É por isso que imagem sai e áudio não.

Decida o que fazer com a legenda de um áudio e **relate a escolha**: descartá-la silenciosamente
esconde informação que o atendente digitou; enviá-la como mensagem de texto separada, logo antes
ou depois do áudio, preserva o que ele quis dizer. A segunda me parece mais honesta, mas se
implicar duas mensagens onde o atendente esperava uma, diga isso no relatório.

Teste que prova: áudio **com** legenda é aceito (nenhum `caption` no corpo de `audio`), e áudio
**sem** legenda continua funcionando. Cubra também que imagem com legenda **mantém** o
`caption` — a correção não pode virar remoção geral.

---

## Bloco 3 — Gravar áudio direto no composer

O composer hoje só anexa arquivo. O atendente precisa gravar na hora.

### O formato decide o bloco

A Meta aceita, para áudio: `audio/aac`, `audio/amr`, `audio/mpeg`, `audio/mp4` e `audio/ogg`
**somente com codec Opus**.

O `MediaRecorder` do Chrome produz `audio/webm;codecs=opus`. O codec é o certo; o **container**
não é aceito. Firefox grava `audio/ogg;codecs=opus` nativamente; Chrome, historicamente, não.

Verifique empiricamente, no navegador que os atendentes usam, antes de escolher caminho:

```js
["audio/ogg;codecs=opus", "audio/mp4", "audio/webm;codecs=opus"]
  .filter((t) => MediaRecorder.isTypeSupported(t))
```

> **Ponto de parada obrigatório.** Se nenhum formato aceito pela Meta estiver disponível na
> gravação, **pare e me avise.** As saídas são reempacotar de webm para ogg (mesmo codec, só o
> container muda) no backend com ffmpeg, ou no navegador com um remuxer. As duas introduzem
> dependência nova em imagem de produção, e essa decisão é minha. **Não adicione ffmpeg ao
> Dockerfile por conta própria.**

### Comportamento

- Botão de microfone no composer, ao lado do clipe. Ícone do `lucide`, rótulo vindo do catálogo
  de textos — **nenhuma string de UI literal no componente**.
- Fluxo: parado → gravando, com timer visível e botão de descartar → **pré-visualização com
  player** → enviar. O áudio **nunca** sai sem confirmação explícita: mensagem enviada por
  engano na conversa de um cliente não tem desfazer.
- Enquanto grava, o campo de texto e o envio de texto ficam desabilitados. Uma coisa por vez.
- Duração máxima vinda de configuração, não fixa no código. Ao atingir o limite, a gravação
  para sozinha e cai na pré-visualização, sem descartar o que já foi gravado.
- Antes de subir, valide o tamanho contra `anexo.tamanho_maximo_audio_mb` de
  `configuracao_automacao` — o limite já é lido por `LimiteDeAnexoRepositorioJdbc`. Estourar o
  limite é erro na tela, não falha no envio.
- Permissão de microfone negada ou revogada: estado explícito e recuperável, sem quebrar o
  composer nem perder o texto já digitado.
- **Se a API não estiver disponível** (`navigator.mediaDevices` ausente, contexto não seguro, ou
  nenhum formato suportado), o botão **não aparece**. Regra do `AGENTS.md`: ou funciona, ou não
  aparece — nada de controle fantasma que falha ao clicar.

### Testes

- Gravação → pré-visualização → envio produz mensagem `AUDIO` com o `media id` da Meta.
- Descartar na pré-visualização não envia nada e não deixa resíduo no storage.
- Permissão negada: botão em estado de erro, composer segue utilizável, texto preservado.
- Limite de duração atingido encerra a gravação preservando o áudio.
- Arquivo acima de `anexo.tamanho_maximo_audio_mb` é recusado antes do upload.
- `MediaRecorder` indisponível: botão ausente, e o resto do composer intacto.

---

## Definição de pronto

- [ ] Body preso à altura da tela; scroll acontece dentro do `main`, não na página
- [ ] Login continua acessível em viewport baixa
- [ ] `caption` deixa de ser enviado em `audio`, e continua em `image` e `document`
- [ ] Destino da legenda de áudio decidido e relatado
- [ ] Botão de gravação com fluxo confirmar-antes-de-enviar
- [ ] Formato de gravação aceito pela Meta — ou ponto de parada acionado
- [ ] Duração máxima por configuração; tamanho validado contra `configuracao_automacao`
- [ ] Botão ausente quando a API ou o formato não existem
- [ ] Testes dos blocos 1, 2 e 3
- [ ] CI verde com **número da run**

## No relatório

Diga qual `mimeType` o `MediaRecorder` entregou no navegador de teste e se ele é aceito pela
Meta sem conversão. Se não for, **não implemente conversão** — relate e pare.

Diga se alguma variável nova precisa entrar no Dokploy, ou afirme que nenhuma precisa.

Blocos 1 e 2 publicados separadamente, antes do 3: informe o SHA de cada um, porque eles vão para
homologação hoje, independentemente do andamento do bloco 3.
