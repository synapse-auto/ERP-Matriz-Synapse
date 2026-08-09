# Prompt E15 — Telas faltantes e fidelidade visual

> Leia `AGENTS.md` e `design/TOKENS.md` antes de começar.
> Pré-requisito: nenhum. Roda em paralelo à E14b, porque não toca em infraestrutura.
> Commite e faça push ao terminar cada tela.

---

**Etapa E15 — Fechar os três buracos do menu e aproximar o visual do protótipo.**

## Diagnóstico

`ITENS_MENU` e `ITENS_GESTAO` em `frontend/src/components/shell/sidebar.tsx` listam nove entradas sem feature flag — ou seja, sempre visíveis. Destas, **três caem no catch-all `app/(shell)/[...slug]` e renderizam o `Placeholder`**:

| Rota | Estado | Protótipo de referência |
|---|---|---|
| `/tags` | Placeholder | `design/componentes/Tags.html` |
| `/automacao` | Placeholder | `design/componentes/Automacao.html` |
| `/horarios` | Placeholder | `design/componentes/Horarios.html` |

As demais (`/atendimentos`, `/agenda`, `/equipe`, `/lembretes`, `/mensagens-rapidas`, `/mensagens-programadas`) têm tela própria.

Isso importa porque a homologação vai para a subgestora e **item de menu visível é promessa**. Ela vai clicar nos três, e "esta área ainda não foi construída" numa entrega de homologação lê como produto quebrado, não como recorte de escopo.

O `Placeholder` está certo como padrão — é melhor que dado mockado, e essa decisão continua valendo. O que não está certo é ele cobrir três rotas que **estão no escopo da primeira entrega** (`docs/09`).

## 1. As três telas

Backend já existe para as três; nenhuma migration nova, nenhum endpoint novo — **se faltar endpoint, pare e me avise antes de criar**, porque é sinal de que a rastreabilidade mentiu de novo (já aconteceu com RF-CRM-57–64).

- **`/tags`** — CRUD de tags: criar, renomear, cor, arquivar. Referência de comportamento: o `atalho-tags.tsx` já consome a API.
- **`/automacao`** — leitura e edição de `configuracao_automacao`. **Toda faixa válida vem do backend**, não replicada no front: um limite duplicado no cliente é um limite que vai divergir. Campo fora da faixa não pode ser salvável.
- **`/horarios`** — janela de atendimento por dia da semana. É o que alimenta a regra de 08:00–18:30; deixe explícito na tela qual janela está ativa hoje.

Sem dado mockado em nenhuma. Se um endpoint não existir, `Placeholder` continua sendo a resposta honesta — mas me avise.

## 2. Fidelidade visual

Os arquivos em `design/componentes/` são o protótipo do Claude Design: HTML estático com dado falso. **Não são especificação para clonar** — são referência de layout, hierarquia e vocabulário. `design/TOKENS.md` é o que manda em cor, tipografia e espaçamento.

Duas divergências conhecidas, em ordem de importância:

**Densidade e hierarquia.** O protótipo tem estrutura que o construído simplificou — painéis laterais com seções colapsáveis, agrupamentos, estados vazios ilustrados. Compare cada tela existente com o `.html` correspondente e **liste as divergências antes de corrigir qualquer uma.** Quero ver a lista e decidir o que vale o tempo; não corrija tudo por conta própria.

**Ícones.** O protótipo usa Remix Icon (`ri-*`); o construído usa `lucide-react`. Não troque a biblioteca — lucide já está integrado e a troca é risco sem retorno. O que vale é conferir se o ícone escolhido comunica a mesma coisa que o do protótipo, um a um no sidebar.

Não invente tela nova, não mude token sem registrar em `design/TOKENS.md`, e não introduza dependência de UI nova.

## 3. O que NÃO entra

Continuam fora, por `docs/09`: Dashboard, Relatórios, Campanhas, Banco de Arquivos. Elas já somem do menu por feature flag e devem continuar sumindo — **não construa nem "só a casca"**.

## Definição de pronto

- [ ] `/tags`, `/automacao`, `/horarios` funcionais, sem mock, consumindo os endpoints existentes
- [ ] Faixas de validação da Automação vindas do backend, não duplicadas no front
- [ ] Catch-all `[...slug]` cobrindo **apenas** rotas de features desligadas
- [ ] Lista escrita das divergências visuais encontradas, tela por tela — **entregue a lista, não as correções**
- [ ] Testes de componente no padrão dos existentes (`*.test.tsx`)
- [ ] CI verde
- [ ] Commit e push

Commit: `feat: telas de tags, automação e horários`.

No relatório, item 3 (decisões não especificadas): me diga se algum endpoint que você esperava encontrar não existia. É o sinal mais barato de que a documentação divergiu do código outra vez.
