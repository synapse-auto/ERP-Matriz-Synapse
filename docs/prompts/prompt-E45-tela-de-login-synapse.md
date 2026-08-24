# Prompt E45 — tela de login da Synapse

> Leia `AGENTS.md`, `CLAUDE.md`, `frontend/AGENTS.md` e `design/TOKENS.md`.
> **Pode commitar localmente a qualquer momento.** Não execute `git push` sem autorização explícita
> do Marcondes.
> Referência visual: `design/login-synapse.jpg` (captura de tela do protótipo do Claude Design).
> Extraia hierarquia, proporção, paleta e estados; a implementação continua em React, Tailwind,
> `lucide-react`, catálogo de textos e tokens.

---

## Contexto e a decisão que organiza tudo

Esta tela é **da Synapse, o produto** — não do filho. O mesmo login, com a mesma marca, aparece em
toda instância: Estrutural Vidros, o próximo cliente e o seguinte. A marca do cliente continua
aparecendo **dentro** do CRM (a Sidebar já lê `logoUrl` e `corPrimaria` de `tema.json` por
instância); o portão de entrada é da Synapse.

Isso é uma **exceção deliberada** à regra de "trocar de cliente sem editar código", e precisa estar
escrita no código, não só aqui:

- O login **não** lê `tema.json`, **não** chama `/api/v1/config/tema` e **não** usa
  `/api/v1/config/logo`. Ele não muda de cor por instância.
- A identidade da Synapse (roxo, logo "S") vive no frontend como constante de produto, com um
  comentário explicando por que é fixa. Sem isso, o próximo agente vai "consertar" ligando o tema.
- A Sidebar e o resto do CRM **não mudam de cor**. O interior continua azul (`corPrimaria`
  `#1F74E0`) enquanto o cliente não pedir outra coisa. Roxo é só o login.

## Bloco 0 — O que já está certo e você não vai refazer

Leia antes de escrever qualquer linha.

- **O fluxo de autenticação já é seguro e não muda.** `app/api/auth/login/route.ts` faz papel de BFF:
  chama `POST /api/v1/auth/login`, guarda o `refreshToken` num cookie **httpOnly** e devolve só
  `accessToken` e `expiraEmSegundos`, que o Zustand mantém **em memória**. Nada de token em
  `localStorage`, nem agora nem para o "manter sessão".
- **As fontes da aplicação são `HankenGrotesk` e `JetBrainsMono`**, versionadas em `app/fonts/` via
  `next/font/local`. O protótipo usa Plus Jakarta Sans e Inter. **Mantenha as fontes atuais.** O
  comentário no `layout.tsx` explica por que elas existem: o fallback mais largo truncava rótulos
  como "Mensagens Programadas". Se o título do login realmente exigir a fonte de display, versione-a
  localmente no mesmo padrão e use **só no login** — nunca um `<link>` para CDN de fontes.
- O `LoginForm` atual já trata erro 401 separado de erro genérico, e já usa o catálogo de textos.
  Preserve os dois comportamentos.

## Bloco 1 — A marca

- O layout é de duas colunas: painel de apresentação à esquerda (gradiente roxo, título, subtítulo e
  três destaques no rodapé), formulário à direita sobre fundo claro.
- Paleta do protótipo: primária `#7C5CE0`; o gradiente do painel vai de `#6A47C7` a `#C4B2FD`
  passando por `#7C5CE0` e `#A78BFA`. **Nenhum hexadecimal solto no JSX** — declare os tokens da
  identidade Synapse num único lugar e consuma por token, como o resto do projeto faz.
- **O logo: cuidado com o peso.** A referência visual mostra o "S" como imagem; isso não pode virar
  um raster pesado em uma tela que todo mundo abre todo dia. Entregue o logo como **SVG**, ou, se
  não for viável redesenhar, como raster otimizado **abaixo de 40 KB** no dobro do tamanho
  renderizado. Diga no relatório qual caminho tomou e o peso final do arquivo.
- Em telas estreitas o painel de apresentação **colapsa** e sobra o formulário. Ninguém entra no CRM
  pelo celular rolando dois metros de gradiente.

## Bloco 2 — "Manter sessão ativa neste dispositivo" precisa funcionar

Hoje a caixa não existe. O mecanismo correto já está a um passo de distância, e **não envolve
`localStorage`**:

