# 13. Estado do Projeto — handoff

Documento de continuidade. Se a conversa com o arquiteto for reiniciada, **este arquivo mais o `AGENTS.md` recuperam o contexto necessário para seguir calibrando as próximas etapas.**

Atualizado em: 03/08/2026, após a etapa de isolamento do `/internal/v1`.

---

## 1. Onde estamos

**Entrega: 25/08/2026. Homologação com a subgestora: ~11/08.** Desenvolvedor solo.

| Etapa | Status | Commit |
|---|---|---|
| E00 Fundação do monorepo | ✅ | `1549651` |
| E01 Schema e migrations | ✅ | `ac8326e` |
| E01b Ajustes de constraints e partição | ✅ | `ad94956` |
| E02 Auth, RBAC, isolamento de agenda | ✅ | `84f9bd8` |
| E02b RLS | ✅ | `1b24e82` |
| E03a Agregados de lead, tags, etapas | ✅ | `66d5844` |
| E03b Filtro modular | ✅ | `6e20515` |
| E04 Atendimento e mensagem | ✅ | `36e3b54` |
| E05 Canal WhatsApp, outbox, ACL | ✅ | `defbb09` |
| E06 Tempo real | ✅ | `3e8365c` |
| E06b Campos customizados | ✅ | `00d5ea3` |
| E07 Contrato `/internal/v1` + config | ✅ | `e745302` |
| E07b Correção dos jobs agendados | ✅ | `a104951` |
| E09a Auditoria via AOP | ✅ | `8a01aca` |
| E10 Fundação do frontend | ✅ | `fa862c3` |
| E11 Tela de Atendimentos | ✅ | `19f9008` |
| E11b Anexos de mídia | ✅ | `69912a1` |
| E12 Painel lateral e timeline | ✅ | `5dffbb8` |
| E13 CRUDs de suporte e gaps | ✅ | `03cbe9b` |
| E14 Preparo do deploy | ✅ | `668b681` |
| Isolamento do `/internal/v1` + n8n | ✅ | `add65b8` |
| Swagger/OpenAPI | ✅ | `3f5f0b0` |
| Roteamento Swagger no Traefik | ✅ commitado, **redeploy pendente** | `4c747e2` |
| E14a Deploy de homologação | ⚠️ **stack no ar, definição de pronto NÃO cumprida** | — |
| **E14b Verificação e provisionamento** | ⏳ **próxima** | — |
| E09b Saúde crítica e watchdog | ⏳ após E14b | — |
| E15 Dashboard consolidada | ⏳ opcional | — |

**CI verde** desde `668b681`. Backend 184 testes, frontend 34.

---

## 2. O que falta

**E14a — Deploy de homologação.** Prompt em `docs/prompts/prompt-E14a-deploy-homologacao.md`.

Bloqueios atuais:

| Item | Situação |
|---|---|
| Acesso ao Dokploy | ✅ disponível |
| Número/credenciais da Meta | ⏳ sendo providenciado |
| Domínios (`SYNAPSE_DOMINIO`, `MIDIA_DOMINIO`, n8n) | ❌ a definir |
| S3-compatível + credenciais | ❌ a definir |
| Etapas do funil e tags | ❌ **depende da subgestora** — pedir hoje |
| Admin inicial + canal para a senha | ❌ a definir |
| Capacidade do VPS | ❌ a definir |
| `N8N_IMAGE_TAG` | ❌ a definir |

**Divisão de trabalho no deploy:** o agente prepara scripts, comandos e checklist; **o humano executa no painel do Dokploy** e devolve a saída. Não entregue credencial de infraestrutura ao agente — ações lá são irreversíveis e ele não vê a tela para confirmar visualmente.

**E09b** roda depois do deploy — watchdog só faz sentido com algo hospedado para vigiar.

**E15 (Dashboard)** é opcional; decidir com o progresso real.

---

## 3. Decisões arquiteturais que não podem ser revertidas sem custo

- **Multi-tenancy Silo:** instância isolada por cliente, sem `tenant_id`. Um repositório, uma imagem, N deploys com configuração diferente. **Filho novo não escreve código.**
- **Regra de precedência:** aba Atendimentos não pode cair 08:00–18:30. Precede qualquer outra decisão.
- **`/internal/v1` não é roteável publicamente.** n8n na mesma overlay, token como segunda camada.
- **n8n tem banco e role próprios** dentro do Postgres da instância — credencial de workflow não alcança tabelas do CRM.
- **Backup:** `pg_dump` horário na homologação. **PITR é dívida com data: antes do go-live de produção.**
- **Registry:** GHCR, imagens com tag por SHA e `latest`.
- **Java 21 fixo.** Java 25 quebra o ArchUnit silenciosamente.
- **Documentação versionada** (`docs/`, `design/`, `AGENTS.md`) — decisão revertida em 03/08 após a divergência aparecer.

