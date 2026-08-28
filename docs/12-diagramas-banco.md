# 12. Diagramas do Banco de Dados

Gerado a partir das migrations V1–V18. Fonte da verdade: o SQL em `backend/crm-app/src/main/resources/db/migration/`.

> Todos os rótulos estão entre aspas e sem acentos — Mermaid quebra com `:`, `(` e `<br/>` em rótulo não aspeado.

---

## 1. Visão geral por contexto

```mermaid
flowchart TB
    subgraph EQ["Equipe"]
        usuario["usuario"]
        refresh_token["refresh_token"]
        avaliacao["avaliacao"]
    end

    subgraph CFG["Configuracao base"]
        canal["canal"]
        canal_credencial["canal_credencial"]
        etapa["etapa_atendimento"]
    end

    subgraph CORE["CRM Core"]
        lead["lead"]
        tag["tag"]
        lembrete["lembrete"]
        timeline["evento_timeline"]
        campo["campo_customizado"]
    end

    subgraph ATD["Atendimento"]
        atendimento["atendimento"]
        mensagem["mensagem"]
    end

    subgraph CAMP["Campanhas - fase 2"]
        campanha["campanha"]
        filtro["filtro_modular"]
    end

    subgraph INFRA["Infra transversal"]
        outbox["outbox_evento"]
        webhook["webhook_entrada"]
        audit["audit_log"]
        flag["feature_flag"]
    end

    usuario --> lead
    usuario --> atendimento
    usuario --> lembrete
    usuario --> refresh_token
    usuario --> avaliacao
    canal --> lead
    canal --> canal_credencial
    canal_credencial --> atendimento
    etapa --> lead
    lead --> atendimento
    lead --> tag
    lead --> timeline
    atendimento --> mensagem
    filtro --> campanha
    lead --> campanha
    mensagem --> outbox
    webhook --> mensagem
```

---

## 2. Equipe

```mermaid
erDiagram
    USUARIO ||--o{ REFRESH_TOKEN : possui
    USUARIO ||--o{ AVALIACAO : recebe
    USUARIO ||--o| DISPONIBILIDADE_ATENDENTE_IA : configura
    USUARIO ||--o{ ROTINA_DISPONIBILIDADE_ATENDENTE : atribuido
    ROTINA_DISPONIBILIDADE ||--o{ ROTINA_DISPONIBILIDADE_ATENDENTE : agrupa

    USUARIO {
        uuid id PK
        varchar nome
        varchar email UK
        varchar senha_hash
        enum papel
        enum status_presenca
        boolean ativo
        timestamptz criado_em
    }

    REFRESH_TOKEN {
        uuid id PK
        uuid usuario_id FK
        varchar token_hash UK
        uuid familia
        timestamptz expira_em
        timestamptz revogado_em
    }

    AVALIACAO {
        uuid id PK
        uuid atendimento_id FK UK
        uuid atendente_id FK
        smallint nota
        text comentario
        timestamptz criado_em
    }

    DISPONIBILIDADE_ATENDENTE_IA {
        uuid atendente_id PK
        boolean disponivel_para_ia
        timestamptz atualizado_em
    }

    ROTINA_DISPONIBILIDADE {
        uuid id PK
        enum dia_semana
        varchar nome
        enum tipo
        boolean ativo
    }

    ROTINA_DISPONIBILIDADE_ATENDENTE {
        uuid rotina_id PK
        uuid atendente_id PK
    }

    HORARIO_TRABALHO {
        uuid id PK
        varchar aplicavel_a
        enum dia_semana
        time inicio
        time fim
    }
```

Notas: `papel` é ATENDENTE, SUBGESTOR, GESTOR ou ADMINISTRADOR. `token_hash` guarda SHA-256, nunca o token. Reuso de refresh revoga a `familia` inteira. `horario_trabalho` tem CHECK de `fim > inicio`.

---

## 3. CRM Core