- A escolha viaja no corpo do `POST /api/auth/login` e é decidida **no route handler**, não no
  cliente.
- **Marcada:** o cookie do refresh é gravado com `maxAge: MAX_AGE_COOKIE_REFRESH_SEGUNDOS` — como
  hoje.
- **Desmarcada:** o cookie é gravado **sem `maxAge` e sem `expires`**, virando cookie de sessão: ele
  morre quando o navegador fecha.
- O `accessToken` continua em memória nos dois casos. A caixa muda a vida do **refresh**, nada mais.

**A armadilha, já verificada no código:** `app/api/auth/refresh/route.ts` regrava o cookie com
`maxAge: MAX_AGE_COOKIE_REFRESH_SEGUNDOS` **incondicionalmente**. Sem tratar isso, a primeira
rotação de token transforma o cookie de sessão num cookie de 7 dias, e a caixa desmarcada não vale
nada — em silêncio. O refresh precisa **preservar a escolha original**. Resolva isso de forma
explícita e diga como; a decisão é sua, mas ela tem que sobreviver a uma rotação e a um `logout`.

**Padrão da caixa:** entregue **desmarcada**. O protótipo mostra marcada, mas este CRM roda em
balcão de vidraçaria, onde a mesma máquina é usada por mais de um atendente. Sessão persistente por
padrão em máquina compartilhada é decisão de segurança que ninguém tomou conscientemente. Se o
Marcondes preferir marcada, é uma linha — mas o padrão sai desmarcado.

## Bloco 3 — A transição entre as telas

Hoje o login faz `router.push("/")` e a tela pisca até o shell montar. Entregue uma transição:

- Um estado de carregamento **entre autenticar e o CRM aparecer**, com a identidade da Synapse — não
  um spinner genérico no meio do branco.
- Ele cobre o intervalo real: sessão gravada, primeiras consultas do shell em voo. **Não invente
  atraso artificial** para o carregamento "aparecer bonito"; se a transição for instantânea, ótimo.
- **Duração mínima, se houver, é para evitar o piscar** — não para simular trabalho. Um limite
  superior é obrigatório: se algo travar, o usuário vê erro, não roda para sempre.
- O botão "Entrar no painel" já tem estado de envio. Ele e a tela de transição são coisas
  diferentes: um é "estou autenticando", o outro é "autenticou, estou abrindo o painel".

## Bloco 4 — Estados e acessibilidade

- Erro de credencial e erro genérico continuam distintos, ligados ao campo por `aria-describedby`, e
  anunciados por região viva — quem usa leitor de tela precisa ouvir que a senha está errada.
- O olho de revelar senha é um `button` com rótulo que muda entre mostrar e ocultar, não um ícone
  mudo.
- `label` real em cada campo, `autocomplete` correto (`email` e `current-password`), `Enter` envia.
- Foco visível em tudo que é operável. O contraste do texto sobre o gradiente precisa passar em
  AA — meça, não confie no olho.
- Todo texto no catálogo (`textos.json` + `schema.ts`). **Nenhum literal no JSX**, inclusive os três
  destaques do rodapé e a linha "Ambiente seguro".

## Bloco 5 — O que NÃO entra

- Nada de "esqueci minha senha", cadastro, SSO ou lembrar e-mail digitado.
- Não mexa no backend de autenticação: nem no `AutenticacaoController`, nem no `RenovarSessaoUseCase`,
  nem na validade do refresh token. Esta etapa é frontend e route handler.
- Não troque a cor do CRM por dentro.
- Não ligue `tema.json` nesta tela.

---

## Verificação

- `npm test -- --run`, `npm run lint`, `npm run build`.
- Teste de que a caixa marcada grava cookie com `maxAge` e a desmarcada grava cookie de sessão.
- **Teste de que uma rotação de refresh não converte cookie de sessão em persistente.** É o teste que
  faz a funcionalidade valer alguma coisa.
- Teste de que o `accessToken` **não** aparece em `localStorage` nem em `sessionStorage` em nenhum
  dos dois modos.
- Teste dos dois erros distintos e do anúncio acessível.
- Verificação de peso: informe o tamanho final do arquivo do logo.
- **Verificação visual obrigatória**, em largura de desktop e de celular, com o backend de pé. Se não
  conseguir, **diga em letras claras** em vez de descrever como a tela deveria estar.
