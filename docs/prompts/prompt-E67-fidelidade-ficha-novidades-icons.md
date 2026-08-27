# Prompt E67 — fidelidade da ficha do lead, estados de ícones e Novidades

> Leia `AGENTS.md`. Entrega em 25/08.
> Esta etapa será executada pelo Antigravity em worktree/branch separado da E65. Não trabalhe no
> mesmo diretório em que outro agente está alterando a E65.
> Commite por bloco na sua branch. Não faça `git push` sem autorização explícita do Marcondes.
> Ao encerrar, rode `npm run lint`, `npm run typecheck`, `npm test -- --run` e `npm run build`.
> Se alterar backend, rode também `cd backend && ./mvnw clean verify`. Sem push, escreva `CI não verificado`.

---

## Contexto — referência visual e limites da etapa

As imagens anexadas e o HTML-modelo mostram três objetivos:

1. A ficha do lead deve se aproximar da referência: cabeçalho limpo, avatar e identificação
   centralizados, etapa do funil legível, informações com ícones, etiquetas compactas e ações no
   rodapé.
2. Os ícones precisam comunicar hover, foco, pressionamento e estado selecionado de maneira coerente.
   Ícones de exclusão/remover devem usar o tom semântico de erro/destrutivo.
3. A barra lateral deve ganhar “Novidades & Em Breve” e “Administração”, com a janela de novidades
   dividida nas abas “Novidades” e “Em breve”. A edição do conteúdo pela Administração será uma etapa
   futura; nesta etapa não existe contrato para persistir ou editar esses itens.

O repositório não está começando do zero. Os pontos reais são:

```tsx
// frontend/src/components/leads/painel-lateral-lead.tsx
<aside
  role="dialog"
  aria-modal="false"
  aria-label={textos.titulo}
  className="fixed inset-y-0 right-0 z-50 flex w-[min(30rem,100vw)] flex-col border-l border-border bg-background shadow-xl"
>
  <div className="flex items-center justify-between border-b border-border px-5 py-3">
    <h2 className="text-lg font-semibold text-foreground">{textos.titulo}</h2>
    <Button type="button" variant="ghost" size="icon" aria-label={textos.fechar} onClick={onFechar}>
      <X />
    </Button>
  </div>
```

Esse painel é aberto pela Agenda em `frontend/src/components/agenda/pagina-agenda.tsx`, que hoje passa
apenas `leadId` e `onFechar`. A Agenda já possui `abrirAtendimento(lead)` e navega para
`/atendimentos?leadId=...`; se a referência exigir um CTA de atendimento na ficha, passe essa ação de
forma explícita, sem criar um link fictício.

A ficha já possui funções que não podem desaparecer: salvar notas/campos customizados, criar lembrete,
programar mensagem, adicionar/remover tags, resumo por IA e timeline. Elas estão no mesmo
`painel-lateral-lead.tsx` e devem continuar acessíveis dentro do scroll.

Os botões de remoção existem em vários locais:

```tsx
// painel-da-conversa.tsx
<Button variant="ghost" ... aria-label={`${textos.atendimentos.painel.remover} ${item.conteudo}`}>
  <Trash2 className="size-3.5" aria-hidden />
</Button>

// painel-lateral-lead.tsx — remover tag
<Button type="button" variant="ghost" aria-label={textos.remover.replace("{nome}", tag.nome)}>
  <X className="size-3" />
</Button>
```

A barra lateral principal está em `frontend/src/components/shell/sidebar.tsx`, com os grupos de menu e
o rodapé do usuário. Hoje ela só possui link para `/configuracoes`; não há rota própria de
`/administracao` nem componente de novidades. `frontend/src/components/shell/placeholder.tsx` já é o
estado verdadeiro para uma área ainda não construída.

O catálogo atual começa assim:

```json
// backend/crm-app/src/main/resources/textos.json
"menu": {
  "grupoMenu": "Menu",
  "grupoGestao": "Gestão",
  "itens": { "atendimentos": "Atendimentos", "...": "..." }
}
```

O frontend valida esse contrato em `frontend/src/lib/config/schema.ts`. Strings novas não podem ser
escritas diretamente nos componentes.

