# Prompt E144 — Composer flutuante: sem faixa de fundo, 12% mais largo, ícones 10% maiores

> Leia `AGENTS.md`, `CLAUDE.md` e `docs/13-estado-do-projeto.md`.
> Branch própria (`feat/composer-flutuante`) e PR. **Sem merge, sem deploy.**
> **Somente frontend.** Sem backend, sem migration, sem mudança de contrato.
> Suíte do `frontend/`, typecheck, lint e build.

Três ajustes no campo de mensagem da conversa, para ele **flutuar** sobre o histórico em vez de
sentar numa faixa:

1. Sair a faixa de fundo que hoje fica atrás dele.
2. O campo fica **12% mais largo**.
3. Os ícones **dentro dele** ficam **10% maiores**.

---

## Onde mexer

`frontend/src/components/atendimentos/composer.tsx`, no bloco de retorno com a janela aberta:

```jsx
<div className="shrink-0 bg-background px-4 pb-4 pt-3">      ← a faixa é este bg-background
  <div className="relative mx-auto max-w-[780px]">           ← a largura é este max-w
    <p className="mb-1.5 ...">{textos.janelaAberta}</p>
    <div className="rounded-xl border border-input bg-card p-3 shadow-md">   ← o cartão
```

`780px + 12% ≈ 874px`. Use o valor arredondado que o projeto já usa como convenção de escala; se
não houver, `874px` serve — registre a conta no comentário.

## Bloco 1 — Flutuar sem quebrar o que está atrás

Tirar o `bg-background` faz o composer flutuar sobre a lista de mensagens. Duas consequências que
você precisa resolver, não ignorar:

- **A última mensagem passa a correr por baixo do composer.** Garanta que o fim do histórico
  continue alcançável — o container das mensagens precisa de espaço no fim equivalente à altura do
  composer, ou o atendente perde a última bolha atrás do cartão flutuante.
- **A faixa "Janela de texto livre aberta"** hoje mora fora do cartão, sobre o fundo. Sem o fundo
  ela fica solta sobre as mensagens. Decida onde ela vai (dentro do cartão, ou com fundo próprio) e
  **descreva a escolha no relatório** — não deixe texto sem fundo por cima de bolha colorida.

O cartão mantém `bg-card`, borda e sombra: é ele que dá o contorno agora que não há faixa.

## Bloco 2 — Os ícones, sem escalar o app inteiro

Os ícones do composer usam `size-(--tamanho-icone-interface)`, que é **token global** (E94). Se
você mexer no token, **todos os ícones do CRM crescem 10%** — não é o pedido.

Aumente **só dentro do composer**, sobrescrevendo o token no escopo do componente, no padrão do
Tailwind v4 (`[--tamanho-icone-interface:calc(...)]` no contêiner do composer, ou equivalente que o
projeto já use). Assim clipe, raio, relógio, emoji, microfone e enviar crescem juntos, e nada fora
dali muda.

Confirme no relatório que a sidebar, o cabeçalho da conversa e a lista de conversas **não** mudaram
de tamanho de ícone.

## Bloco 3 — Telas estreitas

O CRM tem versão de celular (`telaEstreita`). 12% a mais de largura não pode gerar rolagem
horizontal nem encostar nas bordas em **390px**. Se os 12% só fizerem sentido no desktop, aplique
com o limite que já existe (`min()` com a largura da viewport, como o popover de emoji já faz) e
diga isso no relatório.

## Verificação obrigatória

Layout não se prova com teste unitário. **Abra no navegador, autenticado**, e confirme em
**1440, 1024 e 390**:

1. Não há faixa atrás do composer — ele flutua sobre as mensagens.
2. A última mensagem do histórico continua visível e alcançável por rolagem.
3. O composer está mais largo, sem rolagem horizontal em nenhuma das três larguras.
4. Os ícones do composer estão maiores; os da sidebar e do cabeçalho, **iguais**.
5. O aviso da janela continua legível.

Anote as três larguras testadas no relatório. Se não conseguir abrir autenticado, **diga isso** em
vez de afirmar que verificou — relatório com verificação visual inventada é pior que relatório sem.

## Fora do escopo

- O bloco de janela fechada (o cartão de "Nova mensagem" com template) — só o composer da janela aberta.
- Mudar o token global de ícones, a paleta, o `bg-card` ou a sombra.
- Reordenar ou remover botões do composer.
- Qualquer arquivo fora de `composer.tsx` e do container de mensagens que precise do espaço no fim.

## Definição de pronto

- Composer flutua, 12% mais largo, ícones 10% maiores só ali.
- Última mensagem não fica presa atrás do composer.
- Nada fora do composer mudou de tamanho.
- Verificado nas três larguras, com as evidências no relatório.
- Testes, typecheck, lint e build verdes; `git diff --check` limpo.
- Relatório final com os sete itens do `AGENTS.md`.
