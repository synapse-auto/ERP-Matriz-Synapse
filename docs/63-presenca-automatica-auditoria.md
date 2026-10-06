# 63 — Presença automática do atendente: auditoria (E223, Bloco 0)

Leitura **só de código** (sem VPS, sem n8n), sobre `origin/main` (`c364a25`) antes do PR A. Linhas são de `main`.
O que depende de produção ou de navegador real está marcado **não verificado**. Contexto: `docs/62` (por que o 409
do rodízio não pôde ser provado) e `docs/21` (campo `motivo`).

## Resposta curta (item 9)

O desenho é **viável com três ajustes**, todos de design (nenhum muda quem entra no rodízio):

1. **Reconexão não é "primeira sessão".** O frontend troca o socket a cada renovação do token (15 min) e a cada
   reconexão (`tempo-real.ts:302`, E208), então existe um intervalo sem sessão de milissegundos a segundos. Sem regra, o
   AUSENTE manual voltaria a ONLINE a cada 15 minutos. Ajuste: "primeira sessão" = nenhuma sessão por **mais que a
   tolerância**; reconectar dentro dela é continuação e não muda nada.
2. **Varredura periódica além dos eventos.** Só o evento de desconexão deixa para sempre ONLINE quem sumiu durante um
   deploy (o registro de sessões nasce vazio). Uma varredura compara o banco com o registro e marca OFFLINE quem passou
   da tolerância **e** da carência de partida.
3. **Só vale com 1 réplica de backend** (ou exige agregar sessões). O registro é por instância (§4). O padrão é 1 réplica.

Risco aberto, **não verificado**: aba em segundo plano (§5). A mitigação existe na própria biblioteca, mas mexe no
heartbeat — decisão do responsável.

## 1. Onde `status_presenca` é escrito e lido

**Escrito** (só 3 lugares):

| Onde | Arquivo:linha |
|---|---|
| Clique do rodapé → `PATCH /api/v1/usuarios/me/presenca` | `UsuarioController.java:126` → `AtualizarMinhaPresencaUseCase.java:10` → `EquipeRepositorioJdbc.java:30` |
| Desativar usuário (força `OFFLINE`) | `EquipeRepositorioJdbc.java:28` |
| Default na criação (`DEFAULT 'OFFLINE'`) | `V2__equipe.sql:12` |

No frontend, o único caller é o seletor manual (`sidebar.tsx:161`, mobile `navegacao-inferior.tsx`); sem `useEffect` que
mude presença. Nem login nem logout tocam `status_presenca`.

**Lido** (todos como "ONLINE" ou como valor exibido):

| Leitor | Arquivo:linha |
|---|---|
| Rodízio da IA (os dois SQL) | `AtendenteDisponivelRepositorioJdbc.java:63`, `:96` |
| Lista de destinos de transferência (SUBGESTOR só se ONLINE) | `AtendenteParaTransferenciaRepositorioJdbc.java:32-34` |
| Chat interno (bolinha do contato) | `ChatInternoRepositorioJdbc.java:90` |
| Tela de Equipe (contagem de online e cor) | `pagina-equipe.tsx:59`, `:406` ← `EquipeRepositorioJdbc.java` (`BASE`) |
| Dashboard (contador de online) | `DashboardVisaoGeralRepositorioJdbc.java:333` |
| Rodapé da sidebar | `sidebar.tsx:185` (`useMeuUsuario`, chave `["me"]`) |

Consequência: mudar a presença automaticamente **afeta também** a transferência manual (SUBGESTOR), o chat interno e o
Dashboard. É o efeito pretendido, mas precisa constar no relatório.

## 2. Como as sessões STOMP são registradas (item 2)

* Não existe coleção própria: `ObservabilidadeDeSessoesWebSocket.java:27-40` lê o **`SimpUserRegistry`** do Spring,
  que o próprio ciclo CONNECT/DISCONNECT atualiza. `usuarios.getUsers()` devolve cada `SimpUser` com seu conjunto de
  sessões (`getSessions()`): **dá para mapear sessão → usuário e contar sessões por usuário**.
* Eventos do Spring disponíveis: `SessionConnectedEvent` e `SessionDisconnectEvent` (este já é ouvido em
  `LimpezaDeAssinaturasListener.java:26`, só para limpar assinaturas). Segurança entre threads: o registro do Spring
  usa coleções concorrentes (conhecimento do framework, **não testado aqui**); o evento chega em thread do canal de
  entrada (`WebSocketConfig.java:105-108`), **não** na do pool HTTP.
