# Prompt E34 — refinamento visual da Agenda e do composer

> Leia `AGENTS.md`, `design/TOKENS.md` e `frontend/AGENTS.md` antes de começar.
> Entrega em 25/08.
> Esta etapa é de frontend. **Não faça commit nem push sem autorização explícita do Marcondes.**
> Ao encerrar, informe os testes executados, o diff e o que ficou sem verificação.

---

## Referências — o anexo é modelo visual, não código executável

Use como referência visual:

- `C:\Users\marcondes\Downloads\CRM_EstruturalVidros_App (2).html`
- captura da Agenda com filtro de Tags aberto: `codex-clipboard-6d772002-17f3-4039-8503-b35659a5677c.png`
- captura repetida da Agenda: `codex-clipboard-edbe8f79-7b69-4570-97be-f77b9967aa88.png`
- captura do atendimento com o composer destacado: `codex-clipboard-5ef2d29a-c74d-49a6-a14c-f518bd607c48.png`

O HTML contém um protótipo com template engine próprio (`dc-import`, `sc-for`, `sc-if`) e estilos
inline. **Não execute, importe ou copie esse código para o Next.js.** Extraia somente hierarquia,
densidade, espaçamento, estados e comportamento visual. A implementação continua em React,
Tailwind, `lucide-react`, catálogo de textos e tokens do tema.

## Contexto — a funcionalidade existe, mas a superfície construída ainda não parece o protótipo

A Agenda real está nestes arquivos:

```text
frontend/src/components/agenda/pagina-agenda.tsx
frontend/src/components/agenda/barra-de-filtros.tsx
frontend/src/components/agenda/tabela-de-leads.tsx
frontend/src/components/ui/seletor-multiplo.tsx
```

Hoje, a página começa com um contêiner único `p-6`, a barra de filtros é um card próprio:

```tsx
// frontend/src/components/agenda/pagina-agenda.tsx
<div className="flex h-full flex-col overflow-hidden p-6">
  <header className="mb-4 flex-none">...</header>
  <div className="flex-none">
    <BarraDeFiltros ... />
  </div>
  <div className="min-h-0 flex-1 overflow-y-auto">...</div>
</div>
```

```tsx
// frontend/src/components/agenda/barra-de-filtros.tsx
<div className="mb-4 space-y-3 rounded-lg border border-border bg-card p-3.5">
```

O protótipo separa as superfícies: cabeçalho branco com título/subtítulo, barra de filtros direta
na faixa superior, corpo em `--fundo-app` e tabela branca com raio maior. A referência usa busca de
380px por 40px, controles de 40px, dropdown com opções em linhas de 18px e tabela com avatar de
40px, padding horizontal de 22px e chips compactos.

O composer real está em:

```text
frontend/src/components/atendimentos/composer.tsx
frontend/src/components/atendimentos/pagina-atendimentos-cliente.tsx
frontend/src/components/ui/textarea.tsx
```

Hoje ele é um rodapé de largura inteira com `border-t` e o `Textarea` mantém a borda padrão do
componente compartilhado:

```tsx
// frontend/src/components/atendimentos/composer.tsx
<div className="border-t border-border p-3">
  ...
  <div className="flex items-end gap-2">
    ...
    <Textarea className="max-h-32 resize-none" />
    ...
  </div>
</div>
```

No protótipo, o composer é um cartão branco centralizado, com largura máxima de 780px, borda
`--borda-forte`, raio de aproximadamente 14px, sombra azulada discreta, textarea sem borda e uma
barra de ações inferior. A captura enviada mostra exatamente essa diferença — o campo fica
contido, respirando dentro do canvas, em vez de ser uma faixa desenhada de lado a lado.

A consequência é visual, mas não é cosmética: o atendimento é a aba que fica aberta o dia inteiro.
O refinamento não pode quebrar envio, áudio, upload, mensagens rápidas, agendamento, janela de 24h,
estado finalizado ou scroll interno.

## Bloco 1 — auditar a correspondência antes de alterar o JSX

Antes de escrever código, compare o modelo e a implementação real, de cima para baixo, e entregue no
relatório uma tabela curta com:

