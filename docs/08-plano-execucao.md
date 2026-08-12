# 08. Plano de Execução até 25 de agosto

**Contexto:** desenvolvedor solo · hoje 27/07 · meta **25/08** = **29 dias corridos** (~21 dias úteis) · alvo 80% do backend + 50% do frontend.

> **Revisão de prazo:** este documento foi originalmente escrito para uma meta em 07/08 (11 dias), na qual o alvo não era alcançável. Com a data movida para 25/08, o cenário muda completamente — ver §1.

---

## 1. Avaliação do prazo

Com 29 dias corridos, **o alvo de 80% do backend + 50% do frontend passa a ser realista** — e, mais importante, realista *no nível de robustez que o documento interno exige*, que é o que estava em risco antes.

O plano de etapas soma **~13 dias de trabalho efetivo** para o caminho crítico completo (E00 a E14). Isso deixa aproximadamente **8 dias úteis de folga**, que é exatamente o tipo de margem que separa "entregou" de "entregou funcionando".

### Como usar a folga (em ordem de prioridade)

| Uso | Dias | Por quê |
|---|---|---|
| **Contingência real** | 3 | Algo vai dar errado. O provedor de WhatsApp vai ter uma peculiaridade não documentada; uma dependência vai conflitar. Folga não gasta é folga bem investida. |
| **Campanhas (módulo completo)** | 2,5 | É o motivador de compra nº 3 do cliente. Com o prazo folgado, dá para entregar junto em vez de empurrar. |
| **Dashboard + Relatórios (versão inicial)** | 1,5 | Não precisa ser "milhares de informações" na v1 — 8 a 10 indicadores bem escolhidos já entregam a percepção de valor. |
| **Hardening extra e testes** | 1 | Vale mais do que qualquer feature adicional, dada a promessa de estabilidade. |

**Recomendação:** não realoque toda a folga para features. Comprometer os 8 dias com escopo novo recria exatamente o problema que a extensão do prazo resolveu. Segure pelo menos 3 dias como contingência genuína e decida sobre eles na semana de 18/08, quando você já souber onde o projeto realmente está.

### O que continua valendo do diagnóstico original

O escopo total dos requisitos (9 módulos, ~35 tabelas, ~60 casos de uso, chat em tempo real, motor de campanhas, dashboard e design system) ainda é maior do que 29 dias solo. **100% das features não cabe.** O que cabe agora — e não cabia antes — é o caminho crítico completo, sólido e testado, mais dois ou três dos módulos secundários.

O princípio de priorização também continua: entregar 80% das features num estado instável é pior do que entregar 60% que não caem. O cliente veio de um CRM que cai; a estabilidade é o produto.

---

## 2. Recorte de escopo

### 🟢 Núcleo (E00–E14 — inegociável)

| Módulo | Por quê |
|---|---|
| Fundação (Maven multi-módulo, Flyway, config, Docker) | Sem isso nada mais existe |
| Auth + RBAC + Specification de visibilidade | Regra de comissão/propriedade de lead — comercialmente sensível |
| `crm-core`: Lead, Tags, Etapas, filtro modular | Núcleo de dados de tudo |
| `atendimento`: conversas, mensagens, WebSocket, canal WhatsApp | **É o produto.** RNF-CRM-01 |
| Outbox + fila + contrato `/internal/v1` | Sem isso a Automação não pode ser integrada |
| `automacao-config`: painel de parâmetros + feature flags | A Automação depende disso para rodar |
| Audit log (via AOP) | Barato agora, caríssimo de retrofitar |
| Health check + watchdog + circuit breakers | Promessa explícita ao cliente |
| Frontend: shell, Atendimentos, lead, config | As telas que o cliente usa 8h/dia |
| CRUDs de suporte | Lembretes, msg programadas/rápidas, arquivos, equipe |

### 🟡 Provável (com a folga)

**Campanhas** (motor + métricas + comparativo) e **Dashboard/Relatórios v1**. Decida na semana de 18/08, com dados reais de progresso.

### 🔴 Fase 2 (pós-entrega)

Chat interno, Fidelização, Importação CSV, Kanban drag-and-drop, Dashboard completo ("milhares de informações"), RLS entre atendentes.

Nenhum bloqueia o *go-live*. Todos entram com feature flag desligada e são ativados incrementalmente — que é justamente o "pedir algo novo e dias depois aparecer no sistema" que encantou o cliente. **Use a entrega incremental a favor da narrativa comercial**, em vez de tratá-la como dívida.

---

## 3. Cronograma proposto

Organizado por semana, com as etapas do `PLANO-ETAPAS.md`. Datas de referência, não contrato — o que manda é a sequência.

### Semana 1 (28/07 – 03/08) — Fundação e núcleo de dados

| Etapa | Escopo |
|---|---|
| E00 | Fundação do monorepo, Docker, CI |
| E01 | Schema e migrations Flyway |
| E02 | Auth, RBAC, Specification de visibilidade |
| E03 | CRM Core + filtro modular (Composite/Interpreter) |
| E10 | Frontend: fundação, design tokens, shell, login |

**Marco:** dá para logar, e a regra de isolamento de lead está provada por teste.

### Semana 2 (04/08 – 10/08) — O produto

| Etapa | Escopo |
|---|---|
| E04 | Atendimento: domínio e casos de uso |
| E05 | Canal WhatsApp (ACL, webhook, credencial versionada) |
| E06 | Tempo real: WebSocket + Redis |
| E11 | Frontend: tela de Atendimentos |