---

## 4. As nove proteções que falharam em silêncio

O padrão mais valioso aprendido no projeto. Em todos, a proteção existia, o build passava, e nada estava protegido:

1. **E00–E02:** `ArquiteturaTest` com `DoNotIncludeJars` — módulos chegam como JAR, as regras nunca rodaram
2. **E02b:** políticas RLS escritas, mas o usuário era dono/superusuário — todo mundo via tudo, e os testes *positivos* passavam por isso
3. **E03a:** upgrade para Java 25 fez o ArchUnit importar zero classes
4. **E07:** `@Scheduled` chamando método transacional do próprio bean — o caminho de mensagens estava quebrado nas duas direções por duas etapas
5. **E09a:** javadoc afirmando "vira 400" sem `@ExceptionHandler` que fizesse isso
6. **E06:** `JwtAuthenticationToken` de um argumento nasce com `authenticated=false` — todo `@PreAuthorize` falhava sem exceção visível
7. **E01b:** `@Scheduled` de um contexto de teste roubando linha de outbox de outro teste pelo Postgres compartilhado
8. **E14:** verificação `GET` do webhook reusando o validador HMAC do `POST` — a Meta nunca configuraria o webhook
9. **E14:** testes passando local por haver um Redis em `localhost`; no runner, 166 falhas

**Regras derivadas** (também no `AGENTS.md`):

- Toda proteção nova nasce com um teste que a viola de propósito
- Teste o ponto de entrada, não só o método interno — chamar `bean::metodoInterno` num `@Autowired` *parece* testar o ponto de entrada e não exercita a auto-invocação
- Erro recorrente em log de teste é defeito, não paisagem
- Teste o negativo: provar que alguém *não* vê algo pega o que o teste positivo esconde
- Espere por condição, nunca por tempo

---

## 5. Dívidas registradas

| Item | Prazo | Onde |
|---|---|---|
| **PITR no Postgres** | antes do go-live de produção | `docs/10` §1.1b |
| Limites de mídia conferidos contra a doc atual da Meta | antes da produção | `docs/10` §2 |
| Dashboard/Relatórios detalhado | fase 2 | `docs/09` |
| Campanhas, Banco de Arquivos, Chat interno | fase 2 | `docs/09` |
| Teste de combinações de feature flags | quando existir o 2º filho | `docs/06` |
| Warning do React Compiler em `useVirtualizer` | sem prazo | — |
| Docker Desktop local em modo somente-leitura | antes do próximo build local | — |

---

## 6. Como o arquiteto trabalha nesta conversa

Se o contexto for perdido, este é o modo de operação que vinha funcionando:

**O ciclo:** o arquiteto escreve um prompt por etapa → o agente executa e reporta no formato de sete itens do `AGENTS.md` → o arquiteto lê o relatório, decide o que ficou em aberto e calibra o prompt seguinte.

**O que o arquiteto extrai de cada relatório:**

- **Item 3 (decisões não especificadas)** — é onde estão as escolhas que precisam ser validadas ou registradas
- **Item 5 (bugs encontrados)** — sete das nove proteções silenciosas apareceram aqui, fora do escopo da etapa
- **Item 7 (decisões pendentes)** — o arquiteto decide, ou devolve ao humano quando é escolha de produto/custo

**O que ele faz que importa:**

- Decide em vez de listar opções, quando a decisão é técnica
- Devolve ao humano quando envolve dinheiro, cliente ou risco irreversível
- Registra decisão revista nos docs, com o motivo — não sobrescreve silenciosamente
- Diz quando errou. Já errou duas vezes: avaliou vulnerabilidades npm como dev-only quando eram de produção, e decidiu ignorar `docs/` no git

**Tom:** direto, sem bajulação. Discordar do agente quando ele estiver certo tecnicamente mas errado no contexto do produto.

---

## 7. Ordem recomendada daqui

1. **E14a** — deploy de homologação, assim que os acessos existirem
2. **E09b** — health crítico e watchdog, logo após o deploy
3. **Liberar para a subgestora** (~11/08) e coletar feedback
4. **E15** — Dashboard, se o progresso permitir
5. **E14 final** — hardening, checklist do `docs/08` §5
6. **25/08** — entrega

**O que não pode escorregar:** o deploy de homologação. É o primeiro contato do cliente com o produto e o começo da validação contínua prevista no documento interno. Tudo o mais tem folga; isso não.
