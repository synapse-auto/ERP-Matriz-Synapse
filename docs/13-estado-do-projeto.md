# 13. Estado do Projeto — handoff

Documento de continuidade. **Este arquivo mais o `AGENTS.md` recuperam o contexto necessário para seguir calibrando as próximas etapas.**

Handoff da E58 (26/08/2026). O HEAD de código muda; confira o git. Acrescentado em 30/08/2026: `lead.codigo` (V47) e edição do nome na sidebar.

### 30/08/2026 — Código interno do lead

Coluna `lead.codigo VARCHAR(20)`, somente dígitos, opcional, sem unique. Editável na ficha (`PUT /api/v1/leads/{id}`) por quem alcança o lead. Visível e editável em Informações gerais do painel da conversa; no card da lista de Atendimentos aparece de forma compacta (`leadCodigo`) só quando preenchido. Overlay da Agenda mostra, não edita. Agenda/`LeadResumo` não carrega o campo. Não é `dados_customizados`: o card não pode projetar JSONB. ADR-009 em `docs/04`. PR #28.

### 30/08/2026 — Nome do cliente na sidebar

O título da ficha (4ª coluna de Atendimentos e overlay da Agenda) passou a ser um editor inline: blur ou Enter grava via o mesmo `PUT /api/v1/leads/{id}`. Nome vazio não chama a API no frontend e o backend devolve 400 (`Nome invalido`) se o campo vier em branco — o schema é `NOT NULL` e card/cabeçalho/busca dependem dele. Depois de salvar, o cache da inbox recebe `leadNome` e a Agenda é invalidada.

---

## 1. Onde estamos

**Entrega: 25/08/2026.** Desenvolvedor solo, com agentes (Codex desde a E12; Gemini/Antigravity e Claude Code a partir da E31).

O CRM está **no ar em homologação**, com WhatsApp funcionando ponta a ponta e tempo real ativo.

| Etapa | Entrega | Estado |
|---|---|---|
| E00–E25 | fundação, backend, frontend, fidelidade visual, Dashboard, repasse para a Automação | ver histórico do git |
| E26 | telefone canônico, não lidas, autoria real, degradação parcial de tela | feita |
| E27 | isolamento do canal por `phone_number_id` — contenção do incidente de 16/08 | feita |
| E27b | telefone canônico com DDI, migration `V24` | feita |
| E28 | gravação de áudio no composer, scroll interno do canvas, payload de áudio da Meta | feita |
| E29 | primeiro acesso e troca de senha (`senha_alterada_em`) | feita |
| E31 / E31b | ajustes finos de UI, logo e favicon da instância, pendências da E29 | feita |
| E30 | áudio AAC/M4A, `POST /internal/v1/atendimentos/{id}/mensagens-enviadas`, botões e listas | feita — `066a5c1`, run 32443306178 |
| **E32** | **próxima** — payload da Meta com várias mensagens | prompt escrito, não despachada |

**HEAD de código da publicação: `8d59cbe`.** A publicação reúne as correções E53–E57 e o ajuste
de autosize da E58; o commit de documentação desta etapa vem em seguida.

### Estado após E58

- A transferência humana valida destino existente, ativo e com papel `ATENDENTE`, antes de gravar.
- O chat apresenta uma conversa por cliente, unificando o histórico dos atendimentos do lead.
- A leitura de atendimento é por usuário desde a V41; `atendimento.lido_ate` permanece como legado.
- A outbox reserva por lease, envia fora de transação e grava o resultado em transação curta.

## 2. O que já foi provado no ambiente real

- **Mensagem entrando**, de número real, ponta a ponta: Meta → Traefik → HMAC → filtro por número → lead → atendimento → tela
- **Mensagem saindo**, com status de entrega
- **Áudio, imagem e arquivo** entrando; áudio gravado saindo (E30)
- **WebSocket conectando** — `CONNECT(5)-CONNECTED(5)` no log do backend
- App da Meta **publicado**, webhook cadastrado, campo `messages` assinado
- **Isolamento por número validado**: mensagem para o número oficial da Estrutural não chega no CRM de homologação

## 3. Pendências operacionais — dependem do Marcondes

Nenhuma é prompt. Passo a passo completo em `docs/18-runbook-pendencias-operacionais.md`.