```mermaid
erDiagram
    LEAD ||--o{ LEAD_TAG : possui
    TAG ||--o{ LEAD_TAG : aplicada
    LEAD ||--o{ LEMBRETE : gera
    LEAD ||--o{ MENSAGEM_PROGRAMADA : gera
    LEAD ||--o{ EVENTO_TIMELINE : registra
    ETAPA_ATENDIMENTO ||--o{ LEAD : classifica
    USUARIO ||--o{ LEAD : responsavel

    LEAD {
        uuid id PK
        varchar nome
        varchar telefone
        varchar email
        varchar cpf
        varchar empresa
        varchar localizacao
        uuid canal_origem_id FK
        enum status_basico
        uuid etapa_atendimento_id FK
        uuid atendente_responsavel_id FK
        text notas
        text resumo_ia
        int num_atendimentos
        int num_mensagens
        timestamptz ultima_interacao_em
        jsonb dados_customizados
        timestamptz criado_em
    }

    TAG {
        uuid id PK
        varchar nome UK
        varchar cor
        varchar icone
    }

    LEAD_TAG {
        uuid lead_id PK
        uuid tag_id PK
    }

    LEMBRETE {
        uuid id PK
        uuid lead_id FK
        uuid atendente_id FK
        text texto
        timestamptz data_hora
        boolean origem_automatica
        enum status
    }

    MENSAGEM_PROGRAMADA {
        uuid id PK
        uuid lead_id FK
        uuid atendente_id FK
        text conteudo
        timestamptz data_envio
        enum status
    }

    EVENTO_TIMELINE {
        uuid id PK
        uuid lead_id FK
        uuid atendimento_id
        varchar tipo
        text descricao
        enum origem
        timestamptz criado_em
    }

    ETAPA_ATENDIMENTO {
        uuid id PK
        varchar nome
        smallint ordem UK
        varchar cor_visual
    }

    CAMPO_CUSTOMIZADO {
        varchar chave PK
        varchar rotulo
        varchar tipo
        jsonb opcoes
        boolean obrigatorio
        boolean filtravel
        smallint ordem
    }
```

Notas importantes:

- `atendente_responsavel_id` é a base do RLS e da Specification de visibilidade
- `notas`, `resumo_ia` e `dados_customizados` **nunca entram em projeção de listagem**
- `num_atendimentos`, `num_mensagens` e `ultima_interacao_em` são denormalizados, escritos na mesma transação da mensagem. O último usa `GREATEST` para não retroceder em reentrega de webhook
- `campo_customizado` não tem FK para `lead` — guarda só metadados; os valores vivem no JSONB. É a extensibilidade da Base PAI sem nichar o core
- `evento_timeline.atendimento_id` usa `ON DELETE SET NULL`: o histórico do lead sobrevive ao atendimento

---

## 4. Atendimento

```mermaid
erDiagram
    LEAD ||--o{ ATENDIMENTO : possui
    CANAL ||--o{ ATENDIMENTO : origem
    CANAL ||--o{ CANAL_CREDENCIAL : versiona
    CANAL_CREDENCIAL ||--o{ ATENDIMENTO : vigente
    USUARIO ||--o{ ATENDIMENTO : atende
    ATENDIMENTO ||--o{ MENSAGEM : contem

    ATENDIMENTO {
        uuid id PK
        uuid lead_id FK
        uuid canal_id FK
        uuid canal_credencial_id FK
        uuid atendente_id FK
        enum status
        timestamptz iniciado_em
        timestamptz finalizado_em
    }

    MENSAGEM {
        uuid id PK
        timestamptz enviado_em PK
        uuid atendimento_id FK
        enum remetente_tipo
        uuid remetente_id
        enum tipo
        text conteudo
        text midia_url
        jsonb midia_metadados
        enum status_entrega
    }

    CANAL {
        uuid id PK
        varchar nome UK
        varchar tipo
        boolean ativo
    }

    CANAL_CREDENCIAL {
        uuid id PK
        uuid canal_id FK
        varchar numero
        varchar identificador_externo
        varchar token_ref
        boolean ativo
        timestamptz vigente_desde
        timestamptz vigente_ate
    }
```

Notas: `mensagem` é particionada por `RANGE (enviado_em)`, com PK composta. `token_ref` é referência ao secret manager, nunca o token. O índice único parcial em `canal_credencial` garante uma credencial ativa por canal; o histórico aponta para a vigente à época, por isso a antiga nunca é deletada.

---

## 5. Infraestrutura transversal

```mermaid
erDiagram
    OUTBOX_EVENTO {
        uuid id PK
        varchar tipo
        jsonb payload
        timestamptz criado_em
        timestamptz publicado_em
        smallint tentativas
        timestamptz proxima_tentativa_em
        text ultimo_erro
    }

    WEBHOOK_ENTRADA {
        varchar id_externo PK
        varchar provedor
        text payload
        timestamptz recebido_em
        timestamptz processado_em
        smallint tentativas
        text ultimo_erro
        timestamptz esgotado_em
    }

    AUDIT_LOG {
        bigint id PK
        uuid ator_id FK
        enum ator_tipo
        varchar acao
        varchar entidade_tipo
        uuid entidade_id
        uuid lead_id
        jsonb dados_antes
        jsonb dados_depois
        inet ip
        timestamptz criado_em
    }

    FEATURE_FLAG {
        varchar chave PK
        boolean habilitado
        text descricao
    }
```

