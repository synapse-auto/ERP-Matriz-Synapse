# 49. Custo das consultas do CRM — diagnóstico e correções

Origem: `pg_stat_statements` da instância `erp-matriz-hml-oxj4cd`, coletas de 28/09/2026
09:11:05 → 09:39:50 (≈ 28 min 45 s). Estatísticas acumuladas desde 26/09 00:37 **não** foram usadas
como carga atual.

## 1. Limites desta análise — leia antes das conclusões

- **Sem acesso ao banco da instância.** Não houve nova coleta de deltas nem `pg_stat_activity`,
  CPU, memória ou I/O. Os números de produção abaixo são os do prompt.
- **Sem o texto normalizado das consultas.** O mapeamento Query ID → código foi feito pela
  descrição de cada consulta e confirmado pela frequência quando possível. Onde há mais de um
  candidato, está dito.
- **Planos medidos em banco local descartável** (`synapse_crm_perf`, PostgreSQL 15 do
  `docker-compose`), com o schema real até a V83 e dados sintéticos: 30 usuários, 200 mil leads,
  200 mil atendimentos, 1,5 milhão de linhas em `outbox_evento` (480 MB), 455 conversas internas e
  300 mil mensagens internas. A distribuição é hipótese; ordens de grandeza e **formas de plano**
  valem, tempos absolutos não se transferem para produção.
- **Todas as medições de consulta sob RLS foram feitas como a aplicação faz**: `SET LOCAL ROLE
  synapse_app` + `set_config('app.usuario_id'|'app.papel', …, true)`. Um `EXPLAIN` como dono da
  tabela ignora RLS e mostraria planos que a aplicação nunca executa (ver §3.3).

## 2. Mapa Query ID → origem

| Query ID | Consulta (prod.) | Origem no código | Disparador | Frequência medida em prod. |
|---|---|---|---|---|
| `2248583991785613236` | `SELECT count(*) FROM outbox_evento` | `VerificadorFilaOutbox.verificar()` | `AgendadorDaSaudeCritica`, `SAUDE_INTERVALO_MONITORAMENTO=30s` | 57 em 28m45s = 1/30s — **bate exatamente** com o agendador |
| `-7564574167221481291` | Listar conversas do chat interno | `ChatInternoRepositorioJdbc.listarConversas` (variante **sem** paginação, `ORDER BY COALESCE(ultima.enviado_em, c.criado_em)`) ← `GET /api/v1/chat-interno/conversas` | Tela Chat interno e painel de chat dentro de Atendimentos; refetch a cada evento de tempo real do chat (§3.2) | 2.234 (≈ 1,3/s) |
| `4886562838780657537` | "Buscar lead por telefone" | **Incerto.** Candidato A: `SELECT id FROM lead WHERE telefone = ? ORDER BY criado_em LIMIT 1` (webhook, novo contato, reação). Candidato B: `SELECT * FROM app_buscar_lead_para_entrada(?, ?)` ← `GET /api/v1/leads/busca-entrada` | A: cada mensagem recebida; B: digitação na busca de "pedir entrada" | 74, ~1,17 s |
| `-5927214011329185405` / `5123844212923732205` | Contar / listar leads por nome, telefone ou CPF | `LeadRepositorioJpa.contar/listar` com `InterpretadorDeCriterio` (`lower(col) LIKE ? ESCAPE '\'`) | Busca da Agenda/Leads | 4 + 4, ~2,4–2,5 s |
| `802094523090455065` | Listar atendimentos | `PainelDeAtendimentosRepositorioJdbc` | Painel (inbox paginada) e `GET /atendimentos?visao=` sem paginação | 455, ~98 ms |

Para fechar o candidato do `4886562838780657537`, basta o `query` dessa linha em
`pg_stat_statements` (texto normalizado, sem parâmetros).

## 3. Evidências por consulta

### 3.1 Outbox — `count(*)` sem uso

`VerificadorFilaOutbox` executava `SELECT count(*) FROM outbox_evento` e **descartava o valor**.
A garantia real era "a fila é acessível pelo pool do chat" (conexão, tabela, permissão, lock). Não
existe `DELETE FROM outbox_evento` no código de produção: a tabela só cresce, e o `count(*)` fica
mais caro a cada dia (prod: +904 mil `shared_blks_read` em 57 chamadas ≈ 15,9 mil blocos por
execução).

| Local, 1,5 M linhas | Plano | Tempo | Buffers |
|---|---|---|---|
| `SELECT count(*) FROM outbox_evento` | Parallel Seq Scan | 216 ms | 53.572 (read 50.632) |
| `SELECT 1 FROM outbox_evento LIMIT 0` | Limit → Seq Scan *(never executed)* | 0,014 ms | 3 |

`LIMIT 0` ainda resolve a relação e verifica permissão no planejamento e abre a tabela com
`AccessShareLock` no executor: tabela ausente, sem permissão ou sob lock exclusivo continua falhando.

### 3.2 Chat interno — chamada em dobro + custo da RLS por linha

**Frequência.** Três ouvintes de tempo real invalidam o prefixo inteiro `["chat-interno"]` para o
mesmo evento: `NotificacoesTempoReal` (no `app/layout.tsx`, sempre montado), `PaginaChatInterno` e
`PainelConversaInterna`. Cada `invalidateQueries` sobre consulta ativa dispara um novo GET.

| E2E `chat-interno-refetch.spec.ts` (Chromium, WebSocket real) | GET da lista por mensagem recebida |
|---|---|
| Antes | **2** (3/3 execuções) |
| Depois (ouvinte global é o único a invalidar) | **1** (3/3 execuções), mensagem continua aparecendo |

