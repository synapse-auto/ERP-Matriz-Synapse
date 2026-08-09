# Prompt E15b — Automação mínima, corte de Horários e rastreabilidade honesta

> Leia `AGENTS.md`. Continuação da E15, que entregou `/tags` e o levantamento de divergências.
> Commite e faça push ao final de cada bloco.

---

## Contexto: o que a E15 descobriu

O levantamento estava certo e a verificação confirmou algo pior do que o relatado: `horario_trabalho` e `rotina_disponibilidade` aparecem **apenas** em `SchemaMigracoesIT`. Não existe `LocalTime`, `expediente`, `janelaAtendimento` nem qualquer noção de horário comercial em nenhum ponto do backend.

Ou seja: **o sistema hoje não tem conceito de horário de trabalho.** A disponibilidade do atendente é a presença manual (online/ausente/offline) construída na E02/E06. Isso funciona, mas é manual — ninguém "entra em expediente" sozinho às 08:00.

Este prompt aceita esse estado, corta o que não cabe no prazo e **para de mentir na documentação**.

---

## Bloco 1 — `/automacao`, versão mínima e honesta

Hoje existe `PUT /api/v1/automacao/config/{chave}`, mas **nenhum GET autenticado por JWT**. O único GET é `/internal/v1/automation-config`, protegido por `X-Synapse-Token` — o navegador não pode chamar. Editar às cegas não é tela.

**Construa:**

1. `GET /api/v1/automacao/config` autenticado por JWT, com a mesma autorização do PUT existente. Reaproveite o caminho de leitura que o contrato interno já usa; não duplique a query.
   A resposta deve trazer, por parâmetro: chave, valor atual, **faixa válida e unidade**. A tela não pode conhecer limite nenhum por conta própria — limite duplicado no cliente é limite que vai divergir.
2. A tela `/automacao`, lendo esse GET e escrevendo pelo PUT que já existe. Valor fora da faixa não pode ser salvável, e a mensagem de erro deve vir do backend.

**Fica de fora, com flag `automacao_regras` desligada:** CRUD de `regra_follow_up`, `regra_fidelizacao`, `mensagem_festiva` e `configuracao_resumo_ia`, os toggles de "Atendentes Disponíveis" e "Rotinas pré-definidas", e os quatro cards de telemetria do topo. Todos têm tabela e nenhum tem caso de uso; construir agora é módulo inteiro, não tela.

Sem dado mockado nos cards — se não existe leitura, o card não existe.

## Bloco 2 — `/horarios` sai do menu

Crie a feature flag `horarios`, **desligada por padrão**, e ponha o item de menu atrás dela em `sidebar.tsx`, no mesmo padrão de `campanhas` e `relatorios`.

Não é preguiça de esconder: item de menu visível é promessa, e um Placeholder numa homologação lê como produto quebrado. Melhor a funcionalidade não existir do que existir pela metade sem ninguém saber.

Registre o corte em `docs/09-escopo-primeira-entrega.md`, na mesma seção dos outros, com esta justificativa e esta consequência explícita:

> A disponibilidade do atendente é **manual** na primeira entrega. Ninguém entra em expediente automaticamente; cada um marca a própria presença. As tabelas `horario_trabalho` e `rotina_disponibilidade` permanecem no schema — a regra do `docs/09` de não cortar schema continua valendo.

**Isto precisa ser dito à subgestora na homologação.** Não é detalhe técnico: muda a rotina de quem usa.

## Bloco 3 — `docs/05` parou de ser confiável

É a **segunda vez** que a matriz de rastreabilidade marca ✅ em coisa que não existe — RF-CRM-57–64 na E14, agora RF-CRM-54. Documentação que mente é pior que documentação ausente, porque impede a pergunta.

Regenere `docs/05-rastreabilidade-requisitos.md` a partir do código, não da intenção. Regra nova, escrita no topo do arquivo:

> Um requisito só recebe ✅ com **evidência nomeada**: o controller ou serviço que o implementa, e o teste que o cobre. Sem as duas colunas preenchidas, o status é ❌ ou ⚠️ — nunca ✅.

Percorra os controllers reais e reescreva a matriz com as colunas `Requisito | Status | Implementação | Teste`. Tudo que você não conseguir apontar vira ❌, mesmo que "pareça" pronto. Espere encontrar mais mentiras além das duas conhecidas — procure ativamente.

## Bloco 4 — Os cinco ícones do sidebar

Troca barata e o sidebar é a primeira coisa que qualquer pessoa vê. Mantenha `lucide-react`; só escolha melhor:

| Item | Hoje | Trocar para | Motivo |
|---|---|---|---|
| Atendimentos | `Users` | `Headset` | é atendimento, não "pessoas" |
| Agenda de Contatos | `CalendarDays` | `BookUser` ou `Contact` | é cadastro, não calendário |
| Mensagens Rápidas | `Zap` | `MessageSquareText` | é resposta pronta, não automação |
| Equipe | `UserCog` | `Users` | é o time, não administração de usuário |
| Automação | `Workflow` | `Bot` | a tela é sobre IA |

Confira que nenhum ícone ficou repetido depois da troca — `Users` muda de dono aqui.

## Definição de pronto

- [ ] `GET /api/v1/automacao/config` por JWT, com faixa e unidade na resposta
- [ ] `/automacao` lendo e escrevendo, validação vinda do backend, sem card sem dado
- [ ] Flag `automacao_regras` criada e desligada
- [ ] Flag `horarios` criada e desligada; item fora do menu; corte registrado em `docs/09`
- [ ] `docs/05` regenerado com evidência nomeada; toda ✅ sem controller e teste virou ❌
- [ ] Cinco ícones trocados, nenhum duplicado
- [ ] Testes no padrão dos existentes; CI verde
- [ ] Commit e push

Commit: `feat: automação mínima, corte de horários e rastreabilidade por evidência`.

No relatório, item 4: **quantas linhas do `docs/05` você teve que rebaixar de ✅.** É o número que diz o tamanho real da dívida de documentação deste projeto.