```text
elemento do modelo | arquivo/componente atual | existe? | dado/ação real disponível | decisão
```

Faça a tabela para:

- cabeçalho e barra de filtros da Agenda;
- dropdown de Tags aberto;
- card/tabela e uma linha de lead;
- rodapé/composer do atendimento.

Inclua na tabela os elementos que **não** podem entrar nesta etapa:

- `Importar CSV` e `Exportar CSV`;
- alternância `Lista`/`Kanban`;
- Banco de Arquivos compartilhado.

Eles aparecem no HTML, mas `docs/09-escopo-primeira-entrega.md` e `docs/17-plano-de-fechamento.md`
os mantêm fora porque não há contrato funcional dos dois lados. **Não construa a casca só para a
captura ficar parecida.**

> **Ponto de parada.** Se para reproduzir algum elemento visual você precisar criar endpoint,
> inventar contagem, adicionar dado mockado ou mudar o contrato de Agenda/Atendimento, pare e relate
> antes de escolher outro caminho. Esta etapa não autoriza reabsorver escopo.

## Bloco 2 — Agenda: superfície, filtros e tabela

Refine a apresentação da Agenda em:

```text
frontend/src/components/agenda/pagina-agenda.tsx
frontend/src/components/agenda/barra-de-filtros.tsx
frontend/src/components/agenda/tabela-de-leads.tsx
frontend/src/components/ui/seletor-multiplo.tsx  (somente se a comparação provar necessidade)
```

Requisitos:

- separar visualmente cabeçalho, canvas e tabela: o título continua vindo de `useTextos`, o topo usa
  fundo de superfície, o corpo usa o token de canvas/app e a tabela permanece uma superfície branca;
- aproximar título, subtítulo, altura, bordas e espaçamento do modelo sem inserir strings no JSX;
- remover a aparência de “card dentro de card” da barra de filtros: busca e seletores ficam numa
  faixa compacta, com a mesma hierarquia do modelo;
- busca com ícone, 40px de altura, raio de controle e largura mínima coerentes com o protótipo;
- seletores de Etapa, Atendente, Cidade e Tag com altura/raio/padding consistentes entre si;
- estado selecionado continua visível por token (`--cor-primaria-suave`/`--cor-primaria-borda`),
  sem cor literal e sem perder o contador de seleções;
- dropdown aberto deve ficar sobre a tabela, ter largura ancorada ou mínima suficiente, sombra,
  raio, foco de teclado e rolagem interna; clicar em uma opção não pode fechar ou reabrir de forma
  errática;
- checkbox visual do dropdown deve seguir o protótipo: caixa compacta, estado selecionado claro,
  linha com hover e label truncável;
- **não inventar números por opção**: hoje `CatalogosDeFiltro` entrega cidades e tags, mas não
  entrega contagem por opção. Se o modelo mostra `3`, `4`, etc. e o backend não fornece isso,
  deixe o número de fora e registre a divergência; não derive da página de 50 leads nem use número
  fixo;
- chips ativos devem seguir a proporção do modelo: altura compacta, fundo suave, borda suave,
  botão de remoção acessível e `Limpar tudo` discreto; preservar a composição de filtros existente;
- tabela com raio de card do token, cabeçalho de baixo contraste, separadores discretos e densidade
  próxima ao modelo. Preserve semântica de tabela e não troque por dados client-side;
- avatar da linha deve manter 40px, raio e cor por token/implementação existente; nome e empresa
  devem conservar truncamento sem estourar colunas;
- telefone continua em fonte monoespaçada e cidade em texto secundário;
- etapa/status continua usando a cor que vem do backend ou token semântico, não uma cor literal;
- tags devem ser compactas e não ocupar a linha inteira: mostre no máximo as primeiras duas quando
  houver mais, com `+N` para o restante. Só mostre ícone se houver mapeamento real para o campo
  `icone`; não crie ícones de tag fictícios;
- responsável deve seguir a hierarquia do modelo: avatar de iniciais mais nome quando existir;
  `Sem responsável` permanece texto acessível e em itálico, sem avatar inventado;