| Item | Situação |
|---|---|
| **Deploy da E30** | `SYNAPSE_IMAGE_TAG` ainda em `1c3cfca`; o HEAD de código é `066a5c1`. Enquanto não trocar, o endpoint novo devolve 404 para a Automação |
| **Smoke RLS** | nunca executado. Se um atendente vê lead de colega, é incidente comercial |
| **Seed de demonstração** | nunca executado; telas quase vazias |
| **Backup + restauração testada** | não feito |
| **Watchdog externo** | endpoint e runbook prontos; Uptime Kuma não provisionado |
| **Subdomínios reais** | `sslip.io` divide cota do Let's Encrypt com o mundo; na renovação o certificado pode não sair |
| **Painel do Dokploy em HTTP na porta 3000** | fechar com `ufw` e acessar por túnel |
| **Rotações pendentes** | `N8N_DB_PASSWORD` (vazada em grupo) e o token da Meta (colado em chat) |
| **Lead de teste** | `test user name` (`16315551181`) criado pelo webhook de teste; limpar antes do cliente |
| **Etapas do funil e tags** | dependem da subgestora |
| **Jardel como GESTOR** | `UPDATE usuario SET papel='GESTOR'` — a tela só atribui ATENDENTE e SUBGESTOR |

## 4. Decisões que não se revertem sem custo

- **Multi-tenancy Silo**: instância isolada por cliente, sem `tenant_id`. Um repositório, uma imagem, N deploys. **Filho novo não escreve código.**
- **Regra de precedência**: a aba Atendimentos não pode cair 08:00–18:30.
- **`/internal/v1` não é roteável publicamente.** n8n na mesma overlay, token como segunda camada.
- **Repasse para a Automação é assíncrono e opcional.** A entrada de mensagem nunca depende do n8n.
- **Java 21 fixo.** Java 25 quebra o ArchUnit silenciosamente.
- **`SYNAPSE_IMAGE_TAG` sempre com o SHA do commit, nunca `latest`** — em homologação também. O Swarm fixa o digest na criação do serviço; com `latest`, `Restart` e `Deploy` sobem a imagem velha. Custo da regra: todo deploy exige atualizar a variável. Detalhe em `docs/18`.
- **Telefone canônico**: só dígitos, com código de país, sem `+`. Decidido e migrado na E27b (`V24`).
- **Isolamento por `phone_number_id`** lido de `canal_credencial`, com fail-closed. `subscribed_apps` na Meta é por WABA, não por número.
- **Marca da instância em runtime**: `tema.json` aponta `logoUrl` para `/api/v1/config/logo`. Trocar de filho não toca em `.tsx`. Não existe `favicon.ico` em `frontend/src/app/` — metadata de arquivo venceria a do tema.
- **Idempotência de mensagem em tabela estreita não particionada** (`mensagem_automacao_idempotencia`, `V29`): `mensagem` é particionada por `enviado_em` e o Postgres exige a chave de partição em todo UNIQUE.
## 5. O padrão que se repete — dezoito casos

Proteção que existe, passa no teste, e não protege nada. Os mais caros:

1. `DoNotIncludeJars` — ArchUnit nunca rodou por três etapas
2. RLS escrita, usuário era dono das tabelas — só o **teste negativo** expôs
3. `@Scheduled` com auto-invocação — mensagens quebradas nos dois sentidos, build verde
4. `JwtAuthenticationToken` de um argumento nasce `authenticated=false`
5. Webhook `GET` reusando o validador HMAC do `POST`
6. Redis em `localhost` — passava local, 166 falhas no runner
7. **E23**: `traefik.docker.network` somada à `traefik.swarm.network` fez o Traefik **descartar o router do backend inteiro**. `/api/v1` devolvia HTML, `/ws` dava 502, o webhook da Meta parou. CI verde com 323 testes; o teste de handshake da própria etapa passava contra um Traefik montado por ela
8. **E23**: `onMutate` tratando cache de `useInfiniteQuery` como array plano — `mutationFn` nunca era chamado, e **o teste assumia a mesma forma errada**

