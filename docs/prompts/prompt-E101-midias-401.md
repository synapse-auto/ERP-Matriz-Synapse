# Prompt E101 — Bug em produção: mídias do lead não abrem nem baixam (401)

> Leia `AGENTS.md` e `CLAUDE.md`. Branch própria (`fix/midias-401`) e PR. **Sem merge, sem deploy.**
> Backend + frontend: `./mvnw verify` no reator e a suíte do frontend. Sem migration.
> **Isto é correção de bug em produção.** Não aproveite a etapa para redesenhar a tela — o
> visualizador embutido é a E102, e depende desta.

---

## O sintoma

Na ficha do lead, em **Mídias e documentos**, clicar num arquivo devolve 401, ou o navegador mostra
"arquivo não está disponível" / "Tente fazer login no site". Nenhuma mídia abre, nenhuma baixa.

## Bloco 1 — A causa, já localizada. Confirme antes de corrigir.

A autenticação deste CRM é `Authorization: Bearer`, com o token **em memória** no `auth-store`
(`http-client.ts`). **Não há cookie de sessão.** Logo, qualquer `src`/`href` que o navegador resolve
sozinho vai sem token e toma 401.

E é exatamente o que a tela faz. `MidiasDoLeadController` devolve
`urlDownload = "/api/v1/leads/{leadId}/midias/{mensagemId}/download"` — caminho relativo, protegido
por JWT — e `painel-da-conversa.tsx` o usa cru em três lugares:

- linha ~294: `<a href={item.urlDownload} download>` → o download nunca leva o token;
- linha ~299: `<audio src={item.urlDownload}>` → áudio nunca toca;
- linha ~300: `<img src={item.urlDownload}>` → imagem sempre quebrada.

É o mesmo erro que a E97 encontrou na foto do lead, no mesmo arquivo de origem: caminho autenticado
usado como URL pública. O único lugar do app que faz certo é `avatar-iniciais.tsx`, via
`apiFetchBlob` (que manda o `Authorization` e ainda renova o token em 401).

## Bloco 2 — O achado que decide o desenho: existem DOIS caminhos para os mesmos bytes

Confirme lendo, porque muda a correção:

- **Bolha do chat** (`bolha-mensagem.tsx`) usa `mensagem.midiaUrl`, que é **URL assinada** do MinIO
  — `AnexoMidiaIT` verifica `baixarPelaUrlAssinada` e a presença de `token=`. Passa por `urlSegura`,
  que só aceita `http(s)` absoluto. Funciona no `<img>` porque não depende do nosso JWT.
- **Painel de mídias** usa o caminho autenticado acima. Não funciona em lugar nenhum do navegador.

Dois mecanismos para o mesmo arquivo, e um deles quebrado. Corrigir não é escolher um remendo: é
**unificar**.

E tem um detalhe do assinado que você precisa medir antes de decidir: a URL expira. O
`AnexoMidiaIT` chega a afirmar que ela deixa de resolver depois de um tempo. Descubra **qual é o TTL
configurado** e diga no relatório — se for curto, a bolha do chat de uma conversa antiga também está
quebrada hoje, e ninguém percebeu porque o painel quebra mais barulhentamente.

## Bloco 3 — A correção

**Decisão tomada: unificar na URL assinada de curta duração, emitida sob demanda por um endpoint
autenticado.**

O motivo é o vídeo. `apiFetchBlob` resolveria imagem e PDF, mas baixa o arquivo inteiro para a
memória e não fala HTTP Range — vídeo não busca, não faz seek, e um arquivo grande trava a aba. URL
assinada é o que o `<video>` precisa, é o que a bolha do chat **já** usa, e mantém a autorização
onde ela importa: na hora de **emitir** a URL, o backend valida lead, atendimento e mensagem, que é
o que `ListarMidiasDoLeadUseCase.executar(leadId, mensagemId)` já faz.

Portanto:

- O endpoint de listagem **para de devolver** um `urlDownload` que o navegador não consegue usar.
  Ou devolve a URL assinada já pronta, ou devolve apenas o metadado e a tela pede a URL quando
  precisa. **Prefira emitir sob demanda** — no clique, na abertura — e não na listagem: URL emitida
  na listagem já nasce gastando TTL enquanto o painel fica aberto sem ninguém clicar.
- `GET .../download` continua existindo e continua autenticado. Ele é o caminho para quem quiser
  buscar por JWT (e o `apiFetchBlob` continua funcionando nele). O que muda é a tela parar de
  pendurá-lo em `src`/`href`.
- No frontend, o download precisa acontecer **sem navegar para uma URL protegida**. Duas saídas
  aceitáveis: `<a>` apontando para a URL assinada, ou `apiFetchBlob` + `objectURL` + `download`.
  Escolha uma, **use a mesma nos dois lugares** (painel e bolha) e explique.

**A regra que não pode ser quebrada:** o navegador nunca recebe a URL bruta do MinIO sem assinatura,
e nenhum caminho `/api/v1/...` protegido volta a aparecer dentro de `src` ou `href`. Se o seu
diff introduzir um caso desses em qualquer lugar, ele está errado.

## Bloco 4 — Varra o resto do app pelo mesmo erro

Este bug é uma classe, não uma linha. Procure **todo** `src=` e `href=` que aponte para um caminho
começando em `/api/` e relate cada ocorrência, corrigida ou não:

- o painel de mídias (os três casos acima);
- `player-audio.tsx` (`src={src}` — de onde vem esse `src`?);
- a bolha do chat;
- qualquer outro lugar que a busca revelar.

Se algum deles já estiver certo por usar URL assinada, diga isso — a lista completa é o entregável,
não só as correções.

## Bloco 5 — Testes

- IT: a URL emitida para uma mídia resolve o arquivo certo; e um usuário que **não** enxerga o lead
  não consegue emitir URL nenhuma — 404, não 403.
- IT: emitir URL para `mensagemId` de outro lead falha, mesmo com o `leadId` correto no caminho.
- Frontend: o painel de mídias renderiza imagem, áudio e documento sem apontar `src`/`href` para
  `/api/`; um teste que falhe se alguém reintroduzir o padrão vale mais que três testes de render.
- Frontend: clicar em baixar dispara o caminho autorizado, não uma navegação crua.

## Verificação

```
./mvnw verify        # no reator, na raiz de backend/
npm run lint && npm run typecheck && npm run test && npm run build   # em frontend/
```

## Relatório

1. O TTL da URL assinada, onde ele está configurado, e se a bolha do chat de conversa antiga está
   quebrada hoje por causa dele.
2. Onde a URL passou a ser emitida e por quê (sob demanda vs na listagem).
3. A lista **completa** de `src`/`href` apontando para `/api/` que a varredura encontrou.
4. Qual das duas saídas de download você escolheu e por que a mesma serve para painel e bolha.
5. Se alguma mídia antiga em produção continuar inacessível depois desta correção, diga qual caso e
   por quê — melhor eu saber agora do que o Marcondes descobrir com um cliente esperando.
