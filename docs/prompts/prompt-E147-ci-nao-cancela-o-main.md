# Prompt E147 — O CI cancela a si mesmo no `main` e a imagem não é publicada

> Leia `AGENTS.md` e `CLAUDE.md`.
> Branch própria (`ci/nao-cancelar-execucao-do-main`) e PR. **Sem merge, sem deploy.**
> **Só `.github/workflows/ci.yml`.** Sem backend, sem frontend, sem migration.

Etapa pequena e de infraestrutura. Não há bug de aplicação aqui — o defeito é na regra de
concorrência do próprio CI, e o prejuízo é não existir imagem publicada para commits do `main`.

---

## O que aconteceu, em 03/09

Três PRs foram mergeados no `main` em ~2 minutos (`23e481a`, `b6cd27f`, `0329a00`). Os dois
primeiros terminaram com **"4 cancelled checks — Cancelled after 1m"**. Nenhum teste falhou:

```yaml
concurrency:
  group: ci-${{ github.workflow }}-${{ github.ref }}
  cancel-in-progress: true
```

Em push, `github.ref` é `refs/heads/main` para **todos** os merges. Todos caem no mesmo grupo de
concorrência, e cada merge novo mata o run do anterior.

O comentário acima dessa regra explica a intenção original, e ela é boa:

> Um push novo na mesma branch cancela o build anterior: o feedback que importa e o do ultimo
> commit, e a fila de runners e curta.

Isso vale para branch de feature, onde se empurra commit atrás de commit. **Em `main` vale o
contrário:** cada commit precisa de um veredito próprio.

## Por que isso dói de verdade

```yaml
imagens:
  if: github.event_name == 'push' && github.ref == 'refs/heads/main'
  needs: [backend, frontend, infra]
```

A imagem do GHCR só é publicada por um run **completo** no `main`. Run cancelado significa:

- **Não existe** `ghcr.io/synapse-auto/erp-matriz-synapse-{backend,frontend}:<sha>` para aquele
  commit. Apontar o `SYNAPSE_IMAGE_TAG` do Dokploy para ele falha, ou — pior — deixa a stack numa
  tag antiga sem ninguém perceber.
- O estado **combinado** do `main` nunca é validado. As branches estavam verdes isoladamente, mas
  ninguém rodou os testes sobre o merge das três, que é justamente onde duas migrations novas
  (V58 e V59) se encontram pela primeira vez.

## A correção

Manter o cancelamento onde ele serve e desligá-lo no `main`:

```yaml
concurrency:
  group: ci-${{ github.workflow }}-${{ github.ref }}
  cancel-in-progress: ${{ github.ref != 'refs/heads/main' }}
```

Atualize o comentário acima do bloco para dizer as duas metades da regra: cancela em branch de
feature porque o feedback que importa é o do último commit; **não cancela em `main` porque cada
commit precisa produzir imagem e um veredito próprio**.

## Cuidados

- `cancel-in-progress` aceita expressão, mas o resultado precisa ser booleano de verdade. Confirme
  que a sintaxe é válida — um valor que o GitHub interprete como string sempre-verdadeira reintroduz
  o bug silenciosamente. Se tiver dúvida, prove com a documentação e escreva a prova no PR.
- **Não mude o `group`.** Trocar a chave de concorrência muda o comportamento em branch de feature,
  que está correto e não é assunto desta etapa.
- Não mexa em nenhum outro job, nem nos `needs`, nem nas tags do `metadata-action`.
- Runs de `pull_request` continuam como estão.

## Fora do escopo

- Não adicione branch protection, required checks, merge queue nem qualquer política de merge.
  Se você achar que faz falta, **sugira no PR** e pare — é decisão do Marcondes.
- Não tente republicar imagens dos commits que ficaram sem build. Isso se resolve com o próximo
  push no `main`, ou manualmente, e não é código.

## Definição de pronto

- `cancel-in-progress` desligado apenas para `refs/heads/main`.
- Comentário do bloco explicando as duas metades da regra.
- O PR mostra o run próprio do CI concluindo — é a prova de que a sintaxe da expressão é válida.
- Relatório final com os sete itens do `AGENTS.md`.