9. **E29/E31**: `SYNAPSE_IMAGE_TAG=latest` — o Swarm fixa o digest na criação do serviço, então `Restart` e `Deploy` sobem a imagem velha. CI verde, deploy feito, **metade do sistema antiga**. Aconteceu duas vezes antes de alguém entender o mecanismo
10. **E31b**: `frontend/src/app/favicon.ico` do scaffold do Next vencia o `metadata.icons` gerado a partir do tema. O código estava certo; o arquivo do andaime, versionado desde o commit de fundação, é que anulava
11. **Entrada de mensagem**: `get(0)` em `entry`/`changes`/`messages`. A E27 passou a percorrer **todos** os eventos para filtrar por número, e a tradução continuou lendo só o primeiro — as duas metades da mesma classe discordando sobre o que é o payload. A Meta agrupa, o CRM grava uma mensagem e responde 200

Acrescentadas depois da E31: **imagem publicada não é imagem rodando** — confira o digest dos dois serviços, não o log do CI; e **arquivo de andaime vence configuração** — o que o framework resolve por convenção de arquivo ganha do que você configurou em código.
**Regras derivadas** (também no `AGENTS.md`): proteção nova nasce com teste que a viola; teste o ponto de entrada, não o método interno; erro recorrente em log é defeito, não paisagem; teste o negativo; espere por condição, nunca por tempo; **"CI verde" só vale com o número da run**; e o mais recente — **teste que valida a configuração que você montou não prova nada sobre a que roda**.

## 6. Dívidas abertas

`docs/14-pendencias-de-funcionalidade.md` tem a lista completa. As maiores:

- **Payload da Meta com várias mensagens** — perda silenciosa de mensagem de cliente. Prompt E32 escrito, não despachado. **É a maior dívida técnica aberta**
- **Regras de automação** (follow-up, fidelização, festiva, resumo IA) — tabelas existem, zero caso de uso
- **Horários de trabalho** — módulo inteiro; hoje a disponibilidade do atendente é **manual**
- **Disponibilidade para a IA independente da presença** — `disponivel_para_ia` espelha o status e só é gravado para `papel = 'ATENDENTE'`; não existe "atendente online fora do rodízio"
- **Escala do CSAT** — banco 0–5, protótipo 9,4/10. Decidir com o sistema vazio custa muito menos
- Kanban, CSV, avaliação por atendimento, troca de credencial de canal
- **PITR** antes do go-live de produção

## 7. Como o arquiteto trabalha aqui

**O ciclo:** um prompt por etapa → o agente executa e reporta no formato de sete itens do `AGENTS.md` → o arquiteto lê, decide o que ficou aberto e calibra o próximo.

**O que extrair de cada relatório:** o item 3 (decisões não especificadas) traz as escolhas a validar; o item 5 (bugs encontrados) é onde apareceram quase todos os defeitos silenciosos; o item 7 é o que precisa de decisão — decida quando for técnico, devolva ao humano quando envolver dinheiro, cliente ou risco irreversível.

**Ponto de parada é ferramenta.** Prompts que mandam o agente parar e avisar quando a premissa não se sustenta já evitaram duas entregas erradas — a Dashboard sem histórico de etapa, e o card de IA sem registro de transferência.

**Tom:** direto, sem bajulação. Admitir erro rápido e seguir. Neste projeto o arquiteto errou, entre outras: avaliou vulnerabilidades npm como dev-only quando eram de produção; decidiu ignorar `docs/` no git; extraiu o `TOKENS.md` sem mapear a base do shadcn; instruiu a converter tabelas em cards com base num relatório não conferido; sugeriu rollback num ambiente que não é produção.

## 8. Ordem recomendada

1. **Deploy da E30** — `SYNAPSE_IMAGE_TAG=066a5c1`. Sem isso a Automação não tem o endpoint, e o Dylan fica parado
2. **E32** — payload com várias mensagens. Prompt escrito. É perda silenciosa de mensagem de cliente: mais grave que a aba cair, porque queda alguém percebe
3. **Smoke RLS** (`docs/18` fase 3) — dez minutos, e é o portão: se falhar, para tudo
4. **Seed** — sem ele ninguém consegue avaliar tela
5. **Correções pontuais** que aparecerem no teste do Lucas
6. **Backup, watchdog e subdomínios** antes do go-live
7. **25/08** — entrega

`docs/17-plano-de-fechamento.md` tem o plano completo, incluindo o que fica de fora e o que precisa ir por escrito ao cliente.

## 9. Como escrever o próximo prompt

`docs/prompts/COMO-ESCREVER-PROMPTS.md` descreve o formato usado da E19 em diante, com os
exemplos que funcionaram e os erros que custaram etapa. Leia antes de redigir a E33.
