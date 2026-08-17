# 13. Estado do Projeto — handoff

Documento de continuidade. **Este arquivo mais o `AGENTS.md` recuperam o contexto necessário para seguir calibrando as próximas etapas.**

Atualizado em 16/08/2026, depois da E25.

---

## 1. Onde estamos

**Entrega: 25/08/2026.** Nove dias. Desenvolvedor solo, com agentes (Codex desde a E12).

O CRM está **no ar em homologação**, com WhatsApp funcionando ponta a ponta e tempo real ativo.

| Etapa | Entrega |
|---|---|
| E00–E17b | fundação, backend, frontend, fidelidade visual — ver histórico do git |
| E18 | tema do produto ligado à base do shadcn |
| E19 | controles do design system, filtros da Agenda, superfície da página |
| E20a | histórico de transição de etapa, `resultado` da etapa |
| E20 | Dashboard, aba Visão Geral |
| E21 / E21b | desempenho por atendente, transferência pela Automação, resolução por IA |
| E22 | `/health/critical`, watchdog, `docs/04` com evidência nomeada |
| E23 | correções do primeiro uso real — **introduziu regressão** |
| E24 | regressão da E23, teste de fumaça de boot |
| E25 | repasse para a Automação, bug do clique, fidelidade de Atendimentos |
| **E26** | **próxima** — telefone canônico, não lidas, autoria, degradação de tela |

## 2. O que já foi provado no ambiente real

- **Mensagem entrando**, de número real, ponta a ponta: Meta → Traefik → HMAC → lead → atendimento → tela
- **Mensagem saindo**, com status de entrega
- **WebSocket conectando** — `CONNECT(5)-CONNECTED(5)` no log do backend
- App da Meta **publicado**, webhook cadastrado, campo `messages` assinado
- Canal e credencial cadastrados no banco

## 3. Pendências operacionais — dependem do Marcondes

Nenhuma é prompt. Passo a passo completo em `docs/18-runbook-pendencias-operacionais.md`.

| Item | Situação |
|---|---|
| **Smoke RLS** | nunca executado. Se um atendente vê lead de colega, é incidente comercial |
| **Seed de demonstração** | nunca executado; telas quase vazias |
| **Backup + restauração testada** | não feito |
| **Watchdog externo** | endpoint e runbook prontos; Uptime Kuma não provisionado |
| **Subdomínios reais** | `sslip.io` divide cota do Let's Encrypt com o mundo; na renovação o certificado pode não sair |
| **Painel do Dokploy em HTTP na porta 3000** | fechar com `ufw` e acessar por túnel |
| **Etapas do funil e tags** | dependem da subgestora |
| `AUTOMACAO_WEBHOOK_EVENTOS_URL` | cadastrar no Dokploy com a URL interna do n8n |

## 4. Decisões que não se revertem sem custo

- **Multi-tenancy Silo**: instância isolada por cliente, sem `tenant_id`. Um repositório, uma imagem, N deploys. **Filho novo não escreve código.**
- **Regra de precedência**: a aba Atendimentos não pode cair 08:00–18:30.
- **`/internal/v1` não é roteável publicamente.** n8n na mesma overlay, token como segunda camada.
- **Repasse para a Automação é assíncrono e opcional.** A entrada de mensagem nunca depende do n8n.
- **Java 21 fixo.** Java 25 quebra o ArchUnit silenciosamente.
- **`SYNAPSE_IMAGE_TAG=latest` em homologação**, SHA fixo em produção.
- **Telefone canônico**: só dígitos, com código de país, sem `+` (a decidir na E26).
- **Venda ganha** = transições para etapa `GANHO` no período, por lead distinto (ADR-008).

## 5. O padrão que se repete — quinze casos

Proteção que existe, passa no teste, e não protege nada. Os mais caros:

1. `DoNotIncludeJars` — ArchUnit nunca rodou por três etapas
2. RLS escrita, usuário era dono das tabelas — só o **teste negativo** expôs
3. `@Scheduled` com auto-invocação — mensagens quebradas nos dois sentidos, build verde
4. `JwtAuthenticationToken` de um argumento nasce `authenticated=false`
5. Webhook `GET` reusando o validador HMAC do `POST`
6. Redis em `localhost` — passava local, 166 falhas no runner
7. **E23**: `traefik.docker.network` somada à `traefik.swarm.network` fez o Traefik **descartar o router do backend inteiro**. `/api/v1` devolvia HTML, `/ws` dava 502, o webhook da Meta parou. CI verde com 323 testes; o teste de handshake da própria etapa passava contra um Traefik montado por ela
8. **E23**: `onMutate` tratando cache de `useInfiniteQuery` como array plano — `mutationFn` nunca era chamado, e **o teste assumia a mesma forma errada**

**Regras derivadas** (também no `AGENTS.md`): proteção nova nasce com teste que a viola; teste o ponto de entrada, não o método interno; erro recorrente em log é defeito, não paisagem; teste o negativo; espere por condição, nunca por tempo; **"CI verde" só vale com o número da run**; e o mais recente — **teste que valida a configuração que você montou não prova nada sobre a que roda**.

## 6. Dívidas abertas

`docs/14-pendencias-de-funcionalidade.md` tem a lista completa. As maiores:

- **Regras de automação** (follow-up, fidelização, festiva, resumo IA) — tabelas existem, zero caso de uso
- **Horários de trabalho** — módulo inteiro; hoje a disponibilidade do atendente é **manual**
- **Treze superfícies somem inteiras quando uma query falha** — endereçado na E26
- Kanban, CSV, avaliação por atendimento, troca de credencial de canal
- **PITR** antes do go-live de produção

## 7. Como o arquiteto trabalha aqui

**O ciclo:** um prompt por etapa → o agente executa e reporta no formato de sete itens do `AGENTS.md` → o arquiteto lê, decide o que ficou aberto e calibra o próximo.

**O que extrair de cada relatório:** o item 3 (decisões não especificadas) traz as escolhas a validar; o item 5 (bugs encontrados) é onde apareceram quase todos os defeitos silenciosos; o item 7 é o que precisa de decisão — decida quando for técnico, devolva ao humano quando envolver dinheiro, cliente ou risco irreversível.

**Ponto de parada é ferramenta.** Prompts que mandam o agente parar e avisar quando a premissa não se sustenta já evitaram duas entregas erradas — a Dashboard sem histórico de etapa, e o card de IA sem registro de transferência.

**Tom:** direto, sem bajulação. Admitir erro rápido e seguir. Neste projeto o arquiteto errou, entre outras: avaliou vulnerabilidades npm como dev-only quando eram de produção; decidiu ignorar `docs/` no git; extraiu o `TOKENS.md` sem mapear a base do shadcn; instruiu a converter tabelas em cards com base num relatório não conferido; sugeriu rollback num ambiente que não é produção.

## 8. Ordem recomendada

1. **E26** — telefone canônico primeiro; duplicidade de lead é comissão
2. **Pendências operacionais** — smoke RLS e seed são dez minutos somados
3. **Liberar o Lucas para testar** e reservar 23–24/08 para o retorno dele
4. **25/08** — entrega

`docs/17-plano-de-fechamento.md` tem o plano completo, incluindo o que fica de fora e o que precisa ir por escrito ao cliente.
