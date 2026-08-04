# Prompt — Diagramas Mermaid do banco atual

> Tarefa curta: ~1 hora. Documentação, não código de produção.
> Pode rodar em paralelo com qualquer etapa.

---

Gere os diagramas Mermaid do schema atual em `docs/12-diagramas-banco.md`.

**Fonte da verdade: as migrations em `backend/crm-app/src/main/resources/db/migration/`** (V1 a V18). O `docs/11-banco-atual.md` serve de guia de leitura, mas onde houver divergência, o SQL vence — ele é o que roda.

Duas armadilhas conhecidas: a V16 cria `webhook_entrada.payload` como JSONB e a **V17 muda para TEXT**; a V5 não cria partições literais, elas vêm de função. Leia todas as migrations em ordem antes de desenhar.

## Por que não um diagrama só

39 tabelas num único ER é ilegível — vira aquele diagrama que todo mundo abre uma vez, não entende, e nunca mais consulta. Gere **um por contexto**, mais um de visão geral.

## Diagramas a produzir

### 1. Visão geral (agregado)

`erDiagram` com os módulos como blocos e só as relações que cruzam fronteira. Não desenhe colunas aqui — o objetivo é caber numa tela e responder "o que fala com o quê".

### 2. Um `erDiagram` por contexto

Com colunas, tipos e cardinalidade:

| Diagrama | Tabelas |
|---|---|
| **Equipe** | `usuario`, `refresh_token`, `avaliacao`, `horario_trabalho`, `rotina_disponibilidade`, `rotina_disponibilidade_atendente`, `disponibilidade_atendente_ia` |
| **CRM Core** | `lead`, `tag`, `lead_tag`, `lembrete`, `mensagem_programada`, `mensagem_rapida`, `evento_timeline`, `preferencia_usuario`, `arquivo_banco`, `campo_customizado`, `etapa_atendimento` |
| **Atendimento** | `atendimento`, `mensagem`, `canal`, `canal_credencial` |
| **Campanhas** | `filtro_modular`, `campanha`, `campanha_mensagem`, `campanha_mensagem_metrica` |
| **Automação** | `configuracao_automacao`, `regra_follow_up`, `regra_fidelizacao`, `mensagem_festiva`, `configuracao_resumo_ia`, `status_automacao_telemetria` |
| **Chat interno** | `chat_interno_conversa`, `chat_interno_participante`, `chat_interno_mensagem` |
| **Infra transversal** | `audit_log`, `feature_flag`, `outbox_evento`, `webhook_entrada` |

Convenções:

- `PK` e `FK` nos atributos
- Cardinalidade real (`||--o{`, `}o--o{`), lida das constraints
- Comentário curto onde a coluna tem armadilha — ex.: `token_ref` é referência, não token; `payload` é TEXT para reverificação de HMAC; contadores do lead são denormalizados

### 3. Fluxo de mensagem (`flowchart`)

Do webhook da Meta até a tela do atendente, e o caminho de volta. Deve mostrar onde estão a idempotência, a outbox, o publisher e o WebSocket. É o diagrama que mais vai ser consultado — é o caminho crítico do produto.

### 4. Ciclo de vida de `status_entrega` (`stateDiagram-v2`)

`PENDENTE → ENVIADO → ENTREGUE → LIDO`, com `FALHOU` e o caminho de reenvio.

### 5. Particionamento de `mensagem` (`flowchart`)

A janela relativa a `now()`, as três funções, a verificação de boot e a `mensagem_default` como rede de segurança com alarme.

### 6. Decisão de RLS (`flowchart`)

Da abertura da transação até a linha ser visível ou não: `SET LOCAL ROLE` → contexto (autenticado / serviço / nenhum) → política por papel → resultado. Deixe explícito que **sem contexto retorna zero linhas**.

## Restrições

- **Mermaid válido.** Verifique a sintaxe; um diagrama que não renderiza é pior que nenhum. Atenção a acentos e parênteses em rótulos.
- **Nada inventado.** Se uma relação não existe como FK no SQL, não a desenhe. Relação implícita (ex.: `audit_log.lead_id` sem FK) deve aparecer com nota explicando que é desnormalizada de propósito.
- Marque as tabelas **fora da primeira entrega** (campanhas, chat interno, `arquivo_banco`) — elas existem no schema mas não têm UI. Ver `docs/09`.
- Português, coerente com o resto da documentação.

## Definição de pronto

- [ ] `docs/12-diagramas-banco.md` com os 6 grupos
- [ ] Toda tabela das migrations aparece em pelo menos um diagrama
- [ ] Sintaxe Mermaid válida em todos
- [ ] Nenhuma relação desenhada que não exista no SQL

Commit: `docs: diagramas mermaid do banco`.

Ao terminar, me diga se alguma relação do schema te pareceu estranha ao desenhar — diagrama costuma expor modelagem torta que passa despercebida no SQL linha a linha.