**Custo por chamada.** Plano local (36 conversas, ~660 mensagens cada), como `synapse_app`:

| Condição | Tempo | Onde está o tempo |
|---|---|---|
| Com RLS (como a aplicação roda) | **448 ms** | SubPlan `nao_lidas`: 12,1 ms × 36 = 436 ms; lê as ~659 mensagens da conversa pelo índice `(conversa_id, enviado_em)` só por `conversa_id` e avalia `app_chat_participa(conversa_id)` em cada linha; descarta 652 |
| Mesmo SQL como dono (sem RLS) | **4,2 ms** | `enviado_em > lido_ate` vira condição do índice; 7 linhas por conversa |

A política é `USING (app_chat_participa(conversa_id))`, função SQL `SECURITY DEFINER` com
`SET search_path` — não pode ser *inlined*, roda por linha e impede que o filtro de data restrinja o
índice antes dela. Reescrever `nao_lidas` com `LATERAL` não mudou o plano (431 ms).

### 3.3 Busca de leads — a RLS impede qualquer índice

`lower`, `textlike` e `texticlike` **não são leakproof** (`pg_proc.proleakproof = false`);
`texteq` é. Sob RLS, predicado não leakproof não pode virar condição de índice antes da política.

| Local, 200 mil leads, índice experimental `gin (lower(nome) gin_trgm_ops)` | Plano | Tempo |
|---|---|---|
| Como dono (sem RLS) | Bitmap Index Scan no trigram | **3,4 ms** |
| Como `synapse_app`, GESTOR | Seq Scan (índice ignorado) | **904 ms** |
| Busca completa `nome OR telefone OR cpf`, `synapse_app` | Seq Scan | **979 ms** |

Todos os buffers já em memória: o custo é **CPU da avaliação da política por linha** (várias
`current_setting` e subplanos), não I/O. **Um índice novo não resolve** esta consulta para a
aplicação.

### 3.4 Lead por telefone

| Local, `synapse_app` | Plano | Tempo |
|---|---|---|
| A — `telefone = ? ORDER BY criado_em LIMIT 1` (existente ou não) | Index Scan `ux_lead_telefone` | 0,37 ms |
| B — `app_buscar_lead_para_entrada('Maria Silva 12', ana)` | Seq Scan em `atendimento` + `regexp_replace` por lead | 502 ms |

A não explica 1,17 s. B é compatível. B tem ainda um **defeito de correção**: com termo sem
dígitos, `regexp_replace(p_termo,'[^0-9]','','g')` vira `''`, o predicado de telefone vira
`LIKE '%%'` e casa com todo lead com telefone — o filtro por nome fica sem efeito.

### 3.5 Listagem de atendimentos

O painel usa `GET /atendimentos/inbox` (paginado, 50). A lista legada `GET /atendimentos?visao=`
**não é paginada** e é usada por três diálogos (encaminhar, lembrete, mensagem programada, todos com
`visao=TODOS`) e por visões sem inbox. No banco sintético (≈ 80 mil atendimentos abertos, proporção
irreal), `visao=TODOS` devolveu 42 MB em ~5 s. Em produção a média é 98 ms, então o volume real é
bem menor. Não alterado: é risco de crescimento, não causa comprovada do custo atual.

## 4. Prioridade

1. **Chat interno** — maior tempo total (162,7 s no intervalo). Chamada dobrada comprovada e
   corrigida; o custo por chamada depende da RLS (ponto de parada, §5).
2. **Busca de lead "por telefone"** (86,5 s) — depende do texto do Query ID para fechar a origem.
3. **Listar atendimentos** (44,4 s) — investigado; sem alteração.
4. **Busca de leads nome/telefone/CPF** (19,8 s, 8 chamadas) — causa demonstrada (RLS), correção
   é ponto de parada.
5. **Outbox** (4,3 s, mas I/O crescente) — corrigido.

## 5. Pontos de parada — precisa de decisão

| Consulta | Alternativa | Por que não implementei |
|---|---|---|
| Chat `nao_lidas` | (a) Função de política que não seja `SECURITY DEFINER`/`SET search_path`, para poder ser *inlined*; (b) contagem de não lidas por função `SECURITY DEFINER` que verifica a participação uma vez e conta sem RLS por linha; (c) contador materializado de não lidas por participante | Todas mexem na proteção de acesso do chat |
| Busca de leads | (a) Função leakproof de busca (`CREATE FUNCTION … LEAKPROOF` exige superusuário e revisão de segurança); (b) reescrever a política `rls_lead` com `(SELECT app_papel())` etc. para avaliar o contexto uma vez por consulta (InitPlan) em vez de por linha; (c) contagem aproximada/sem `COUNT` exato | (a)/(b) alteram a política de visibilidade de leads (RN-CRM-01); (c) muda a semântica do total |
| `app_buscar_lead_para_entrada` | Nova migration: só comparar telefone quando o termo tem dígitos; limitar por prefixo/índice | Corrige o defeito de correção, mas muda o resultado da busca |
| Outbox sem limpeza | Retenção de eventos publicados | Regra de negócio da outbox |

## 6. Implantação

- **Sem migration e sem variável nova.** As duas correções são código (backend e frontend).
- Nenhuma operação síncrona nova no caminho de mensagem; nenhuma alteração de RLS, pools ou
  configuração do banco.
- Pode subir em horário de atendimento pelo processo normal, mas a recomendação da casa continua
  sendo fora de 08:00–18:30.
- Para medir o efeito em produção: repetir a coleta de deltas de `pg_stat_statements` (mesma duração)
  para os Query IDs `2248583991785613236` e `-7564574167221481291` depois do deploy. Não use
  `pg_stat_statements_reset()`.
