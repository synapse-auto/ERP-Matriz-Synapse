# Prompt E129 — Chat interno no menu lateral

> Leia `AGENTS.md` e `CLAUDE.md`. Branch própria (`fix/chat-interno-no-menu`) e PR.
> **Sem merge, sem deploy.** Só `frontend/`. Sem backend, sem migration, sem contrato.
> Verificação proporcional: **suíte do frontend, sem Maven.**

---

## O problema

A rota `/chat-interno` está **órfã**. A página `PaginaChatInterno` existe, está completa e ganhou na
E122 os diálogos de grupo (`DialogoCriarGrupo`, `PainelParticipantesGrupo`) — mas **nenhum item de
menu aponta para ela**, em nenhum papel.

Consequência prática, medida em produção: **não existe forma de criar um grupo do chat interno pela
interface.** O botão "Novo grupo" e o painel de participantes só existem nessa página, e o único
jeito de chegar nela hoje é digitar a URL na barra do navegador.

O único caminho até o chat interno na interface é a **inbox unificada**, e ela só carrega conversas
internas na aba **Todos** (`useAtendimentos` só chama `/api/v1/atendimentos/inbox` quando
`visao === "TODOS"`; o `@Parameter` do `InboxUnificadaController` diz textualmente *"conversas
internas só aparecem em TODOS"*). E pela inbox só dá para abrir conversa **direta** — nada de grupo.

Confirme os três fatos acima antes de escrever código, e diga no relatório.

---

## O que fazer

Quase tudo já existe. Esta etapa liga as pontas.

**1. `frontend/src/lib/navegacao/itens-do-menu.ts`** — acrescente a `ITENS_MENU`:

```ts
{ chave: "chatInterno", rota: "/chat-interno", flag: "chat_interno" },
```

Posição: logo depois de `lembretes`, antes de `feedbacks`.

A flag é **a mesma** que `pagina-atendimentos-cliente.tsx` já consulta
(`flags?.includes("chat_interno")`). Não crie flag nova, não invente nome novo, não deixe sem flag —
o chat interno é um módulo opcional da Base PAI e tem que poder ser desligado por filho como os
outros.

**2. `ICONES_MENU` em `sidebar.tsx`** — mapeie `chatInterno`. Escolha um ícone do `lucide-react` que
**ainda não esteja em uso** no menu (`MessageSquareText` já é do `mensagensRapidas`,
`MessageSquarePlus` já é do `feedbacks`) e diga qual escolheu no relatório.

**3. `itemDeMenuVisivel`** — **não** ganha regra de papel para essa chave. O chat interno é de todo
mundo: atendente, subgestor, gestor e administrador. Só a flag decide.

**4. O rótulo já existe** em `textos.json`: `menu.itens.chatInterno` = `"Chat interno"`.
**Não edite `textos.json`.** Se você precisar criar chave de texto, leu algo errado.

---

## O que NÃO fazer

- Não mexa na inbox unificada, no `useAtendimentos`, nem em `lista-conversas.tsx`. O chat interno
  continua aparecendo na aba Todos exatamente como hoje; ter dois caminhos é aceitável e é o estado
  desejado.
- Não mexa na `PaginaChatInterno`, nos diálogos de grupo, nem em nada que a E122 entregou. A página
  funciona — o que faltava era a porta.
- Não mexa em `ITENS_GESTAO`. Chat interno não é item de gestão.
- Nenhum arquivo de backend. Se você abrir um `.java`, parou de fazer esta etapa.

---

## Testes

- Com a flag `chat_interno` **habilitada**: o item "Chat interno" aparece na sidebar, aponta para
  `/chat-interno`, e aparece para **atendente e para gestor** — os dois casos, não só um.
- Com a flag **ausente**: o item não é renderizado. Este é o teste que prova que a flag foi
  respeitada, e o lugar natural dele é o teste de `itemDeMenuVisivel`.
- Navegar para `/chat-interno` marca o item como ativo na sidebar (`pathname.startsWith(item.rota)`
  já faz isso — garanta que não quebrou).
- A navegação inferior do celular (`navegacao-inferior.tsx`, `CHAVES_ABA_INFERIOR`) **não** muda:
  continuam `atendimentos`, `dashboard`, `agenda`. Se um teste dela quebrar, você mexeu demais.
- Os testes existentes da sidebar continuam verdes; se algum contava itens do menu, corrija a
  contagem e diga no relatório de quanto para quanto.

## Verificação

Suíte do `frontend/`. **Sem Maven** — nenhum arquivo de backend foi tocado.

## Relatório

1. Os três fatos do diagnóstico, confirmados no código com arquivo e linha.
2. Qual ícone você escolheu e por que ele não colide com nenhum outro do menu.
3. A contagem de itens do menu, se algum teste dependia dela.
4. Confirmação de que nenhum arquivo de backend e nenhum texto de `textos.json` foi tocado.