## Regra de integração com a E65

A E65 está sendo executada em paralelo pelo Codex e também pode alterar `sidebar.tsx`,
`painel-da-conversa.tsx`, `pagina-atendimentos-cliente.tsx` e o catálogo de textos para retração de
sidebars e aviso. Portanto:

- trabalhe somente na sua worktree/branch isolada;
- não faça rebase, cherry-pick, merge ou push da E65;
- se a base mudar enquanto você trabalha, registre o SHA-base e entregue somente seus commits;
- conflitos nesses arquivos serão resolvidos depois, pelo Marcondes, com revisão da composição final;
- se um requisito da E65 for necessário para sua tela, pare e relate o ponto de integração, não copie
  código de outra worktree.

## Bloco 1 — fidelidade visual da ficha do lead

Ajuste `PainelLateralLead` para aproximá-lo da ficha das imagens, preservando a lógica e o conteúdo
real já existentes.

- O painel deve ser uma superfície branca/`bg-background` sobre a tela, com separação clara do fundo e
  cantos/arredondamento coerentes com a referência. Se adicionar backdrop, ele deve usar token
  semântico, não cor literal, e não pode impedir o botão de fechar nem o scroll da ficha.
- O cabeçalho deve manter “Ficha do lead”/texto do catálogo, botão de fechar acessível e alinhamento
  consistente. O botão X de fechar não é exclusão e não deve receber tom destrutivo só por ser um X.
- A identificação deve priorizar avatar, nome e empresa no topo. O avatar deve continuar usando a
  foto segura quando disponível e as iniciais/tom derivado quando não houver foto.
- A etapa do funil deve ficar visualmente próxima da referência: título, pílula da etapa atual,
  segmentos preenchidos conforme a ordem real, posição `{atual} de {total}` e nomes das extremidades.
  Não invente etapas, cores ou contagens; use os dados de `useEtapas` e tokens/cores já fornecidos pelo
  domínio. Se a etapa não existir, preserve o estado vazio real.
- Informações gerais devem manter telefone, e-mail, CPF, empresa, localização e canal quando
  disponíveis, com ícones alinhados e hierarquia tipográfica compacta. Campos nulos continuam ocultos
  ou com o estado vazio já definido pelo catálogo; não crie valores de demonstração.
- Tags devem continuar mostrando apenas dados reais. Deixe os chips legíveis e mantenha adicionar,
  remover, erro e reversão funcionando.
- As ações de lembrete e mensagem programada devem continuar funcionais. A referência não autoriza
  remover essas ações; se ficarem abaixo da dobra, devem estar no scroll e com agrupamento visual claro.
- Se implementar o rodapé visual da referência, ele deve ficar sticky somente dentro do painel e não
  cobrir o conteúdo. “Abrir atendimento” deve chamar o callback real da Agenda e o botão de telefone
  deve usar o telefone real com `tel:`; não renderize nenhum dos dois quando o dado/ação não existir.
- Notas, campos customizados, resumo IA e timeline permanecem acessíveis sem mudar contrato, payload,
  autorização ou regra de visibilidade.
- Confira desktop estreito e viewport menor: o painel não pode criar scroll horizontal nem esconder o
  botão de fechar.

> **Não transforme a referência em uma ficha fake.** Não substitua dados reais por “Camila Nunes”,
> “Negociação”, números, tags ou textos do screenshot. Esses valores são apenas referência visual.

> **Ponto de parada.** Se para copiar a referência for necessário remover edição, resumo, timeline,
> lembretes ou mensagens programadas, pare e relate a divergência; não decida sozinho qual capacidade
> do CRM será perdida.

## Bloco 2 — estados visuais dos ícones e exclusões em vermelho

Faça uma auditoria dos botões icon-only e dos ícones dentro de ações da ficha, painel da conversa,
composer e sidebar, sem alterar indiscriminadamente todos os botões do sistema.

