# Prompt E132 — Vídeo e figurinha recebidos do WhatsApp

> Leia `AGENTS.md`, `CLAUDE.md` e `docs/13-estado-do-projeto.md`.
> Branch própria (`fix/video-e-figurinha-na-entrada`) e PR. **Sem merge, sem deploy.**
> Backend + frontend. **Uma migration nova** (use o próximo número livre na `main`).
> `./mvnw -pl crm-app -am verify` na raiz de `backend/` e a suíte do `frontend/`.

**Bug em produção, com cliente reclamando.** Vídeo e figurinha que o cliente manda **nunca chegam
na conversa** — nem como erro, nem como anexo quebrado. Somem.

---

## A causa, já isolada — não reinvestigue

`MetaCloudWebhookTradutor.java:181`:

```java
private static final Map<String, String> TIPO_META_PARA_CRM =
        Map.of("image", "IMAGEM", "audio", "AUDIO", "document", "DOCUMENTO");
```

e logo abaixo:

```java
String tipoCrm = TIPO_META_PARA_CRM.get(tipoMeta);
if (tipoCrm == null) {
    // Nem texto, nem midia suportada (status, reacao, sticker, etc.). Ignorar
    // somente este item preserva as mensagens boas que vierem no mesmo POST.
    continue;
}
```

`video` e `sticker` não estão no mapa. O `continue` descarta em silêncio, **antes** de gravar
qualquer coisa. Não é falha de download, de MinIO nem de tamanho.

Confirmado em produção: o `webhook_entrada` de 02/09 tem `video` às 10:59:24 e três `sticker` às
10:54:13, e nenhum virou mensagem. As `image` do mesmo período chegaram todas.

E não é só o tradutor — **`VIDEO` não existe em lugar nenhum**:

| Onde | Situação |
| --- | --- |
| `TipoMensagem.java` | `TEXTO, AUDIO, IMAGEM, DOCUMENTO, BOTOES, LISTA, SISTEMA` — sem `VIDEO` |
| ENUM `tipo_mensagem` no banco | idem (V1 + V30 + V54) |
| `TipoMensagem.exigeMidia()` | só `AUDIO`, `IMAGEM`, `DOCUMENTO` |
| `MetaCloudApiAdapter` (envio) | `switch` sem `case VIDEO` |
| `types.ts:12` e `bolha-mensagem.tsx` | sem `VIDEO`, sem player |

Vídeo nunca foi suportado. Não regrediu — nunca existiu.

---

## As duas decisões, já tomadas

**Vídeo vira tipo próprio `VIDEO`.** É mídia com player, não anexo genérico. Mapear para
`DOCUMENTO` faria o cliente mandar um vídeo e o atendente receber "arquivo para baixar" — pior que
o bug, porque parece funcionar.

**Figurinha vira `IMAGEM`.** Figurinha do WhatsApp é WebP animado ou estático; a tag `<img>` já
renderiza os dois, e o balão de imagem já existe. **Não** crie tipo `STICKER`: seria um tipo novo
atravessando banco, domínio, contrato e tela para render idêntico ao de imagem.

Consequência aceita: a figurinha aparece como imagem no histórico, sem se identificar como
figurinha. É o certo para esta etapa — quem olha a conversa quer ver o que o cliente mandou.

---

## Bloco 1 — Migration

`ALTER TYPE tipo_mensagem ADD VALUE IF NOT EXISTS 'VIDEO';` — no padrão da V30 e da V54.

**Armadilha do Postgres, e ela derruba o deploy:** um valor acrescentado com `ALTER TYPE ADD VALUE`
**não pode ser usado na mesma transação** que o adicionou. O Flyway roda cada migration numa
transação. Então esta migration **contém só o `ALTER TYPE`** — nenhum `INSERT`, nenhum `UPDATE`,
nenhum `WHERE tipo = 'VIDEO'`, nenhuma função que referencie o valor novo. Se precisar de qualquer
coisa que use `'VIDEO'`, vai em migration seguinte.