* Cuidado de ordem: não se sabe se o registro já foi atualizado quando o nosso listener roda. Por isso o desenho deve
  contar "outras sessões do usuário **excluindo a sessão do evento**", que vale em qualquer ordem.
* O nome do usuário no registro é o `getName()` do `Principal` (§3): o `sub` do JWT, que é o id do usuário
  (`AutenticacaoHandshakeHandler.java:38`).

## 3. Como o usuário fica ligado à sessão (item 3)

* Autenticação **só no handshake**: `JwtHandshakeInterceptor.java:55-73` decodifica o token, exige `vigente` (usuário
  ativo e papel igual ao do token) e grava o `Jwt`; `AutenticacaoHandshakeHandler.java:38` cria o `Principal`
  (`JwtAuthenticationToken`) uma vez para a sessão.
* **Sessão de token expirado conta como ativa:** nenhum frame revalida a expiração (a validação é do handshake).
  Na prática o frontend renova o token a cada 15 min e **troca o socket** (`tempo-real.ts:302`), então a sessão
  velha é fechada. Se o cliente falhar em renovar, a sessão antiga segue aberta enquanto houver heartbeat.
* Usuário desativado com sessão aberta continua no registro até desconectar; a atualização de presença exige
  `ativo = TRUE` e não o altera.

## 4. Réplicas e reinício (item 4)

* `docker/dokploy-stack.yml:278`: `replicas: ${BACKEND_REPLICAS:-1}` — **padrão 1, número real em produção não
  verificado**. Atualização com `order: start-first` (`:282`): durante o deploy **as duas versões coexistem** por um
  tempo.
* Broker em memória (`WebSocketConfig.java:91`, `enableSimpleBroker`). O Redis é só backplane de mensagens para o usuário;
  `ObservabilidadeDeSessoesWebSocket` diz expressamente que **Redis não é fonte de sessões STOMP**. Com N > 1 réplicas
  cada uma só enxerga os usuários conectados a ela: "sem sessão" numa réplica não significa "desconectado". **O desenho
  só é correto com 1 réplica.**
* Reinício do backend: o registro nasce vazio e os clientes voltam em segundos a dezenas de segundos (backoff com teto
  de 15 a 30 s, E203). Sem carência de partida, uma varredura logo após o boot marcaria todos OFFLINE. Com `start-first`,
  a instância **velha** ainda roda e vê sessões saindo: ela não pode gravar OFFLINE ao encerrar (guardar contra
  `ContextClosedEvent`).

## 5. Aba em segundo plano e computador em repouso (item 5)

* Heartbeat atual: 10 s / 10 s (`WebSocketConfig.java:93`, `tempo-real.ts:56`); o cliente usa `reconnectDelay: 0` e
  reconecta por conta própria com backoff (`tempo-real.ts:120`).
* **Fonte:** a documentação do STOMP.js 7.3.0 instalado (`node_modules/@stomp/stompjs/esm6/client.d.ts:164-180`) diz que
  a estratégia padrão é `setInterval` e que `TickerStrategy.Worker` é "preferível para reduzir desconexões quando as abas
  estão em segundo plano". O app **não define** `heartbeatStrategy` (`grep` em `frontend/src` vazio), logo usa
  `setInterval`.
* **Não verificado** (precisa de teste em navegador real): o quanto o navegador atrasa esse timer numa aba oculta e se o
  servidor derruba a sessão por heartbeat perdido. Se derrubar, o usuário que trabalha em outra janela some do registro
  e, com a presença automática ligada, vira OFFLINE depois da tolerância — **o mesmo efeito do problema original, no
  sentido inverso (falso OFFLINE)**.
* Computador em repouso/suspenso: o socket morre sem aviso; a detecção é pelo heartbeat do servidor (~20 s) e depois a
  tolerância — comportamento desejado (sai do rodízio).
* **Mitigações possíveis (decisão do responsável):** (a) `heartbeatStrategy: TickerStrategy.Worker` no cliente — não
  muda os 10 s, mas toca o heartbeat; (b) aumentar a tolerância; (c) teste manual de 10 minutos com a aba oculta antes de
  ligar a chave. Não implementei (a) porque o prompt veta mexer no heartbeat.

## 6. Padrão de migration e RLS (item 6)