- Estado normal: ícone e fundo coerentes com a superfície.
- Hover: feedback visível, discreto e consistente com `Button`/tokens já existentes.
- Foco por teclado: `focus-visible` claramente perceptível e nunca removido.
- Pressionamento/clique: o botão deve apresentar o estado ativo do componente sem depender de segurar o
  mouse. Para controles que representam seleção/toggle, use `aria-pressed` ou `aria-expanded` e faça
  o estilo refletir esse estado.
- Os ícones de excluir/remover/cancelar destrutivo devem usar `text-destructive` ou `variant="destructive"`
  conforme o contexto. Isso inclui, pelo menos, remover mensagem programada, remover lembrete, remover
  tag e descartar gravação/anexo quando a ação realmente destrói o rascunho. O X de fechar modal/painel
  e o X de limpar um campo não são automaticamente destrutivos.
- Use os componentes `Button`, `Tooltip` e tokens existentes. Não crie CSS global que deixe todo X do
  sistema vermelho e não use `#ff0000`, `#dc2626`, `rgb(...)` ou cor literal no JSX.
- Cada icon-only mantém `aria-label` do catálogo; tooltip é complemento visual, não substituto de
  acessibilidade.
- Não altere o comportamento de envio do composer, upload, áudio, tags ou confirmação de remoção.

## Bloco 3 — Novidades & Em breve no shell

Crie a experiência visual indicada nas imagens 3, 4 e 5, mas com fonte de texto configurável e sem
inventar backend.

### Entrada na sidebar

- Em `sidebar.tsx`, adicione na região inferior, antes do rodapé do usuário, um item “Novidades & Em
  Breve” com ícone de destaque e um item “Administração” com ícone de escudo e badge “ADM”, na ordem
  da referência.
- Use os rótulos e `aria-label`s do catálogo. Não espalhe strings no JSX.
- “Novidades & Em Breve” abre a janela/modal na própria tela, sem navegação desnecessária.
- “Administração” deve apontar para uma rota real (`/administracao`) que exiba explicitamente o estado
  de área futura usando `Placeholder` ou equivalente baseado no catálogo. Não crie botões de editar
  novidades que não salvam nada.
- Na ausência de uma política nova, preserve a mesma fronteira de acesso usada para áreas de gestão
  (`GESTOR` e `ADMINISTRADOR`). Se a imagem ou o código evidenciarem uma política diferente, pare e
  relate antes de criar autorização nova.
- O item não pode desaparecer com erro de `GET /api/v1/config/features`; são entradas centrais do shell,
  a menos que o contrato defina uma flag explícita posteriormente.

### Janela de novidades

- Use `Dialog`/componente acessível existente, com backdrop, fechamento por botão, tecla Escape e
  foco coerente. O modal deve respeitar a altura disponível e ter scroll interno, sem estourar a janela.
- Reproduza a estrutura da referência: controle segmentado com abas `Novidades` e `Em breve`, texto de
  introdução abaixo e conteúdo rolável.
- Aba `Novidades`: grupos por data em ordem decrescente; cada item tem estado `NOVO` quando informado,
  título e descrição.
- Aba `Em breve`: cards em grade responsiva; cada card tem ícone, título, descrição, status como “Em
  desenvolvimento”/“Planejado” e previsão quando configurada.
- O conteúdo inicial das imagens pode entrar em `textos.json` como catálogo estruturado, validado por
  `TextosSchema`, porque não existe endpoint nem persistência de novidades nesta etapa. Não use array
  mockado dentro do componente e não faça fetch para endpoint inexistente.
- Separe a camada de dados do renderer para que uma futura Administração possa trocar o catálogo por um
  endpoint/configuração sem reescrever a apresentação. Não implemente essa edição agora.
- Ícones dos cards devem vir do conjunto existente e ser mapeados por chave segura; não permita que
  texto de configuração seja executado como componente React.
- Cores de status e cartões usam tokens/tema. O badge `NOVO` deve ser semântico e não depender de cor
  literal.

> **Não chame isso de dado operacional.** Novidades são conteúdo editorial do produto, não leads,
> mensagens ou métricas. Mesmo assim, o texto deve vir do catálogo e não de JSX hardcoded.

