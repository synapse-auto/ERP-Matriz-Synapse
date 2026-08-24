# Prompt E46 — regressões da E45 e fidelidade da tela de login

> Leia `AGENTS.md`, `CLAUDE.md`, `frontend/AGENTS.md` e `design/TOKENS.md`.
> **Pode commitar localmente a qualquer momento.** Não execute `git push` sem autorização explícita.
> Referência visual: **`design/login-synapse.html`** — o export real do Claude Design. Ele existe no
> checkout como `design/login-synapse.html.html` (extensão dupla); **renomeie para
> `design/login-synapse.html` e commite**. Não execute nem importe o arquivo; extraia dele
> hierarquia, proporção, cor, tipografia e texto.

---

## Bloco 1 — Duas regressões da E45, com a causa já localizada

**Faça estes dois primeiro. São defeitos, não ajuste de gosto.**

### 1a. Tudo em `/login` está renderizando com fonte serifada

`globals.css` define:

```css
--font-sans: var(--fonte-base-carregada), var(--fonte-base);
```

`--fonte-base-carregada` vem do `next/font/local` e continua presente. Mas `--fonte-base` era
emitida por `temaParaCssVariaveis(tema)`, que a E45 **removeu do layout raiz** — e em `/login` não há
mais nada que a defina.

Em CSS, uma `var()` sem valor torna a **declaração inteira inválida no tempo de valor computado**.
`--font-sans` não cai no primeiro item da lista: ela é descartada por completo e `font-family` volta
ao valor inicial do navegador, que é **serifado**. É por isso que a tela inteira mudou de fonte, e
não só o título.

**Correção:** dê a `--fonte-base` e `--fonte-mono` um valor padrão em `:root`, no `globals.css`, que
não dependa do tema da instância. A cadeia passa a nunca quebrar em rota nenhuma, e o `tema.json`
continua sobrescrevendo dentro do shell. **Não** resolva devolvendo o tema ao layout raiz — isso
desfaz a decisão da E45.

Enquanto estiver aí: procure **qualquer outra** `var()` do tema usada fora do `(shell)`. O mesmo
mecanismo derruba a declaração inteira, em silêncio, e o sintoma nunca aponta para a causa.

### 1b. O favicon sumiu

A E45 removeu isto do `generateMetadata()` do layout raiz:

```ts
if (tema.logoUrl) {
  metadata.icons = { icon: tema.logoUrl };
}
```

Sem `icons`, a aba fica sem ícone. Já custou caro antes (E31b) e voltou.

**Correção:** restaure `icons` lendo `tema.logoUrl`. **A logo da aba é do cliente, não da Synapse** —
o ícone identifica a aba do CRM daquele cliente, que é onde a pessoa passa o dia.

Isso **não** viola a regra da E45. O que não pode entrar em `/login` são as **cores e a logo
visível** da instância. Ler o tema dentro de `generateMetadata()` para definir o favicon não injeta
variável de tema nenhuma na página. Escreva isso como comentário, ou alguém remove de novo.

**Teste de regressão obrigatório para os dois:** um que prove que `icons` é definido a partir de
`tema.logoUrl`, e um que prove que `--font-sans` resolve para a fonte local em `/login` — sem
depender de `--fonte-base`.

---

## Bloco 2 — A tela não se parece com o protótipo

A E45 foi implementada a partir de descrição escrita, porque o HTML não estava no repositório.
Agora está. **Refaça a fidelidade a partir dele**, não da sua memória nem deste texto.

Diferenças visíveis na comparação:

- **O texto é outro.** O protótipo diz "Todo o atendimento da sua operação em um só lugar." com o
  apoio "Um painel para toda sua operação de atendimento e gerenciamento de clientes." A tela atual
  inventou "Relacionamentos que movem negócios." Use o texto do protótipo — e ele vive no catálogo,
  não no JSX.
- **A marca está incompleta.** O protótipo tem o "S" com o gradiente da Synapse, a palavra
  **Synapse** ao lado e a pastilha **CRM**. A tela atual tem só um quadrado roxo com um "S" branco.
- **O fundo está errado.** O protótipo é violeta claro com um arco suave e amplo; a tela atual é um
  roxo escuro chapado. O contraste do texto muda junto — remeça depois de acertar.
- **Há um "S" gigante de água por trás do formulário** que não existe no protótipo. Remova.
- **Os três destaques estão no lugar errado.** No protótipo são três colunas no rodapé do painel —
  CRM, Atendimento, IA Assistente, cada um com título e uma linha de apoio. Na tela atual são três
  linhas empilhadas no meio da coluna esquerda.
- **Faltam elementos do painel direito:** a pastilha "Ambiente seguro · acesso por instância" no
  topo, a linha "Não tem acesso? Fale com o administrador da sua instância." e o rodapé
  "© 2026 Synapse · Automação inteligente". O título é "Acesse sua conta", com o apoio "Entre com
  suas credenciais para continuar no painel."
- Os campos do protótipo têm **ícone à esquerda** (envelope e cadeado).

**Mantém-se da E45, não regrida:** a caixa "manter sessão" **desmarcada** por padrão (decisão do
Marcondes, diferente do protótipo), o cookie de sessão e sua preservação na rotação de refresh, a
ausência de `tema.json` nas cores do login, e o logo em SVG leve.

## Bloco 3 — O que NÃO entra

- Não mexa no backend de autenticação.
- Não devolva o tema da instância ao layout raiz.
- Não troque as fontes da aplicação por Plus Jakarta Sans / Inter, e não use CDN de fonte. Se o
  título exigir a fonte de display do protótipo, versione-a em `app/fonts/` como as outras e use-a
  **só** no login.

---

## Verificação

- `npm test -- --run`, `npm run lint`, `npm run build`.
- Os dois testes de regressão do Bloco 1.
- **Verificação visual obrigatória, lado a lado com `design/login-synapse.html` aberto**, em largura
  de desktop e de celular. Se não conseguir subir a aplicação, **diga em letras claras** — não
  descreva como a tela deveria estar.
- Confirme que o favicon aparece na aba e que nenhuma tela do `(shell)` perdeu a cor do cliente.