- último contato deve continuar vindo de `ultimaInteracaoEm`. Se for convertido para formato
  relativo, use uma API de data/localização determinística e não strings literais como “Há 12 min”
  no componente; se o catálogo não suportar o formato, mantenha o formato atual e relate a
  divergência;
- estado vazio, carregamento, erro, paginação, clique simples na ficha e duplo clique no atendimento
  continuam existindo e não podem ser escondidos para melhorar a captura;
- mantenha scroll interno da área de dados; a barra de filtros e o cabeçalho não devem rolar junto
  com as linhas.

> **Não faça:** transformar a tabela em uma coleção de cards, remover a paginação, carregar todos os
> leads no browser, ou alterar a Specification/endpoint para obter um resultado visual.

## Bloco 3 — composer: cartão centralizado sem regressão de comportamento

Refine `frontend/src/components/atendimentos/composer.tsx` e, somente se necessário, o contêiner
em `frontend/src/components/atendimentos/pagina-atendimentos-cliente.tsx`.

Requisitos visuais:

- o rodapé deve respirar sobre o canvas, sem uma faixa visual pesada de `border-t` atravessando toda
  a coluna;
- criar um contêiner interno centralizado, com `max-width` equivalente a 780px, fundo de superfície,
  borda forte, raio de card/controle e sombra tokenizada discreta;
- textarea sem borda própria, sem dupla borda, com altura mínima próxima de 44px, crescimento até o
  limite atual e foco visível acessível;
- manter placeholder e todos os textos vindos de `useTextos`; não copiar o texto do HTML para o
  componente;
- barra de ações inferior compacta, com os controles agrupados como no modelo: anexo, respostas
  rápidas, agendamento e emoji à esquerda; microfone e envio à direita;
- usar `lucide-react` com equivalentes semânticos (`CirclePlus`/`Paperclip`, `Zap`, `Clock`,
  `Smile`, `Mic`, `Send` ou equivalentes já adotados). Não adicionar Remix Icon para copiar o HTML;
- botão enviar mantém o tamanho, cor primária, hover, sombra e estados disabled/pending atuais;
- o controle de `Respostas rápidas` só pode aparecer como botão se abrir uma lista alimentada por
  `listarMensagensRapidas(true)` e inserir a resposta escolhida no textarea. Não criar botão que só
  decora a captura. O atalho `/` e a navegação por teclado continuam funcionando;
- anexo, gravação de áudio, preview, progresso, remoção, erro, agendamento e emoji continuam
  funcionais. O rearranjo visual não pode mudar payload nem chamada de API;
- quando a janela de 24h estiver fechada, o estado informativo continua honesto e não ganha composer
  falso;
- quando o atendimento estiver finalizado, o estado final continua sendo o texto de encerramento,
  sem botões desabilitados parecendo disponíveis;
- dropdown de respostas rápidas e sugestões devem abrir para cima sem ficar atrás da lista de
  mensagens, painel lateral ou canvas.

> **Não faça:** alterar `frontend/src/components/ui/textarea.tsx` globalmente só para este caso sem
> conferir todos os consumidores. Prefira classes no uso do composer; se o componente compartilhado
> precisar mudar, teste login, formulários e todas as telas que usam `Textarea`.

> **Não faça:** remover o botão de anexo porque o Banco de Arquivos está fora. Upload direto de foto,
> áudio e documento no chat permanece no escopo (`docs/09`).

## Bloco 4 — verificação visual e regressão

Faça a verificação no mesmo viewport das referências quando o ambiente permitir (`1920x1080` para a
Agenda e o viewport da captura do atendimento para o composer). Tire capturas antes/depois fora do
commit ou anexe-as ao relatório; não adicione imagens temporárias ao repositório.

Compare pelo menos:

- Agenda sem filtro aberto;
- Agenda com o dropdown de Tags aberto;
- Agenda com chips ativos;
- Atendimento com composer vazio;
- Atendimento com sugestão de mensagem rápida aberta;
- Atendimento em gravação/preview de áudio e com anexo selecionado.

