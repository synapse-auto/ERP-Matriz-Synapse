# Prompt E12 — Aba lateral do lead e timeline

> Pré-requisito: E11b commitada (`69912a1`). **Sessão limpa.**
> Leia `AGENTS.md` por inteiro antes de começar.

---

**Etapa E12 — Painel lateral com a ficha completa do lead e a linha do tempo de eventos.**

## Antes de tocar no frontend: confira o backend

A E11 revelou que casos de uso existiam sem endpoint. Verifique o mesmo aqui — provavelmente falta:

- `GET /api/v1/leads/{id}/timeline` — eventos do lead, paginado
- Vincular e desvincular tag de um lead (`lead_tag` existe, endpoint talvez não)

Se faltar, construa antes, com a mesma disciplina: caso de uso, autorização por papel **e** por propriedade, Specification aplicada, teste negativo provando que atendente não alcança lead de colega.

## 1. Painel lateral

Abre com **um clique** no lead; **duplo clique** abre o atendimento (`RN-CRM-05`). Funciona tanto na lista de Atendimentos quanto na Agenda.

Conteúdo (`RF-CRM-14`, `RF-CRM-17`):

- Avatar, nome, telefone, e-mail, CPF, empresa, localização, canal de origem
- **Stepper de etapa** (`RF-CRM-70`): numerado "X de N", com rótulo de início e fim, a partir de `etapa_atendimento.ordem`
- **Contadores** (`RF-CRM-71`): número de atendimentos e de mensagens — já denormalizados no lead, não calcule
- **Tags** com cor e ícone (`RF-CRM-77`), com adicionar e remover
- **Resumo por IA** — somente leitura; quem escreve é a Automação
- **Notas** — campo de texto compartilhado, editável, com salvamento explícito
- **Campos customizados** (E06b): renderizados a partir de `GET /api/v1/campos-customizados`, respeitando tipo e obrigatoriedade

## 2. Timeline

Eventos do lead e do sistema em ordem cronológica (`RF-CRM-15`):

- "Follow-up de 1 hora enviado"
- "Atendente Daiane transferiu o atendimento para Michael"
- Origem visível: `SISTEMA`, `AUTOMACAO` ou `USUARIO`

Paginada — é append-only e cresce sem limite. Não carregue tudo.

## 3. Sem controle fantasma

**Lembretes e mensagens programadas ainda não têm endpoint** (vão na E13). Não construa botão que não funciona: ou o backend existe e a ação funciona de verdade, ou o controle não aparece.

Mesma regra para qualquer outro campo sem rota.

## 4. Cuidados

- Zero dado mockado, zero cor ou string literal
- Atualização otimista em notas e tags **com reversão visível** se o servidor recusar
- Abrir a lateral não pode recarregar a lista inteira
- A lateral respeita a visibilidade: se o lead não é alcançável, o servidor devolve 404 e a UI trata — não esconda por `if`

## 5. Testes

- Um clique abre a lateral; duplo clique abre o atendimento
- Ficha traz `notas` e `resumo_ia` (a **listagem** continua sem eles)
- Contadores vêm do lead, sem `COUNT(*)`
- Adicionar e remover tag persiste e reflete
- Atendente não abre lateral de lead de colega
- Timeline pagina e mostra a origem de cada evento
- Campo customizado obrigatório é exigido ao salvar

## Definição de pronto

- [ ] Endpoints faltantes construídos, com teste negativo
- [ ] Lateral com ficha completa, stepper, contadores, tags, notas, resumo e campos customizados
- [ ] Timeline paginada com origem visível
- [ ] `RN-CRM-05` funcionando
- [ ] Nenhum controle sem backend por trás
- [ ] CI verde

Commit: `feat: painel lateral do lead e timeline`.
