# Prompt E17 — Fidelidade visual ao protótipo aprovado

> Leia `AGENTS.md` e `design/TOKENS.md`.
> **Esta etapa reverte uma decisão errada do arquiteto.** Até aqui os agentes foram instruídos a tratar `design/componentes/*.html` como referência de layout. Estava errado: o protótipo foi **aprovado pelo cliente** e é especificação. As telas devem ficar iguais a ele.
> Blocos em ordem de prioridade. **Commite e faça push ao fim de cada bloco** — se o tempo acabar no meio, o que ficou para trás é o que o cliente vê menos.

---

## Método — leia antes de escrever qualquer código

Para **cada** tela, nesta ordem:

1. Abra o `.html` correspondente em `design/componentes/` e o componente React construído, lado a lado.
2. Produza uma **tabela de correspondência**: `elemento do protótipo | existe no construído? | dado disponível? | ação`. Percorra o HTML de cima para baixo; não trabalhe de impressão.
3. Só então implemente.
4. Anexe a tabela ao relatório. É por ela que a validação visual vai ser feita.

**Não trabalhe de memória e não "aproxime".** Se o protótipo tem um chip, é um chip. Se tem três colunas, são três colunas. Se o cabeçalho tem dois botões à direita, são dois botões à direita.

### Regras que continuam valendo

- **Cor sempre por token.** O protótipo usa hex; `AGENTS.md` proíbe literal. Traduza para as variáveis do `design/TOKENS.md`. Se faltar token para alguma cor do protótipo, **crie o token** e registre no `TOKENS.md` — não use hex.
- **Ícones em `lucide-react`.** O protótipo usa Remix; escolha o lucide de significado equivalente, não troque de biblioteca.
- **Zero dado mockado.** Onde o protótipo mostra número que não existe no backend, o Bloco 6 cria o endpoint. Enquanto o endpoint não existir, o elemento não existe — nunca um valor de exemplo.
- **Escopo da primeira entrega inalterado.** Dashboard, Relatórios, Campanhas e Banco de Arquivos continuam fora (`docs/09`). Não construa nem a casca.

---

## Bloco 1 — Sidebar

`design/componentes/Sidebar.html`. É o menor e aparece em todas as telas.

Confira, elemento por elemento: agrupamentos e seus rótulos, ordem dos itens, ícone de cada um, estado ativo, avatar e bloco do usuário no rodapé, seletor de presença, tipografia e espaçamento.

Os cinco ícones já corrigidos na E15b (`Headset`, `BookUser`, `MessageSquareText`, `Users`, `Bot`) permanecem — confira o restante contra o mapeamento do protótipo.

## Bloco 2 — Atendimentos

`design/componentes/Atendimentos.html`. É a tela aberta oito horas por dia. Se só uma ficar idêntica, é esta.

Divergências já levantadas na E15, para começar — **não pare nelas**, faça a tabela completa:

- abas de visão com **badge numérico** de contagem no topo da lista
- botões de nova conversa e de filtro no cabeçalho da lista
- selo de canal (WhatsApp) no avatar de cada item
- chips de tag inline nos cards da lista
- painel lateral direito: seções colapsáveis de resumo, lembretes, arquivos e programadas, na mesma ordem e com o mesmo comportamento de abrir/fechar

Os contadores das abas dependem do Bloco 6.

## Bloco 3 — Agenda

`design/componentes/Agenda.html`. A E16 já construiu tabela, filtros vindos do backend, chips removíveis e contador. Agora é igualar: colunas na mesma ordem, barra de filtros com o mesmo desenho, chips com o mesmo formato, densidade da tabela.

**Continuam fora:** toggle Lista↔Kanban e import/export CSV — sem endpoint dos dois lados. Registre os dois na tabela como "fora da primeira entrega", para irem ao cliente por escrito.

## Bloco 4 — As quatro tabelas cruas

`Equipe.html`, `Lembretes.html`, `MensagensRapidas.html`, `MensagensProgramadas.html`.

As quatro foram construídas como tabela HTML + Dialog. O protótipo usa cards com avatar, tinta por pessoa, pills de status coloridas e agrupamento visual. Iguale as quatro ao protótipo.

Se houver componente comum entre elas — card de pessoa, pill de status —, extraia para `components/ui/` em vez de repetir quatro vezes.

## Bloco 5 — Tags e Automação

`Tags.html`, `Automacao.html`. São as mais recentes e as de menor dívida.

- **Tags:** grid de cards em vez da tabela atual, e o mini-dashboard do topo (tag mais usada, % de leads tagueados, contagem por tag) — depende do Bloco 6.
- **Automação:** o desenho do protótipo para a seção Geral. As seções atrás da flag `automacao_regras` continuam ausentes, sem casca.

## Bloco 6 — Os endpoints de agregação

São o que falta para os elementos acima existirem sem inventar número. Todos são agregação sobre tabela existente; nenhum é módulo novo.

| Endpoint | Alimenta |
|---|---|
| contagem de leads por tag, tag mais usada, % de leads tagueados | mini-dashboard de Tags |
| contagem por visão (todos / pendentes / demais abas) | badges das abas de Atendimentos |
| leitura de `status_automacao_telemetria` | os quatro cards do topo da Automação |

**O ponto que não pode falhar:** toda contagem passa pela mesma visibilidade que a listagem. Um atendente não pode ver "47 leads com a tag Obra" quando ele enxerga 6 — contador é vazamento de informação sem devolver uma linha. Reaproveite o `visivel(filtro)` de `LeadRepositorioJpa`, não escreva query nova por fora.

**Escreva o teste negativo antes do endpoint:** contagem pedida por atendente devolve o número restrito; gestor devolve o total. Se algum já vier certo de graça, ótimo — diga no relatório, não force um achado.

## Definição de pronto

- [ ] Tabela de correspondência entregue **para cada tela**, elemento por elemento
- [ ] Blocos 1 a 5 iguais ao protótipo no que tem dado disponível
- [ ] Endpoints do Bloco 6 com teste negativo de visibilidade
- [ ] Nenhuma cor literal; tokens novos registrados em `TOKENS.md`
- [ ] Nenhum dado mockado; elementos sem fonte de dado listados por escrito
- [ ] Componentes repetidos extraídos para `components/ui/`
- [ ] Testes no padrão existente; CI verde; testes de integração **executados**, não só compilados
- [ ] Commit e push por bloco

Commit por bloco: `feat: fidelidade visual — <tela>`.

No relatório, além do formato de sempre: **a lista consolidada de elementos do protótipo que ficaram de fora e por quê.** Essa lista vai para o cliente por escrito — é ela que transforma "está diferente" em "está combinado".