* Migration Flyway `V{n}__descricao.sql`, número a partir de `origin/main` (PR #265 ocupa o V97; o PR A usa o **V98**).
* RLS só em `lead`, `atendimento`, `lembrete` e tabelas derivadas; `usuario` e `disponibilidade_atendente_ia` **não têm
  RLS** (`AtendenteDisponivelRepositorioJdbc.java:18-20`). O histórico de presença é dado de equipe, sem lead: sem RLS,
  como as tabelas irmãs.
* **Armadilha registrada** (`docs/03:574`): dono de tabela e superusuário ignoram RLS, e o usuário do Flyway pode não
  enxergar linhas de tabela com `FORCE ROW LEVEL SECURITY`; um `INSERT … SELECT` de preenchimento em tabela com RLS
  afeta **zero linhas sem avisar**. Por isso o preenchimento da V98 só lê `usuario` (sem RLS) e tem **teste que executa o
  SQL da própria migration** (`PresencaHistoricoIT`), em vez de confiar que "passou".
* Grants: `ALTER DEFAULT PRIVILEGES … TO synapse_app` (`V13__role_da_aplicacao.sql:40`) cobre tabelas novas.

## 7. Onde usuários são criados (item 7)

* Único caminho de produção: `EquipeRepositorioJdbc.criar` (`:23`) ← `CriarUsuarioUseCase` ← `POST /api/v1/usuarios`.
  **Não criava a linha** de `disponibilidade_atendente_ia`. Só V34 (`ATENDENTE`), V51 (`SUBGESTOR`), o toggle
  (`:31`) e `desativar` (`:28`) escreviam ali. Corrigido no PR A (criar + preenchimento).
* `OPERADOR` (V92) não foi coberto pelos backfills antigos; entra no preenchimento da V98.

## 8. Como a tela mostra e atualiza a presença (item 8)

* O rodapé lê `meuUsuario.data?.presenca` (`sidebar.tsx:185`), da consulta `["me"]` (`use-equipe.ts`), e **só se
  atualiza** quando o próprio usuário clica (`setQueryData`, `sidebar.tsx:161-166`) ou quando a consulta é refeita.
  **Não há push de presença.** Se o sistema mudar o estado sozinho, o rodapé fica errado até o próximo refetch.
* Solução (PR B): publicar um aviso `PRESENCA_ALTERADA` na fila pessoal do usuário (o mesmo caminho do aviso de
  finalização em massa e do `ACESSO_ALTERADO`: Redis `synapse:aviso-usuario` → `/user/queue/notificacoes`) e o frontend
  invalida `["me"]` e `["equipe"]`. A lista de **Equipe de outros** usuários continua por consulta (foco/recarga).
* Lembrete: `tempo-real.ts:404-413` descarta tipos desconhecidos — o tipo novo precisa entrar na lista (já foi o bug da
  finalização em massa).

## 9. Viabilidade (resumo do desenho)

| Regra proposta | Veredito |
|---|---|
| 1. Conectado = ≥ 1 sessão STOMP autenticada; abas contam como uma pessoa | **Viável** (`SimpUserRegistry`, §2). Só com 1 réplica |
| 2. Primeira sessão → ONLINE | **Viável com ajuste:** "primeira" = sem sessão por mais que a tolerância (§0.1) |
| 3. Escolha manual respeitada durante a sessão | **Viável** com o mesmo ajuste |
| 4. Sem sessão por mais que a tolerância → OFFLINE (SISTEMA) | **Viável** com varredura + carência (§0.2, §4) |
| 5. AUSENTE só manual | OK |
| 6. Rodízio inalterado (exige ONLINE) | OK |
| Tolerância 90 s e carência 180 s | Coerentes: tolerância > teto de backoff (30 s) + reconexão; carência > janela de sobreposição do `start-first` (10 s de `delay` + até 60 s de `stop_grace_period` + backoff de 30 s) e o tempo de os clientes voltarem após deploy. Carência de 180 s decidida no E223.1; sem a variante "um ciclo sem sessões de outro". |
| Encerramento ordenado do Spring | Verificado no bytecode do Spring 6.2.19: `AbstractApplicationContext.doClose` publica `ContextClosedEvent` **antes** de `lifecycleProcessor.onClose()`, e `SubProtocolWebSocketHandler.stop()` fecha **todas** as sessões com `CloseStatus.GOING_AWAY` logo no início da parada (não ficam abertas até o timeout). Por isso o `ContextClosedEvent` já marcou `encerrando` quando chegam os `SessionDisconnectEvent`, e a instância que encerra não grava OFFLINE. |

Riscos a decidir: réplicas > 1 (§4), aba em segundo plano (§5), conexão durante a carência com estado manual
(AUSENTE/OFFLINE) — nos primeiros segundos pós-deploy o desenho preserva o estado manual de quem já estava conectado
antes e só promove OFFLINE → ONLINE.

## 10. Sem prova

Comportamento de aba oculta/repouso no navegador; número de réplicas em produção; quanto tempo cada cliente leva para
reconectar após um deploy real; se o servidor derruba sessão com heartbeat perdido nos tempos acima.
