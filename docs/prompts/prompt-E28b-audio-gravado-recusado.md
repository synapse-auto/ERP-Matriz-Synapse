# Prompt E28b — O áudio gravado é recusado no upload

> Leia `AGENTS.md`. Entrega em 25/08.
> Commite e faça push por bloco.
> Ao encerrar: `cd backend && ./mvnw clean verify` e o **número da run** do CI.

---

## O defeito

A gravação da E28 funciona até o envio. Ao subir, o backend responde **422**:

```
POST /api/v1/atendimentos/{id}/mensagens/midia  →  422
tipo de arquivo nao permitido: video/quicktime
```

Reproduzido em homologação, no navegador do Lucas, com microfone real.

A E28 negociou `audio/mp4` — aceito pela Meta — e a gravação produziu
`audio/mp4;codecs=opus`. **Opus dentro de contêiner MP4 é uma combinação incomum**: o `ftyp`
resultante não é o de um M4A convencional, e a detecção por conteúdo do backend classifica o
arquivo como `video/quicktime`, que não está na allowlist.

Ou seja: o formato foi validado contra a lista da Meta, mas não contra o validador de upload do
próprio CRM. O teste da E28 exercitou gravar, pré-visualizar e descartar — **não exercitou o
upload real**, e foi por isso que passou.

---

## Bloco 1 — Corrigir na origem, não no validador

**Grave AAC dentro de MP4**, que é o M4A convencional: os detectores por conteúdo o reconhecem
como `audio/mp4`, e a Meta o aceita nativamente. Ordem de preferência na negociação:

```js
'audio/mp4;codecs=mp4a.40.2'   // M4A/AAC — preferido
'audio/mp4'                     // deixa o navegador escolher o codec
```

> **Proibido nesta etapa: adicionar `video/quicktime` à allowlist de upload.** Seria trocar um
> defeito de formato por um buraco de segurança — o CRM não trata vídeo, e a lista existe para
> impedir que ele receba um. Se concluir que não há saída pelo lado da gravação, **pare e me
> avise**; a decisão de afrouxar o validador é minha.

Confirme empiricamente, no Chrome, antes de escolher:

```js
['audio/mp4;codecs=mp4a.40.2','audio/mp4','audio/aac']
  .filter((t) => MediaRecorder.isTypeSupported(t))
```

E confirme o outro lado: qual tipo o backend **detecta** para um M4A/AAC real, e se esse tipo
está na allowlist. Não presuma — o defeito atual nasceu exatamente de presumir que o tipo
declarado e o detectado seriam iguais.

> **Ponto de parada.** Se nenhuma variante de MP4/AAC estiver disponível na gravação, pare e
> relate. Não introduza conversão, ffmpeg ou remuxer por conta própria.

## Bloco 2 — Dizer a verdade quando não há microfone

Sem microfone na máquina, `getUserMedia` rejeita com `NotFoundError` e a tela mostra
"Não foi possível gravar o áudio. Tente novamente." — que é falso: tentar de novo nunca vai
funcionar, e o atendente fica clicando achando que o sistema está com defeito.

Distinga os três casos, com textos do catálogo:

| Erro | Significado |
|---|---|
| `NotFoundError` | nenhum microfone disponível no computador |
| `NotAllowedError` | permissão negada — e diga que se libera no cadeado da barra de endereço |
| `NotReadableError` | microfone em uso por outro programa |

Qualquer outro erro mantém a mensagem genérica.

---

## Testes — o que faltou na E28

O buraco não foi de código, foi de cobertura: nenhum teste levou o áudio gravado até o upload.

- **Teste de contrato do endpoint**: um arquivo M4A/AAC real — bytes de verdade, não um stub com
  `content-type` forjado — sobe por `POST /api/v1/atendimentos/{id}/mensagens/midia` e recebe
  `200`. É este teste que o defeito atual teria reprovado.
- **Negativo obrigatório**: um arquivo de vídeo real continua recebendo `422`. A correção não
  pode virar allowlist frouxa.
- **Ponta a ponta no navegador**: gravar, confirmar e enviar, com o upload chegando ao backend.
  Descartar na pré-visualização não basta — foi o que a E28 cobriu.
- Os três estados de erro do Bloco 2, cada um com sua mensagem.

## Definição de pronto

- [ ] Gravação em MP4/AAC, com a negociação verificada no navegador e relatada
- [ ] Nenhum tipo novo na allowlist de upload
- [ ] Tipo detectado pelo backend para M4A/AAC confirmado, não presumido
- [ ] `NotFoundError`, `NotAllowedError` e `NotReadableError` com mensagens distintas
- [ ] Teste de contrato com arquivo real, e o negativo com vídeo
- [ ] Teste de navegador cobrindo gravar → enviar → upload aceito
- [ ] CI verde com **número da run**

## No relatório

Diga qual `mimeType` a gravação passou a produzir e qual tipo o backend detectou para ele — os
dois valores, lado a lado. Foi a diferença entre eles que causou este defeito.

Diga se alguma variável nova precisa entrar no Dokploy, ou afirme que nenhuma precisa.
