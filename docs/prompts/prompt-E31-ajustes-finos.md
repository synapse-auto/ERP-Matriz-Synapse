# Prompt E31 — Ajustes finos de interface

> Leia `AGENTS.md`. Entrega em 25/08.
> Quatro ajustes independentes. Commite e faça push por bloco.
> Ao encerrar: `cd backend && ./mvnw clean verify` e o **número da run** do CI.

Etapa pequena e de baixo risco. Nenhum bloco depende do outro — se um travar, publique os
demais.

---

## Bloco 1 — Confirmação antes de desativar um usuário

Hoje a ação dispara direto, sem pergunta. Um clique errado tira um atendente do ar no meio do
expediente.

**A ação é `desativar`, não excluir** — `DesativarUsuarioUseCase` marca `ativo = false`, e nada
é apagado: o histórico, os leads e os atendimentos do usuário permanecem. O texto do diálogo
precisa dizer isso. Chamar de "excluir" assusta quem clica e é falso; a pessoa precisa saber que
dá para reativar e que nada se perde.

- Diálogo de confirmação com o **nome do usuário** no texto, não um "tem certeza?" genérico.
- Ação destrutiva visualmente distinta, com o botão seguro em foco por padrão.
- Cancelar não deixa efeito colateral nenhum.
- Textos do catálogo, cores por token.

Teste: a mutação de desativar **não** é chamada ao abrir o diálogo nem ao cancelar; é chamada
uma única vez ao confirmar.

## Bloco 2 — Revelar a senha digitada

Campo de senha sem alternância obriga a digitar às cegas — e, num sistema onde a senha vem de
terceiro e precisa ser trocada no primeiro acesso (E29), errar a digitação é a regra, não a
exceção.

Aplique em **todos** os campos de senha: login, troca de senha (as três caixas: atual, nova,
confirmação) e criação de usuário na Equipe.

- Botão dentro do campo, alternando `type` entre `password` e `text`.
- Começa sempre oculto; alternar nunca é o estado inicial.
- `aria-label` do catálogo, mudando conforme o estado (mostrar/ocultar).
- Alcançável por teclado, e o foco não pula para fora do campo ao alternar.
- **Não** persista a preferência entre sessões: senha visível por padrão numa tela compartilhada
  é pior do que o incômodo que resolve.

Teste: o `type` do input alterna, o rótulo acessível acompanha, e o valor digitado é preservado
na alternância.

## Bloco 3 — A marca da instância no favicon e na sidebar

Hoje o favicon é o do scaffold do Next (`frontend/src/app/favicon.ico`), e a sidebar desenha um
quadrado com gradiente — placeholder, não marca.

**O ponto de extensão já existe e nunca foi ligado:** `tema.json` tem `"logoUrl": null`. É por
isso que aparece o quadradinho — é o fallback.

> **Não coloque o logo da Estrutural Vidros no core.** Isto é Base PAI: arquivo de imagem de um
> cliente dentro de `frontend/` é exatamente o que o `AGENTS.md` proíbe. O logo é dado da
> instância, como o `tema.json` e o `textos.json`.

O que fazer:

- Ligar `logoUrl` de verdade: quando preenchido, vira o **favicon** e a **marca da sidebar**;
  quando `null`, o comportamento atual continua exatamente igual. Nenhum filho novo quebra por
  não ter logo.
- O `RootLayout` já injeta as CSS variables do tema no `<head>` a cada carregamento — o mesmo
  caminho serve para o ícone. Confirme na documentação local do Next 16 qual a forma correta de
  favicon dinâmico nessa versão (o `AGENTS.md` do frontend exige essa consulta); `src/app/favicon.ico`
  é estático e não serve para valor vindo de configuração.
- Onde o arquivo da instância mora é decisão sua — proponha e **justifique no relatório**. A
  restrição é uma só: trocar de cliente não pode exigir editar código nem rebuildar a imagem.
- Aproveite e apague o lixo do scaffold que sobrou em `frontend/public/`: `next.svg`,
  `vercel.svg`, `file.svg`, `globe.svg`, `window.svg`.

Teste: com `logoUrl` preenchido, favicon e sidebar usam a imagem; com `null`, cai no desenho
atual sem erro no console.

**Ação necessária depois desta etapa:** o arquivo do logo da Estrutural e o `logoUrl` apontando
para ele são configuração de instância — entram no relatório como passo de operação, não no
commit.

## Bloco 4 — Bordas mais discretas

A tela tem borda demais: cada card, cada painel e cada bloco da Dashboard desenha um contorno, e
o conjunto fica ruidoso.

- Ajuste **nos design tokens**, não componente a componente. Se a mudança exigir tocar em vários
  `.tsx`, é sinal de que o valor não estava tokenizado — tokenize.
- Onde a borda existe só para separar, prefira o mesmo recurso que o protótipo usa para hierarquia
  (elevação, fundo, espaçamento) em vez de somar contorno.
- **Não** vale remover borda de campo de formulário: input sem contorno some para quem enxerga
  pouco. Contraste de foco permanece.
- Vale para claro e escuro.

Como isto é subjetivo, entregue uma comparação: capturas de Atendimentos, Dashboard e Equipe
antes e depois, no mesmo viewport.

---

## Definição de pronto

- [ ] Diálogo de confirmação ao desativar, com o nome do usuário e o texto dizendo "desativar"
- [ ] Alternância de visibilidade em todos os campos de senha, começando oculta
- [ ] `logoUrl` ligado no favicon e na sidebar, com fallback intacto quando `null`
- [ ] Nenhum arquivo de marca de cliente dentro de `frontend/`
- [ ] Sobras do scaffold do Next removidas de `public/`
- [ ] Bordas ajustadas via tokens, com comparação antes/depois
- [ ] Testes dos blocos 1 e 2
- [ ] CI verde com **número da run**

## No relatório

Onde o arquivo de logo da instância passa a morar, e por quê — a restrição é que trocar de
cliente não exija editar código nem rebuildar imagem.

Quais tokens de borda mudaram e quantos `.tsx` precisaram ser tocados. Se foram muitos, diga
quais valores não estavam tokenizados: isso é dívida a registrar.

Ação de operação pendente: subir o logo da Estrutural e preencher `logoUrl` na instância.
