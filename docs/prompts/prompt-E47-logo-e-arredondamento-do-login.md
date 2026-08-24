# Prompt E47 — logo real da Synapse e arredondamento dos campos

> Leia `AGENTS.md`, `frontend/AGENTS.md` e `design/TOKENS.md`.
> **Pode commitar localmente a qualquer momento.** Não execute `git push` sem autorização explícita.
> Referência: `design/login-synapse.html` (protótipo) e `design/logo-synapse.jpg` (a marca).

A tela de login já está muito próxima do protótipo. Faltam dois acabamentos.

---

## Bloco 1 — A marca real no lugar do "S" genérico

Hoje o login usa um SVG feito à mão: um quadrado arredondado com um "S" branco simples. O protótipo
usa a **marca real da Synapse** — o "S" em fita, com o gradiente rosa/roxo e as camadas
sobrepostas. São coisas visivelmente diferentes.

O arquivo está em **`design/logo-synapse.jpg`** — 640×640.

**Antes de usar, leia isto, porque o arquivo tem um problema real:**

**JPEG não tem canal alfa.** Essa imagem traz o fundo lavanda quadrado embutido. Colada sobre o
gradiente roxo do painel, ela vai aparecer como um adesivo com fundo próprio, com uma borda visível
onde o lavanda encontra o roxo. Não resolva isso com `mix-blend-mode` nem recortando no CSS — é
remendo que quebra quando o gradiente mudar.

Três saídas, em ordem de preferência. **Escolha uma, execute e diga qual foi e por quê:**

1. **SVG.** Redesenhe a marca em vetor a partir da imagem. É o certo: escala sem perder, pesa pouco,
   funciona sobre qualquer fundo. É também o mais trabalhoso, e a fidelidade das camadas e do
   gradiente precisa ser boa — se não ficar fiel, não force.
2. **PNG com transparência**, derivado do JPEG, recortando o fundo. Serve bem no tamanho em que a
   marca é usada (cerca de 32–40 px de altura no cabeçalho do painel). **Máximo de 40 KB**, no dobro
   do tamanho renderizado.
3. Se nenhuma das duas for viável com qualidade, **pare e relate** pedindo ao Marcondes o arquivo
   original em SVG ou PNG. Não entregue o JPEG cru sobre o gradiente.

O ativo entra no frontend como parte da identidade fixa do produto — não vai para `tema.json`, não
passa por `/api/v1/config/logo`, e não muda por instância. O nome do arquivo deve dizer o que ele é:
**`logo-synapse`**, não `login-synapse` — hoje `design/logo-synapse.jpg` (a marca) e
`design/login-synapse.html` (o protótipo da tela) têm nomes quase iguais e conteúdos sem relação
nenhuma, o que já confundiu uma etapa. Renomeie o arquivo de design para `design/logo-synapse.jpg`.

## Bloco 2 — Arredondamento dos campos

Compare os dois lado a lado: no protótipo os campos de e-mail e senha e o botão têm o canto
visivelmente mais arredondado que o da implementação atual. O protótipo também deixa o campo mais
claro, com a borda fazendo o trabalho de delimitar, enquanto a tela atual usa um preenchimento
lavanda mais forte.

- Tire o raio do **protótipo**, medindo no arquivo — não escolha um valor pelo olho.
- O botão "Entrar no painel" acompanha o mesmo raio dos campos.
- Se o projeto já tem token de raio em `design/TOKENS.md`, use-o. Se o valor do protótipo não existir
  entre os tokens, **acrescente o token** em vez de escrever o valor solto no JSX.
- Isso vale **só para o login**. Não altere o raio dos campos do resto do CRM.

## Bloco 3 — O que NÃO mudar

Nada além destes dois itens. Em especial, continuam como estão: a caixa "manter sessão"
**desmarcada** por padrão, o favicon vindo de `tema.logoUrl`, o fallback de fonte no `:root`, e a
ausência do tema da instância nas cores do login.

---

## Verificação

- `npm test -- --run`, `npm run lint`, `npm run build`.
- Informe o **peso final** do arquivo da marca.
- **Verificação visual obrigatória**, lado a lado com `design/login-synapse.html`, em desktop e
  celular — com atenção à borda da marca sobre o gradiente, que é o ponto que pode dar errado.