**Marco:** um atendente troca mensagens reais com um cliente pelo CRM. É aqui que o projeto deixa de ser esqueleto.

### Semana 3 (11/08 – 17/08) — Integração e resiliência

| Etapa | Escopo |
|---|---|
| E07 | Outbox, fila, `/internal/v1`, OpenAPI |
| E08 | Config de automação + feature flags |
| E09 | Auditoria, health check, watchdog, circuit breakers |
| E12 | Frontend: aba lateral do lead e timeline |

**Marco:** a Automação consegue operar contra o CRM, e derrubar um componente não derruba o atendimento.

### Semana 4 (18/08 – 25/08) — Complemento e hardening

| Etapa | Escopo |
|---|---|
| E13 | CRUDs de suporte (back + front) |
| — | **Ponto de decisão (18/08):** avaliar progresso e alocar a folga entre Campanhas, Dashboard e contingência |
| E14 | Hardening, testes, deploy de homologação |

**Marco:** checklist do §5 todo verde e a subgestora usando o sistema sem acompanhamento.

> **Entrega antecipada à subgestora:** o documento interno prevê disponibilizar o sistema a ela 10–15 dias antes da implantação. Com a meta em 25/08, isso significa dar acesso a um ambiente de homologação **por volta de 11/08** — ao fim da Semana 2, quando Atendimentos já funciona. Não espere estar tudo pronto: o valor do acompanhamento dela é justamente pegar desalinhamento cedo.

---

## 4. Como comprar velocidade sem comprar dívida

**Aceleradores que valem a pena:**

- **Gere o CRUD, escreva o domínio.** Casos de uso repetitivos podem ser gerados a partir de um template interno. Invista o tempo humano no domínio de atendimento.
- **shadcn/ui + Tremor.** shadcn para interação, Tremor para gráficos. Ambos sobre Radix (acessibilidade resolvida). Customize só os tokens.
- **Testcontainers em vez de mocks de banco.** Sobem em segundos e pegam os bugs que mocks escondem. Dado que "a maioria dos bugs só aparece em produção", é o investimento de teste com melhor retorno.
- **Feature flags em vez de branches longas.** Commit direto na main com a feature desligada. Dev solo não deveria gastar tempo com merge de branch de 5 dias.

**Atalhos que vão cobrar caro — não faça, nem com a folga no bolso:**

- Pular o Outbox "porque a fila raramente falha".
- Espalhar a regra de visibilidade de lead pelos endpoints em vez de centralizar na Specification.
- Hardcodar textos/cores "só nessa tela". Não arruma depois — e é o oposto literal da Base PAI.
- Deixar os testes para o fim.

---

## 5. Definição de pronto para 25/08

**Backend**

- [ ] Um atendente faz login, vê apenas seus leads, e a regra é imposta no servidor
- [ ] Mensagem enviada pelo CRM chega no WhatsApp do cliente
- [ ] Mensagem enviada pelo cliente aparece na tela do atendente em < 1s
- [ ] Transferência de lead funciona e gera evento na timeline e no audit log
- [ ] Filtros modulares combinam critérios aninhados com contagem em tempo real
- [ ] `/internal/v1/automation-config` documentado em OpenAPI e consumível pela Automação
- [ ] Alteração de parâmetro no painel reflete na Automação sem deploy
- [x] `/health/critical` responde corretamente (`SaudeCriticaIT`, `SaudeBancoIndisponivelIT`, `SaudeCanalInvalidoIT`)
- [ ] watchdog externo provisionado e alerta confirmado em queda intencional de homologação (`docs/15`)
- [ ] Derrubar o RabbitMQ no meio de uma transferência não perde o evento (outbox)
- [ ] Testes de integração cobrindo o fluxo de mensagem ponta a ponta
- [ ] CRUDs de suporte respeitando a privacidade por atendente (RN-CRM-04)

**Frontend**

- [ ] Layout base com sidebar, perfil e configurações (referência claude.ai)
- [ ] Tela de Atendimentos usável de verdade: lista, conversa, envio de texto e mídia, mensagens rápidas, status de entrega
- [ ] Aba lateral do lead com ficha, stepper de etapa, contadores e timeline
- [ ] Reconexão de WebSocket sem perda de mensagem
- [ ] Tema aplicado via design tokens, zero cor ou texto hardcoded
- [ ] Painel de configuração da automação
- [ ] Telas dos CRUDs de suporte

**Base PAI**

- [ ] Módulos com fronteiras respeitadas (nenhum acessa tabela de outro)
- [ ] Nenhum `if` por tenant no core
- [ ] Feature flags controlando a visibilidade dos módulos
- [ ] Tema e textos trocáveis por arquivo de configuração

---

## 6. Sobre a comunicação com a Synapse

Com o prazo em 25/08, a conversa muda de "renegociar expectativa" para "alinhar o que entra na folga". Vale explicitar o recorte desde já:

> "Em 25/08 entrego o núcleo completo e estável — Atendimentos, leads, filtros, integração com a Automação, resiliência e as telas principais. Campanhas e Dashboard entram se o progresso permitir, e a decisão sobre isso sai em 18/08. Chat interno, importação CSV e o Dashboard completo ficam para a fase 2, entregues incrementalmente após a implantação."

Duas vantagens: o cliente sabe o que esperar, e o "entregue incrementalmente após a implantação" vira demonstração contínua daquilo que o documento interno diz que mais o cativa — pedir algo e ver aparecer poucos dias depois.
