# Prompt E13 — CRUDs de suporte e gaps do composer

> Pré-requisito: E12 commitada (`5dffbb8`). **Sessão limpa.**
> Leia `AGENTS.md` por inteiro antes de começar.
> Etapa larga (~1,5 dia). Se o contexto ficar longo, commite por bloco.

---

**Etapa E13 — O que falta para o atendente trabalhar um dia inteiro sem tropeçar.**

Cinco blocos independentes. Cada um pode ser commitado sozinho.

---

## Bloco 1 — Lembretes (`RF-CRM-57` a `60`)

CRUD completo, back e front.

- Criados a partir da aba lateral do lead **e** da aba de Lembretes
- **Privados por atendente** (`RN-CRM-04`): cada um vê os seus; gestor vê todos, com coluna indicando de quem é
- `lembrete` já tem política RLS — confirme que o CRUD a respeita, com teste negativo
- Data/hora, texto, status pendente/concluído
- `origem_automatica` marca os criados pela Automação em transferência (`RF-CRM-60`) — o atendente não cria esses

Aba dedicada, listando os lembretes do usuário com filtro por período e status.

## Bloco 2 — Mensagens programadas (`RF-CRM-61` a `64`)

Mesma estrutura e mesma regra de privacidade.

- Criadas pela aba lateral do lead e pelo **relógio do composer** (`RF-CRM-69`) — este é um dos gaps da E11
- Editáveis enquanto `AGENDADA`; canceláveis
- O envio no horário é da Automação, não seu: você grava a linha, ela envia

## Bloco 3 — Mensagens rápidas (`RF-CRM-51` a `53`)

- Lista **pessoal** por atendente; gestor visualiza todas
- Palavra-chave única por atendente (o índice já garante)
- **Acionamento por palavra-chave dentro da conversa** (`RF-CRM-12`) — o segundo gap da E11

Sobre o acionamento: o atendente digita a palavra-chave e a mensagem é inserida no composer. Decida e relate como isso é disparado (prefixo, atalho, autocomplete) — o requisito não especifica, e a escolha afeta o dia a dia de quem usa 8 horas.

## Bloco 4 — Equipe e presença (`RF-CRM-46`, `47`, `81`, `74`)

- CRUD de atendentes e subgestores, restrito a gestor: criar, editar, desativar, definir papel
- **Nunca deletar usuário** — desativar. Há FK de lead, atendimento, lembrete e auditoria apontando para ele
- Mini-dashboard de avaliações no topo (`avaliacao` já existe)
- **Presença** (`RF-CRM-81`): o usuário define online/ausente/offline; o rodapé mostra o próprio estado
- A presença alimenta a disponibilidade para a IA (`RF-CRM-74`) — exponha em `/internal/v1/atendentes/disponiveis`, que já existe

A presença ficou de fora da E10 justamente por não ter endpoint. Agora tem.

## Bloco 5 — Gaps restantes da E11 e ajuste da timeline

**Atalho de tags no cabeçalho do atendimento** (`RF-CRM-65`) — os endpoints vieram na E12.

**Cursor real de paginação no histórico de mensagens.** Cursor, não offset: mensagens novas chegam durante a rolagem e o offset duplica ou pula itens.

**Ator estruturado na timeline.** Decisão tomada: acrescente `ator_id` e `dados` (JSONB) a `evento_timeline`, mantendo `descricao` como snapshot de fallback.

- Os listeners passam a gravar os dois: os parâmetros estruturados **e** a descrição renderizada
- Na leitura, renderize a partir dos dados atuais — nome atual do ator, não UUID
- Caia no `descricao` quando o ator não existir mais ou a linha for anterior à mudança
- **Sem reescrita retroativa.** As linhas antigas continuam legíveis pelo fallback

Resolve o UUID aparecendo na tela sem quebrar histórico nem exigir migração de dados.

---

## Cuidados transversais

- `RN-CRM-04` vale para os três primeiros blocos: privado por atendente, gestor vê tudo com coluna de origem. **Teste negativo em cada um.**
- Zero dado mockado, zero cor ou string literal
- Repositórios novos seguem o padrão obrigatório (porta sem `findAll` cru, JPA pacote-privada, regra ArchUnit)
- Nada bloqueante no caminho de mensagem

## Definição de pronto

- [ ] Lembretes, mensagens programadas e mensagens rápidas: CRUD completo com privacidade provada por teste negativo
- [ ] Equipe com desativação (nunca exclusão) e avaliações
- [ ] Presença definível e refletida em `/internal/v1/atendentes/disponiveis`
- [ ] Mensagem rápida acionável por palavra-chave no composer
- [ ] Agendar mensagem pelo relógio do composer
- [ ] Atalho de tags no cabeçalho
- [ ] Paginação do histórico por cursor
- [ ] Timeline mostrando nome do ator, com fallback funcionando para linhas antigas
- [ ] CI verde

Commits sugeridos, um por bloco:
`feat: lembretes`, `feat: mensagens programadas`, `feat: mensagens rapidas`, `feat: equipe e presenca`, `feat: gaps do composer e ator na timeline`.

Ao terminar, me diga **como ficou o acionamento da mensagem rápida** — é a interação que o atendente vai repetir mais vezes por dia, e vale conferir antes da homologação.