> **Ponto de parada.** Se “configurado futuramente na Administração” exigir banco, migration, endpoint,
> autenticação nova ou contrato de edição, pare e devolva a decisão ao Marcondes. A E67 entrega a
> apresentação e o ponto de extensão, não inventa o modelo administrativo.

## Testes — a proteção nasce com um teste que a viola

### Ficha do lead

- renderiza nome, empresa, avatar/initials, etapa, posição, informações disponíveis e tags reais;
- campos nulos não criam texto ou valor do screenshot;
- botão de fechar chama `onFechar` por clique completo e permanece acessível;
- salvar notas/campos continua chamando o hook existente;
- abrir lembrete e mensagem programada continua abrindo os formulários;
- adicionar/remover tag mantém callbacks e estado de erro;
- se houver CTA, “Abrir atendimento” chama o callback real e telefone usa o número real;
- conteúdo longo permanece dentro do scroll do painel, sem overflow horizontal.

### Ícones

- botão icon-only possui `aria-label`;
- foco por teclado é visível;
- estado `aria-pressed`/`aria-expanded` acompanha toggle quando aplicável;
- após clique completo, o estado selecionado permanece sem exigir mouse pressionado;
- exclusão de mensagem programada, lembrete, tag e rascunho de áudio/anexo usa classe/variant
  destrutivo;
- fechar painel/modal e limpar campo não são pintados de vermelho por engano.

### Sidebar e novidades

- itens aparecem na ordem e região da referência para usuário autorizado;
- erro nas features não remove esses itens centrais;
- “Novidades & Em Breve” abre o modal, alterna abas e mantém somente uma aba ativa;
- modal fecha por botão, Escape e mecanismo padrão do componente;
- novidades aparecem agrupadas por data decrescente, sem item inventado pelo componente;
- cards “Em breve” são responsivos, sem overflow, e exibem status/previsão do catálogo;
- “Administração” navega para rota real e mostra estado futuro honesto;
- usuário sem o papel definido não recebe autorização nova por acidente; cobertura deve refletir a
  decisão documentada.

Faça uma captura visual ou validação manual em desktop comparando: ficha aberta, ícone de exclusão,
sidebar com os dois itens, modal em cada aba e rota Administração. Se a execução paralela impedir a
captura na base final da E65, registre que a comparação foi feita na branch isolada e forneça o viewport.

## Definição de pronto

- [ ] Ficha do lead se aproxima da referência sem perder nenhuma função existente.
- [ ] Dados exibidos continuam vindo de APIs/hooks reais; não há valores do screenshot hardcoded como
      dados de produção.
- [ ] Ícones têm estados de hover/foco/ativo coerentes e exclusões usam tom destrutivo semântico.
- [ ] “Novidades & Em Breve” aparece no shell e abre modal acessível com duas abas.
- [ ] Conteúdo editorial está no catálogo/schema, não em JSX nem em endpoint inventado.
- [ ] “Administração” tem rota real e estado futuro honesto, sem CRUD fantasma.
- [ ] Permissões seguem política existente ou a divergência foi parada e relatada.
- [ ] Testes unitários/componentes cobrem positivos e negativos descritos.
- [ ] `npm run lint`, `npm run typecheck`, `npm test -- --run` e `npm run build` passam.
- [ ] Se houver alteração backend, `cd backend && ./mvnw clean verify` passa com Java 21/Testcontainers.
- [ ] O relatório informa SHA-base e SHA final da branch, commits, variáveis novas no Dokploy
      (expectativa: nenhuma), decisões, divergências, bugs, fora de escopo e evidência visual.
- [ ] CI só é chamado de verde com número da run; sem push, registrar `CI não verificado`.

---

## Fora desta etapa

- Não implementar editor administrativo, banco, migration, endpoint ou feature flag para novidades.
- Não alterar backend, WebSocket, contratos da inbox ou regras de visibilidade.
- Não remover ou esconder notas, campos customizados, resumo IA, timeline, lembretes, mensagens
  programadas, tags ou ações de atendimento apenas para copiar o screenshot.
- Não incorporar alterações da E65 nesta branch nem trabalhar no mesmo diretório do Codex.
- Não commitar ou enviar os prompts não rastreados existentes automaticamente.
