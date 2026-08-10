# Prompt E18 — Ligar o tema do produto à base do shadcn

> Leia `AGENTS.md`. **Esta é a correção de maior impacto visual do projeto inteiro** e mexe num arquivo só.
> Prioridade máxima: ela precede qualquer outro ajuste de tela.

---

## O diagnóstico

O projeto tem dois sistemas de token que nunca foram conectados.

**`backend/crm-app/src/main/resources/tema.json`** carrega a paleta correta do protótipo, injetada em runtime como `--cor-primaria`, `--fundo-app`, `--borda`, `--texto-suave`, `--raio-lg` e companhia.

**`frontend/src/app/globals.css`** — o que os componentes shadcn de fato leem — está com o `:root` **stock, em escala de cinza**, exatamente como veio do `shadcn init`:

```css
--background: oklch(1 0 0);           /* branco, não #F4F7FB */
--primary: oklch(0.205 0 0);          /* quase preto, não #1F74E0 */
--border: oklch(0.922 0 0);           /* cinza neutro, não #E7EDF4 */
--muted-foreground: oklch(0.556 0 0); /* cinza, não #5A6B7B */
--radius: 0.625rem;                   /* 10px, não 16px */
--chart-1..5: oklch(... 0 0);         /* cinco tons de cinza */
```

Resultado: todo `bg-card`, `border`, `bg-primary`, `text-muted-foreground` e `rounded-lg` do app resolve para o cinza padrão do shadcn. Os agentes usaram os utilitários certos; a camada de baixo é que nunca foi configurada.

**Caso mais visível:** cor de tag é guardada como `var(--chart-1..5)` desde a E15 — cinco tons de cinza neste tema. A tela de Tags está monocromática.

## A correção

Reescreva o `:root` de `globals.css` mapeando cada token do shadcn para o token equivalente do tema. Mantenha os nomes do shadcn — não renomeie nada, senão todo componente quebra.

```css
:root {
  --background: var(--fundo-app);
  --foreground: var(--texto-padrao);

  --card: var(--fundo-superficie);
  --card-foreground: var(--texto-padrao);
  --popover: var(--fundo-superficie);
  --popover-foreground: var(--texto-padrao);

  --primary: var(--cor-primaria);
  --primary-foreground: var(--cor-primaria-texto);

  --secondary: var(--fundo-sutil);
  --secondary-foreground: var(--texto-forte);

  --muted: var(--fundo-sutil);
  --muted-foreground: var(--texto-suave);

  --accent: var(--cor-primaria-suave);
  --accent-foreground: var(--cor-primaria);

  --destructive: var(--cor-erro);
  --border: var(--borda);
  --input: var(--borda-forte);
  --ring: var(--cor-primaria);

  --radius: var(--raio-md);

  /* Paleta de tags — precisa ser COLORIDA e distinguível entre si. */
  --chart-1: var(--cor-info);
  --chart-2: var(--cor-ia);
  --chart-3: var(--cor-destaque-2);
  --chart-4: var(--cor-destaque-3);
  --chart-5: var(--cor-atencao);
}
```

**Confira a convenção real de nome antes de escrever.** O `@theme` no topo do arquivo mostra o padrão (`--color-fundo-sidebar-bloco: var(--fundo-sidebar-bloco)`), ou seja `corPrimaria` do JSON vira `--cor-primaria`. Confirme cada um contra o injetor de tema em vez de deduzir.

**Fallback obrigatório.** Se o tema vier do backend em runtime, existe um instante antes de ele chegar — e o `next build` do standalone já mordeu esse caminho antes. Use `var(--fundo-app, #F4F7FB)` com o valor do `tema.json` como fallback em cada linha, para que a página nunca renderize sem cor.

## Três consequências para verificar depois

1. **Raio de card.** O protótipo usa 16px em card (`--raio-lg`) e ~11px em controle (`--raio-md`). Como `--radius` do shadcn é único e deriva os demais, confira se `Card` está com 16px; se não, ajuste o componente `Card` para `rounded-lg` explícito em vez de mexer no `--radius` global.

2. **Contraste.** `--muted-foreground` passa de cinza para `#5A6B7B`. Percorra as telas e confirme que nenhum texto ficou ilegível sobre `--fundo-sutil`.

3. **Sombra.** O protótipo usa sombras grandes e azuladas (`0 40px 120px -34px rgba(12,42,67,.5)`); o shadcn usa sombra neutra pequena. Se `--sombra-*` existir no tema, mapeie também.

## Correções estruturais que vêm junto

Levantadas comparando as telas renderizadas do protótipo com o que foi construído. **A instrução anterior estava errada em um ponto e precisa ser revertida.**

**1. Equipe volta a ser tabela.** O Bloco 4 da E17b mandou converter Equipe em cards. Errado: o protótipo usa **tabela** com colunas `USUÁRIO | FUNÇÃO | PRESENÇA | AVALIAÇÃO | ATEND. | VENDAS | AÇÕES`, e avatar, pills e estrelas **dentro das células**. Acima dela ficam quatro cards de estatística (Equipe, Avaliação média, Ranking por avaliação, Ranking por vendas fechadas).

Reverta para tabela, mantendo os componentes `AvatarIniciais` e `PillDeStatus` já extraídos — eles continuam válidos, só mudam de lugar. As colunas ATEND. e VENDAS e os dois rankings continuam fora por falta de endpoint; deixe as demais.

Antes de mexer nas outras três (Lembretes, Mensagens Rápidas, Mensagens Programadas), **abra o `.html` de cada uma e confirme se é card ou tabela.** Não repita a suposição.

**2. A sidebar flutua.** No protótipo ela é um painel arredondado com margem em volta, sobre o fundo `#E6ECF4` — não encostada na borda da janela e não de altura total. Ajuste margem e raio.

**3. O card de tag tem três elementos que ficaram de fora:**
- quadrado do ícone com **fundo tintado na cor da tag** (é o `color-mix` que foi recusado por falta de precedente — agora tem precedente, use)
- **barra de progresso** na cor da tag, proporcional ao % da base
- chip "Prévia da etiqueta" mostrando como a tag aparece no resto do CRM

**4. Pills coloridas por significado**, não uma cor só: etapa em azul/âmbar/verde conforme o estágio, presença em verde/âmbar/cinza, função em azul.

## Definição de pronto

- [ ] `:root` de `globals.css` mapeado, com fallback em cada linha
- [ ] Tags renderizando **coloridas**, não em cinza
- [ ] Botão primário em `#1F74E0`
- [ ] Fundo do app em `#F4F7FB`, cards brancos por cima — o card precisa se destacar do fundo
- [ ] Bordas azuladas (`#E7EDF4`), não cinza neutro
- [ ] `.dark` deixado como está se o produto não usa tema escuro — diga no relatório qual foi o caso
- [ ] Passada visual em todas as telas, relatando o que **piorou** além do que melhorou
- [ ] `./mvnw clean verify` se tocar em backend; **número da run** do CI
- [ ] Commit e push

Commit: `fix: liga o tema do produto aos tokens base do shadcn`.

No relatório: diga se encontrou token do `tema.json` **sem** equivalente no shadcn, ou o contrário. Essa lista é o que falta para o tema ser realmente completo, e vale para todo filho futuro — não só para a Estrutural.
