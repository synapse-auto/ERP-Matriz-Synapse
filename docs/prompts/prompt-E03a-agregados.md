# Prompt E03a — Lead, Tag e Etapa

> Pré-requisito: E02b commitada (`1b24e82`).
> **Etapa dividida.** Esta é a parte 1 de 2 — CRUD e agregados. O filtro modular fica no `prompt-E03b`.
> Não leia `docs/03` nem `docs/06` inteiros: o schema já está nas migrations e o essencial está aqui.

---

**Etapa E03a — Agregados de CRM Core.**

## Contexto herdado

O `LeadRepositorio` já existe com `listar(FiltroLead)`, `porId(UUID)` e `contar(FiltroLead)`. **Estenda-o; não crie caminho paralelo.**

**Repita o padrão de repositório** (documentado no `README.md`) em todo repositório novo: porta sem `findAll`/`findById` cru, implementação JPA pacote-privada, regra ArchUnit correspondente. Vale mesmo quando parecer exagero para um CRUD simples — é o que impediu o vazamento na E02.

## O que construir

### 1. Lead

Ficha completa conforme o schema. Dois pontos de atenção:

- **Contadores** `num_atendimentos` e `num_mensagens` são incrementados na aplicação, dentro da transação que cria o atendimento/mensagem. Nunca `COUNT(*)` ao abrir a ficha.
- **Projeção em listagem:** `resumo_ia` e `notas` são campos longos e **nunca** entram em consulta de lista. Use projeção/DTO, não a entidade.

### 2. Tag

CRUD com escrita restrita a gestor/subgestor (`RN-CRM-03`). Cor e ícone por tag. Tags são compartilhadas por toda a operação — não há tag pessoal.

### 3. EtapaAtendimento

Tabela de configuração, não enum: a Automação define as etapas e elas mudam sem deploy. Leitura para todos; escrita para gestor.

### 4. Endpoints

| Método | Rota | Papel |
|---|---|---|
| GET/PUT | `/api/v1/leads/{id}` | Atendente (só os seus) |
| GET/POST/PUT/DELETE | `/api/v1/tags` | Escrita: gestor/subgestor |
| GET | `/api/v1/etapas` | Atendente |
| POST/PUT/DELETE | `/api/v1/etapas` | Gestor |

### 5. Testes

- Atendente não cria nem edita tag (403)
- Atendente não acessa lead de colega (404, não 403 — 403 confirmaria a existência)
- Listagem não traz `resumo_ia` nem `notas`
- Contadores incrementam na transação correta
- Repositórios novos passam nas regras ArchUnit

## Restrições

- Nenhuma consulta de lead fora do `LeadRepositorio`
- Nada de Spring/JPA em `domain`
- Etapas e tags vêm do banco, nunca hardcoded

## Definição de pronto

- [ ] CRUDs funcionando com autorização por papel
- [ ] Projeção de listagem sem campos longos
- [ ] Repositórios seguindo o padrão, com regra ArchUnit
- [ ] CI verde

Commit: `feat: agregados de lead, tags e etapas`.

**Pare aqui e reporte.** O filtro modular vem no prompt seguinte, com contexto limpo.
