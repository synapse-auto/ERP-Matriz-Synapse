# Prompt E110 — Desativar a aba Novidades (temporário)

> Leia `AGENTS.md` e `CLAUDE.md`. Branch própria (`chore/desativar-novidades`) e PR.
> **Sem merge, sem deploy.** Só `frontend/`: `npm run lint && typecheck && test && build`.
> Sem backend, sem migration.

---

## O pedido

O cliente pediu para **desativar a Novidades por enquanto**. É temporário — vai voltar. Então a
entrega tem que ser fácil de reverter, e o `NovidadesDialog` e os testes dele **ficam onde estão**.

## Bloco 1 — São dois lugares, e eles não concordam entre si

Confirme lendo antes de mexer:

- **Desktop** — `frontend/src/components/shell/sidebar.tsx`, por volta da linha 312: um `<li>` com
  `<button>` e ícone `Sparkles`, rótulo `{textos.novidades?.titulo || "Novidades"}`.
- **Mobile** — `frontend/src/components/shell/navegacao-inferior.tsx`, por volta da linha 259: o
  mesmo botão dentro do menu "Mais", **guardado por `{textos.novidades?.titulo && (...)}`**.
- Os dois montam `<NovidadesDialog ...>` no fim do próprio componente.

Repare na assimetria: **o mobile some quando o texto não existe; o desktop cai num literal
`"Novidades"` cravado no código.** Isso já é um defeito por si só — o rótulo hardcoded fura os textos
da instância — e é o motivo de não dar para desativar só apagando o texto.

## Bloco 2 — Como desativar

**Unifique no guard que o mobile já tem, e desligue pelos textos da instância.**

- No desktop, o botão passa a renderizar **somente** quando `textos.novidades?.titulo` existir —
  mesma condição do mobile. Some o `|| "Novidades"` dos três lugares (`aria-label`, `title`, rótulo):
  se o botão só existe quando há título, o fallback não tem razão de ser.
- Com os dois guardados, desativar vira **remover o título nos textos da instância**, sem tocar em
  código. E reativar é devolver o texto — sem deploy, se os textos forem configuração.

Descubra onde `textos.novidades.titulo` é definido para esta instância e **diga no relatório**:

- se for configuração fora do build, faça só a mudança de código do guard e **indique exatamente o
  que o Marcondes precisa alterar** para desligar;
- se estiver cravado no repositório, remova o título nesta mesma etapa e diga em que arquivo.

**Não apague** o `novidades-dialog.tsx`, os testes dele, nem o estado `novidadesAberto`. O dialog fica
montado e simplesmente nunca abre. É o que torna a volta barata.

## Bloco 3 — O que não fazer

- Nada de comentar bloco de JSX. Comentário vira lixo permanente.
- Nada de `feature_flag` no banco para isto. A tabela existe, mas exige migration e um `INSERT` por
  instância — caro demais para algo que o cliente quer de volta em breve. Se você achar que vale, é
  decisão do Marcondes, não sua: **relate em vez de implementar**.
- Nada de mexer em Feedbacks, que é vizinho na mesma tela e **continua ativo**.

## Bloco 4 — Testes

- Sem `textos.novidades.titulo`: o botão **não** aparece nem no desktop nem no mobile.
- Com o título: aparece nos dois, e o dialog abre.
- `novidades-dialog.test.tsx` continua verde **sem ser editado**.
- Nenhum teste de sidebar/navegação passou a depender do literal `"Novidades"`.

## Verificação

```
npm run lint && npm run typecheck && npm run test && npm run build   # em frontend/
```

## Relatório

1. Onde `textos.novidades.titulo` é definido, e o que exatamente desliga a Novidades nesta instância.
2. Confirmação de que o `|| "Novidades"` cravado saiu dos três lugares.
3. Confirmação de que o dialog e os testes dele ficaram intactos.
4. Como se reativa, em uma linha.
