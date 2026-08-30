# CRM Estrutural Vidros — Documentação de Arquitetura

Documentação técnica gerada a partir do documento **"Requisitos do CRM — CRM Integrado com IA (Estrutural Vidros)"**, para orientar o desenvolvimento do CRM em **Java (backend) + Next.js (frontend) + PostgreSQL**.

Este pacote cobre a análise, arquitetura e modelagem. A execução (automação/IA) é tratada no documento irmão de Requisitos da Automação e se integra a este sistema pelas superfícies de configuração descritas aqui (seção 3.4 do documento de requisitos).

## Decisões estruturais já tomadas

- **Multi-tenancy:** instância isolada por cliente (deploy + banco próprios por "filho"). Sem `tenant_id`, sem RLS entre clientes — isolamento físico.
- **Estilo:** monólito modular com Clean/Hexagonal Architecture por bounded context.
- **Base PAI:** core versionado + repositório fino por cliente; customização por configuração, feature flags e adaptadores — nunca por fork.
- **Precedência absoluta:** a aba Atendimentos não pode cair no horário comercial. Toda decisão de arquitetura passa por esse filtro primeiro.

## Índice

### Modelagem
1. [`01-arquitetura-geral.md`](./01-arquitetura-geral.md) — stack, estilo arquitetural, módulos (bounded contexts), estratégia de tempo real, mensageria com a Automação, decisões não funcionais.
2. [`02-modelo-dominio-classes.md`](./02-modelo-dominio-classes.md) — diagrama de classes do domínio (Mermaid), agregados e regras de negócio embutidas.
3. [`03-modelo-dados-postgres.md`](./03-modelo-dados-postgres.md) — diagrama entidade-relacionamento (Mermaid) + script DDL completo para PostgreSQL, com índices e particionamento.
4. [`04-adrs-e-api.md`](./04-adrs-e-api.md) — Architecture Decision Records (ADRs) e contrato de API (REST + WebSocket) entre CRM, frontend e Automação.
5. [`05-rastreabilidade-requisitos.md`](./05-rastreabilidade-requisitos.md) — matriz de rastreabilidade cruzando cada RF-CRM/RNF-CRM/RN-CRM crítico com o artefato técnico correspondente.

### Análise consolidada e execução
6. [`06-analise-consolidada-e-padroes.md`](./06-analise-consolidada-e-padroes.md) — análise cruzada dos requisitos do CRM + requisitos internos, tensões a arbitrar, e catálogo de padrões de projeto justificados por requisito.
7. [`07-base-pai-multitenancy.md`](./07-base-pai-multitenancy.md) — estratégia Base PAI: como o core se propaga para os filhos, camadas de customização, troca do número principal, contrato estável com a Automação.
8. [`08-plano-execucao.md`](./08-plano-execucao.md) — avaliação do prazo, cronograma semanal até 25/08 e definição objetiva de pronto.
9. [`09-escopo-primeira-entrega.md`](./09-escopo-primeira-entrega.md) — **recorte oficial da primeira entrega.** Tem precedência sobre a lista de features dos requisitos para o que entra até 25/08.

### Schema vigente (vence o `03` quando divergirem)
- [`11-banco-atual.md`](./11-banco-atual.md) — colunas e constraints como estão nas migrations Flyway (última: V47 `lead.codigo`).
- [`12-diagramas-banco.md`](./12-diagramas-banco.md) — ERD Mermaid extraído do schema atual.

### Continuidade

- [`13-estado-do-projeto.md`](./13-estado-do-projeto.md) — **leia primeiro se estiver retomando o projeto.** Onde estamos, o que falta, decisões que não se revertem, as nove proteções silenciosas e as dívidas com prazo.
- [`14-pendencias-de-funcionalidade.md`](./14-pendencias-de-funcionalidade.md) — funcionalidades que ainda não existem.
- [`15-operacao-watchdog-externo.md`](./15-operacao-watchdog-externo.md) — provisionamento e teste destrutivo do monitor externo de `/health/critical`.

### Execução com Claude Code
- [`CLAUDE.md`](./CLAUDE.md) — **vai na raiz do repositório**, não em `/docs`. Lido automaticamente pelo Claude Code em todo comando: stack, regras de arquitetura, padrões obrigatórios, proibições e regras de negócio sensíveis.
- [`PLANO-ETAPAS.md`](./PLANO-ETAPAS.md) — as 14 etapas de desenvolvimento com escopo, dependências e definição de pronto.
- `prompt-E00-fundacao.md`, `prompt-E01-schema.md`, `prompt-E02-auth-rbac.md` — prompts prontos para colar no Claude Code, um por etapa.

## Estrutura sugerida no repositório

```
/CLAUDE.md              ← raiz (o Claude Code lê daqui)
/docs/
  00-README.md … 08-plano-execucao.md
  PLANO-ETAPAS.md
  /prompts/
    prompt-E00-fundacao.md …
```

## Como ler

**Se você vai começar a codar agora:** `PLANO-ETAPAS.md` → prompt da etapa atual. Schema vigente é o `11` (vence o `03`). O `06` é a referência de padrões.

**Se você vai apresentar/discutir a arquitetura:** siga a ordem 01 → 07, terminando na rastreabilidade (`05`) como checklist de cobertura.
