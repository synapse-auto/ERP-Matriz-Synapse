# Prompt E143 — Traços ficam verdes quando o cliente lê

> Leia `AGENTS.md`, `CLAUDE.md` e `docs/13-estado-do-projeto.md`.
> Branch própria (`feat/tracos-verdes-na-leitura`) e PR. **Sem merge, sem deploy.**
> **Somente frontend.** Um arquivo de produção mais tokens. Sem backend, sem migration.
> Suíte do `frontend/`, typecheck, lint e build.

Pedido do cliente: **quando o cliente lê a mensagem, os dois traços ficam verdes** — o
comportamento que todo mundo conhece do WhatsApp.

---

## O que existe hoje

`status-entrega.tsx` já distingue os quatro estados, e `LIDO` já se diferencia de `ENTREGUE` — mas
só pela **opacidade**, não pela cor:

```jsx
LIDO: { icone: <CheckCheck className="size-3.5 text-primary-foreground" ... > }
```

`PENDENTE`, `ENVIADO` e `ENTREGUE` herdam `text-primary-foreground/70` do rodapé do balão; `LIDO`
usa a mesma cor sem a opacidade reduzida. Funciona, mas é sutil demais — e não é o que o cliente
pediu.

## A armadilha, que já nos pegou uma vez — leia antes de escolher a cor

O balão de saída é sempre **`bg-primary` (azul)**. O comentário nas linhas 19–21 do próprio arquivo
registra o erro anterior: `text-destructive` é calibrado para fundo claro e **some** no balão azul;
foi preciso criar `--sidebar-item-texto-perigo` (`#E88B7D`) para o `FALHOU` ficar legível ali. O
`LIDO` também já foi `text-primary`, que era literalmente a cor do próprio balão — invisível.

`design/TOKENS.md:53` tem `--cor-sucesso: #17835A`. **Não use esse token aqui sem verificar.** Ele
é verde escuro, calibrado para "Finalizado, entregue" sobre fundo claro; sobre o azul do balão o
contraste é ruim.

O que fazer: escolha um verde que **leia sobre `bg-primary`**, no espírito do que foi feito para o
`FALHOU`. Se `TOKENS.md` não tiver um que sirva, **crie um token novo**, documente-o na tabela com
a mesma nota de calibragem dos vizinhos, e explique no comentário por que o `--cor-sucesso` não
serviu. Não chumbe hexadecimal no componente.

Conferir contraste é parte da tarefa, não detalhe: reporte a razão de contraste do verde escolhido
contra o azul do balão no relatório.

## Escopo

- Só `LIDO` muda de cor. `PENDENTE`, `ENVIADO`, `ENTREGUE` e `FALHOU` ficam exatamente como estão.
- O ícone continua `CheckCheck` — o que muda é a cor, não o desenho.
- Vale para o balão de saída na conversa. Não mexa no cartão da lista.

## Testes obrigatórios

1. `LIDO` renderiza `CheckCheck` com a classe do verde novo.
2. `ENTREGUE` continua **sem** a cor verde (o mesmo ícone, a opacidade herdada) — é este teste que
   impede o "dois traços iguais" voltar.
3. `ENVIADO` continua com `Check` simples.
4. `FALHOU` continua com `text-sidebar-item-texto-perigo` — sem regressão da E130.

## Fora do escopo

- Mexer em opacidade do rodapé, no horário ou no layout do balão.
- Mudar o `--cor-sucesso` existente ou onde ele já é usado.
- Qualquer outro estado de entrega.

## Definição de pronto

- Traços de `LIDO` verdes e legíveis sobre o balão azul.
- Cor vem de token documentado em `design/TOKENS.md`, não de hex no componente.
- Razão de contraste reportada.
- Testes, typecheck, lint e build verdes; `git diff --check` limpo.
- Relatório final com os sete itens do `AGENTS.md`.