Se a homologação não tiver dados suficientes para alguma captura, registre o bloqueio. Não semeie
mock só para produzir screenshot.

## Testes — a proteção nasce com um teste que a viola

Atualize ou crie testes no padrão existente:

- `frontend/src/components/agenda/pagina-agenda.test.tsx`: filtros, chips, contador, estado vazio,
  clique simples, duplo clique e paginação continuam passando após a mudança de layout;
- teste da tabela (novo `tabela-de-leads.test.tsx`, se ainda não existir): responsável com avatar,
  `Sem responsável`, duas tags mais `+N`, etapa/status, telefone monoespaçado e último contato;
- `frontend/src/components/atendimentos/composer.test.tsx`: placeholder, envio por Enter, atalho de
  mensagem rápida, anexo, microfone, agendamento, emoji, estado de erro e estados finalizado/janela
  fechada;
- **negativo:** não deve haver botão de CSV/Kanban/Banco de Arquivos nesta etapa, não deve haver
  contagem fictícia no dropdown e nenhuma ação visual nova pode disparar chamada de API que não
  exista;
- **negativo de regressão:** abrir dropdown, selecionar filtro, abrir sugestão rápida ou focar o
  textarea não pode mover o scroll global nem esconder a lista de mensagens;
- se criar um botão visível de respostas rápidas, teste que ele abre dados reais e que escolher um
  item altera o texto sem enviar automaticamente;
- execute `npm run lint`, `npm test` e, se o ambiente estiver disponível, `npm run test:e2e` com
  `Awaitility`/polling equivalente nos efeitos assíncronos — nunca `Thread.sleep`;
- por política do projeto, tente também `cd backend && ./mvnw clean verify`; se não executar, informe
  exatamente o motivo e quais testes ficaram fora.

## Definição de pronto

- [ ] Tabela de correspondência entre protótipo e código entregue no relatório.
- [ ] Agenda com hierarquia, densidade, filtros, dropdown, chips, tabela e scroll coerentes com as
      referências, sem criar controles fora do escopo.
- [ ] Dropdown não exibe contagens inventadas; qualquer ausência de dado do backend está registrada.
- [ ] Linhas exibem dados reais, avatar de responsável quando disponível, tags compactas e estado
      vazio honesto.
- [ ] Composer é um cartão centralizado e sem dupla borda, com controles equivalentes ao modelo.
- [ ] Envio, áudio, upload, emoji, agendamento, mensagens rápidas, janela de 24h e finalização não
      regrediram.
- [ ] Nenhuma string ou cor nova foi hardcoded; tokens e catálogo permanecem a fonte.
- [ ] Testes unitários/frontend e capturas de verificação foram executados ou tiveram o bloqueio
      explicitado.
- [ ] Nenhum endpoint, migration ou contrato de backend foi alterado nesta etapa.
- [ ] Nenhum commit ou push foi feito sem autorização explícita.

## No relatório

1. Tabela de correspondência visual, incluindo itens deliberadamente fora.
2. Arquivos alterados e por que cada um foi tocado.
3. Comparação antes/depois por viewport; registre divergências que permaneceram.
4. Confirmação de que não houve dado mockado, endpoint novo, cor literal ou string de UI fora do
   catálogo.
5. Testes executados com números e resultado; diga quais não rodaram.
6. Decisões tomadas sozinho, especialmente sobre raio, largura do composer, ordem dos controles e
   formato do último contato.
7. Variável nova no Dokploy: expectativa **nenhuma**.
8. Commit/push: expectativa **não executar sem autorização explícita**; se autorizado depois,
   informe SHA, branch, confirmação no `origin` e quantidade de arquivos.

---

## Fora desta etapa

- Importação/exportação CSV e o motor correspondente.
- Kanban, drag-and-drop e agrupamento por etapa.
- Banco de Arquivos compartilhado; permanece apenas upload direto no chat.
- Novo endpoint de contagem por opção de filtro.
- Mudança no backend, no schema, na autenticação, no WebSocket ou no contrato de mensagens.
- Configuração de aparência para o usuário final; design tokens continuam sendo configuração da
  instância, não controles novos na tela.