Notas:

- `webhook_entrada.id_externo` como PK **é** a idempotência: reentrega do provedor não duplica mensagem
- `payload` é **TEXT desde a V17**, não JSONB — JSONB normaliza o JSON e a assinatura HMAC deixaria de ser reconferível
- `outbox_evento.publicado_em IS NULL` marca pendente; o índice parcial só varre esses
- `audit_log.lead_id` **não tem FK de propósito** — o log é append-only e precisa sobreviver à exclusão do lead

---

## 6. Fluxo de mensagem — entrada

```mermaid
flowchart TD
    A1["Meta Cloud API envia webhook"] --> A2{"Assinatura HMAC valida?"}
    A2 -->|"nao"| A3["Rejeita com 401"]
    A2 -->|"sim"| A4["Grava em webhook_entrada"]
    A4 --> A5{"id_externo ja existe?"}
    A5 -->|"sim"| A6["Ignora - idempotencia"]
    A5 -->|"nao"| A7["Job agendado processa"]
    A7 --> A8["MetaCloudApiAdapter traduz payload"]
    A8 --> A9["RegistrarMensagemRecebidaUseCase"]
    A9 --> A10["Grava mensagem"]
    A9 --> A11["Atualiza contadores e ultima_interacao_em"]
    A10 --> A12["Evento AFTER COMMIT"]
    A12 --> A13["Publica no Redis"]
    A13 --> A14{"Assinatura autorizada?"}
    A14 -->|"nao"| A15["Nao entrega"]
    A14 -->|"sim"| A16["Tela do atendente"]
```

---

## 7. Fluxo de mensagem — saida

```mermaid
flowchart TD
    B1["Atendente envia"] --> B2{"Janela de 24h aberta?"}
    B2 -->|"nao e texto livre"| B3["Rejeita antes da rede"]
    B2 -->|"sim ou template"| B4["EnviarMensagemUseCase"]
    B4 --> B5["Grava mensagem como PENDENTE"]
    B4 --> B6["Grava linha na outbox"]
    B5 -.->|"mesma transacao"| B6
    B6 --> B7["PublicadorDaOutbox com SKIP LOCKED"]
    B7 --> B8{"Circuit breaker aberto?"}
    B8 -->|"sim"| B9["Permanece na outbox"]
    B8 -->|"nao"| B10["Chama Meta Cloud API"]
    B10 -->|"aceito"| B11["Status vira ENVIADO"]
    B10 -->|"erro"| B12["Retry com backoff"]
    B12 -->|"esgotou tentativas"| B13["ALERTA OUTBOX ESGOTADA"]
    B12 -->|"tenta de novo"| B7
```

> `mensagem` e `outbox_evento` são gravadas na mesma transação. É o padrão Outbox: publicar fora dela permitiria o estado gravado e o evento perdido.

---

## 8. Ciclo de vida da entrega

```mermaid
stateDiagram-v2
    [*] --> PENDENTE
    PENDENTE --> ENVIADO
    PENDENTE --> FALHOU
    ENVIADO --> ENTREGUE
    ENTREGUE --> LIDO
    FALHOU --> PENDENTE
    LIDO --> [*]
```

| Estado | Significado | Na tela |
|---|---|---|
| `PENDENTE` | Gravada na transação, provedor ainda não viu | Relógio |
| `ENVIADO` | Provedor aceitou | ✓ |
| `ENTREGUE` | Chegou no aparelho | ✓✓ |
| `LIDO` | Cliente abriu | ✓✓ azul |
| `FALHOU` | Retry esgotado | ⚠ com ação de reenviar |

`FALHOU` precisa ser visível e acionável — um ✓ mentiroso é pior que um erro honesto.

---

## 9. Particionamento de `mensagem`

```mermaid
flowchart TD
    S["Aplicacao inicia"] --> V{"particoes_mensagem_faltantes"}
    V -->|"falta mes corrente ou proximo"| F["BOOT FALHA - melhor que falhar as 9h da manha"]
    V -->|"janela coberta"| OK["Aplicacao sobe"]

    J["Job mensal"] --> G["garantir_particoes_mensagem com 3 meses"]
    G --> C["criar_particao_mensagem por mes"]

    I["INSERT em mensagem"] --> P{"Existe particao para a data?"}
    P -->|"sim"| N["Particao do mes"]
    P -->|"nao"| D["Cai em mensagem_default"]
    D --> AL["Job diario alerta ALERTA PARTICAO DEFAULT"]
    AL -.->|"drenar antes de criar a particao"| C
```