Confirme no relatório que a migration tem uma instrução só, e qual número ela recebeu.

---

## Bloco 2 — Backend

- `TipoMensagem`: acrescente `VIDEO`, e inclua em `exigeMidia()` — vídeo carrega arquivo e precisa
  de `midiaUrl` como os outros três.
- `MetaCloudWebhookTradutor`: `"video" -> "VIDEO"` e `"sticker" -> "IMAGEM"` no `TIPO_META_PARA_CRM`.
  O corpo do `sticker` na Meta tem a mesma forma dos outros (`id`, `mime_type`, `sha256`) — confirme
  lendo o payload real que está em `webhook_entrada` e diga no relatório.
- Atualize o comentário do `continue`: ele lista `sticker` como exemplo de descarte e vai passar a
  mentir.
- `MetaCloudApiAdapter`: o `switch` sobre `TipoMensagem` é exaustivo, então **o compilador vai te
  obrigar** a tratar `VIDEO`. Mapeie para `"video"`. A Meta aceita `caption` em `image`, `video` e
  `document` — o código já sabe disso na linha 254; inclua `VIDEO` na mesma condição em vez de criar
  outra.
- **Não** mexa no download de mídia, no MinIO, na URL assinada, nem na E88/E101. O caminho de mídia
  funciona — imagem e áudio chegam por ele todo dia.

Sobre limite de tamanho: a Meta entrega vídeo de até 16 MB. **Verifique** se existe algum limite no
caminho de download e diga no relatório qual é. Se não existir, não invente um nesta etapa.

---

## Bloco 3 — Frontend

- `types.ts:12`: `TipoMensagem` ganha `"VIDEO"`.
- `bolha-mensagem.tsx`: um ramo `mensagem.tipo === "VIDEO"` com `<video controls preload="metadata">`
  — **não** dê autoplay, e **não** carregue o arquivo inteiro ao abrir a conversa: um histórico com
  dez vídeos não pode baixar dez arquivos.
- A linha 270 (`if (mensagem.tipo !== "IMAGEM" && ... !== "AUDIO")`) decide o que entra no
  visualizador de mídia. Inclua `VIDEO`.
- A legenda do vídeo aparece como já aparece a de imagem.
- Base UI: `data-active:`, nunca `data-[state=active]:`.

Se algum texto novo for necessário, ele vai em `textos.json` **e** no `schema.ts` no mesmo commit.

---

## Bloco 4 — Testes

- Payload de webhook com `type: "video"` → cria mensagem `VIDEO`, com `midiaUrl`, na conversa do
  remetente. **É o teste que falharia hoje.**
- Payload com `type: "sticker"` → cria mensagem `IMAGEM`.
- Payload com `type` desconhecido (por exemplo `"unsupported"`, que aparece em produção) → continua
  sendo ignorado **sem derrubar** as outras mensagens do mesmo POST. O comportamento de "ignorar só
  este item" não pode regredir.
- Um POST com `video` + `text` juntos grava as duas.
- Envio de vídeo pelo CRM chega ao adaptador como `type: "video"` com legenda quando houver.
- Frontend: balão de `VIDEO` renderiza `<video>` com controles e **sem** autoplay; abre no
  visualizador.
- Os testes de `AnexoMidiaIT` e do balão de imagem/áudio continuam verdes **sem edição**.

## Verificação

```
./mvnw -pl crm-app -am verify
```
e a suíte do frontend. Spotless, ArchUnit e a contagem de endpoints do OpenAPI verdes.

## Relatório

1. O número da migration e a confirmação de que ela tem uma instrução só, com o motivo.
2. A forma real do corpo de `sticker` no payload de produção.
3. Se existe limite de tamanho no download de mídia, e qual.
4. O que o `switch` do `MetaCloudApiAdapter` obrigou a tratar.
5. Confirmação de que tipo desconhecido continua sendo ignorado sem derrubar o POST inteiro.
6. Confirmação de que nada do caminho de download/MinIO foi tocado.
