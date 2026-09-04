# Prompt E135 — Finalizar deixa o composer habilitado

> Leia `AGENTS.md`, `CLAUDE.md` e `docs/13-estado-do-projeto.md`.
> Branch própria (`fix/finalizar-fecha-o-composer`) e PR. **Sem merge, sem deploy.**
> **Somente frontend.** Sem backend, sem migration, sem endpoint novo.
> Suíte do `frontend/`: testes, typecheck, lint e build.

Regressão introduzida pela PR #71 (`c22251a`), já em produção. O atendente clica em **Finalizar**,
o atendimento é encerrado no backend, o cartão sai da lista — e a conversa **continua aberta com o
composer habilitado**. Ele consegue digitar num atendimento fechado.

---

## A causa, já isolada — não reinvestigue

A #71 resolveu, corretamente, o problema de a conversa fechar ao responder em Pendentes. Para
isso, `pagina-atendimentos-cliente.tsx` passou a guardar um snapshot do cartão selecionado:

```tsx
const conversa = conversaDaLista
  ?? (cartaoSelecionado?.leadId === leadSelecionadoId ? cartaoSelecionado : null);
```

e `atualizarAtendimentos` preserva o snapshot quando o cartão some da lista.

Isso é o comportamento desejado quando o atendimento apenas **troca de aba** (Pendentes → Ativos):
`WHERE_PENDENTES_PROPRIOS` é `WHERE_ATIVOS` mais "última mensagem é do LEAD", então Pendentes é
subconjunto de Ativos e o cartão reaparece.

Mas ao **finalizar** o cartão não reaparece em lugar nenhum: `FINALIZADO` não entra em `ATIVOS`,
nem em `PENDENTES`, nem em `POTENCIAIS`, e o atendente não tem a aba `Todos` desde a E125. O
snapshot sobrevive com o `status` velho, `atendimentoAtivoId` continua preenchido, e portanto:

```tsx
const atendimentoAtivo = atendimentoAtivoId ? { ...conversa!, ... } : null;
...
{atendimentoAtivo ? <Composer ... /> : <faixa "Atendimento finalizado.">}
```

cai no ramo errado. Nada limpa a seleção: `useFinalizarAtendimento` só invalida `["atendimentos"]`,
e o evento de revogação em tempo real é de **transferência**, não de finalização.

O teste que protegia isso foi invertido pela #71
(`"fecha a superfície da conversa quando o atendimento desaparece da visão"` virou
`"mantém a conversa aberta..."`). Os testes de finalizado que restaram sempre reinjetam o cartão
`FINALIZADO` na lista via `atualizarLista([...])` — situação que não acontece para atendente em
produção, porque o cartão simplesmente sai.

---

## O que fazer

**Não** volte a fechar a conversa quando o cartão some da lista — isso reintroduz o bug da #71.
A conversa deve continuar aberta e legível; o que precisa mudar é o **estado** do snapshot.

`useFinalizarAtendimento` já devolve `AtendimentoResumo` no `onSuccess`. Propague esse sucesso do
`cabecalho-conversa.tsx` até a página e envelheça o snapshot:

```tsx
setCartaoSelecionado((atual) =>
  atual ? { ...atual, status: "FINALIZADO", atendimentoAtivoId: null } : null);
```

Com isso `atendimentoAtivo` vira `null`, o composer dá lugar à faixa "Atendimento finalizado." e o
botão de abrir novo atendimento aparece — o mesmo comportamento que o gestor já vê hoje na aba
`Todos`, onde o cartão permanece na lista com o status atualizado.

Prefira uma prop de callback explícita (`onAtendimentoFinalizado`) a espalhar estado global. Se
existir um caminho mais limpo dentro do padrão de TanStack Query já usado na tela, use-o — mas
justifique no relatório.

---

## Testes obrigatórios

1. **A regressão:** cartão aberto → finalizar com sucesso → `atualizarLista([])` → o composer
   **não** está no documento, a faixa "Atendimento finalizado." está, e a conversa continua montada
   (cabeçalho e histórico presentes).
2. **Não reintroduzir o bug da #71:** cartão pendente aberto → envio bem-sucedido →
   `atualizarLista([])` → composer **presente**, conversa aberta, visão troca para `ATIVOS`.
3. **Falha ao finalizar** não altera nada: composer segue habilitado, conversa aberta.
4. O caso do gestor (cartão `FINALIZADO` continua na lista) segue funcionando como hoje.

---

## Dívida de teste que esta etapa deve pagar

Os testes de `pagina-atendimentos-cliente.test.tsx` mockam `ListaConversas` inteira e simulam o
envio com um botão que chama `onMensagemEnviada` direto, pulando `useEnviarMensagem`. Por isso
**nenhum teste prova** que um envio real dispara a troca de aba, nem que uma falha real não
dispara — e o teste "mantém conversa e visão Pendentes quando o envio falha" apenas deixa de
chamar o callback e verifica que nada mudou, o que é tautológico.

Nesta etapa, faça pelo menos o teste de falha exercitar o caminho real: mock da API de envio
rejeitando, e asserção de que a visão continua `PENDENTES` e o composer preservado. Se isso exigir
desmontar o mock de `ListaConversas`, é aceitável — descreva a escolha no relatório.

---

## Fora do escopo

- Reordenar abas, mexer em visibilidade por papel, RN-CRM-01/02/06, RLS ou permissões.
- Backend, endpoint novo, migration.
- O comportamento de transferência/revogação, que já fecha a conversa corretamente.

## Definição de pronto

- Finalizar encerra o composer sem fechar a conversa.
- O comportamento da #71 (responder em Pendentes) continua intacto e coberto por teste.
- Existe teste que exercita a falha de envio pelo caminho real, não pelo callback.
- Testes, typecheck, lint e build do frontend verdes; `git diff --check` limpo.
- Relatório final com os sete itens do `AGENTS.md`.