> A `mensagem_default` é rede de segurança de último recurso. Sem ela, um `INSERT` sem partição falharia e o envio pararia — o que a regra de precedência proíbe. Com ela, vira dívida de manutenção recuperável. **Só vale com alarme:** linhas presas ali impedem anexar a partição daquele mês depois.

---

## 10. Decisão de RLS

```mermaid
flowchart TD
    T["Transacao abre"] --> R["SET LOCAL ROLE synapse_app"]
    R --> N{"Role trocada?"}
    N -->|"nao - dono ou superusuario"| X["RLS IGNORADO - ninguem protegido"]
    N -->|"sim"| CTX{"Contexto definido?"}

    CTX -->|"nenhum"| Z["ZERO LINHAS - falha fechado"]
    CTX -->|"servico"| ALL["Ve todas as linhas"]
    CTX -->|"usuario autenticado"| P{"Qual papel?"}

    P -->|"GESTOR SUBGESTOR ADMINISTRADOR"| ALL
    P -->|"ATENDENTE"| A{"Qual tabela?"}

    A -->|"lead ou atendimento"| L["Proprios mais os em status IA"]
    A -->|"lembrete ou mensagem_programada"| M["Somente os proprios - sem escape de IA"]
```

> **`SET LOCAL ROLE` é o que faz tudo funcionar.** Dono de tabela ignora RLS; superusuário ignora inclusive com `FORCE ROW LEVEL SECURITY`. Sem a troca de role as políticas existem e ninguém está protegido — foi o que aconteceu na E02b, e só os testes negativos expuseram.

---

## 11. Campanhas e Automação

Tabelas existem no schema; UI fora da primeira entrega (`docs/09`).

```mermaid
erDiagram
    FILTRO_MODULAR ||--o{ CAMPANHA : define
    CAMPANHA ||--o{ CAMPANHA_MENSAGEM : possui
    CAMPANHA_MENSAGEM ||--o{ CAMPANHA_MENSAGEM_METRICA : mede
    LEAD ||--o{ CAMPANHA_MENSAGEM_METRICA : alvo

    FILTRO_MODULAR {
        uuid id PK
        varchar nome
        enum contexto
        jsonb criterios
        uuid criado_por_id FK
        timestamptz criado_em
    }

    CAMPANHA {
        uuid id PK
        varchar nome
        uuid filtro_publico_id FK
        timestamptz data_inicio
        timestamptz data_fim
        smallint intervalo_envio_dias
        enum status
        uuid criado_por_id FK
    }

    CAMPANHA_MENSAGEM {
        uuid id PK
        uuid campanha_id FK
        smallint ordem
        text conteudo
        varchar tipo_midia
    }

    CAMPANHA_MENSAGEM_METRICA {
        uuid id PK
        uuid campanha_mensagem_id FK
        uuid lead_id FK
        boolean enviada
        boolean visualizou
        boolean respondeu
        boolean entrou_atendimento
        int num_mensagens_lead
        boolean fechado
    }

    CONFIGURACAO_AUTOMACAO {
        varchar chave PK
        text valor
        varchar unidade
        varchar tipo
        numeric valor_min
        numeric valor_max
        text descricao
        uuid atualizado_por_id FK
        timestamptz atualizado_em
    }
```

Notas: `criterios` é a árvore AND/OR do filtro modular, com índice GIN. `intervalo_envio_dias` tem CHECK de 1 a 7. `configuracao_automacao` é chave-valor tipado de propósito — parâmetro novo é `INSERT`, não migration nem deploy.

---

## 12. Chat interno *(fase 2)*

```mermaid
erDiagram
    CHAT_INTERNO_CONVERSA ||--o{ CHAT_INTERNO_PARTICIPANTE : tem
    CHAT_INTERNO_CONVERSA ||--o{ CHAT_INTERNO_MENSAGEM : contem
    USUARIO ||--o{ CHAT_INTERNO_PARTICIPANTE : participa

    CHAT_INTERNO_CONVERSA {
        uuid id PK
        enum tipo
        timestamptz criado_em
    }

    CHAT_INTERNO_PARTICIPANTE {
        uuid conversa_id PK
        uuid usuario_id PK
    }

    CHAT_INTERNO_MENSAGEM {
        uuid id PK
        uuid conversa_id FK
        uuid remetente_id FK
        enum tipo
        text conteudo
        text midia_url
        jsonb midia_metadados
        timestamptz enviado_em
    }
```
