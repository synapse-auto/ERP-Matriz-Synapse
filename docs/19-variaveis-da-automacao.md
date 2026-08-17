# 19. Variáveis de ambiente da Automação

Referência para a construção dos workflows do n8n. Complementa o `docs/16`, que descreve o
contrato de acesso ao CRM.

---

## 1. O que é seu

Quatro variáveis. Três já estão disponíveis dentro do container do n8n.

| Variável | Origem | Uso |
|---|---|---|
| `SYNAPSE_API_URL` | já no container | Base das chamadas ao CRM |
| `SYNAPSE_TOKEN_INTERNO` | já no container | Credential do tipo **Header Auth** |
| `AUTOMACAO_TOKEN` | já no container | Valida o que o CRM envia ao workflow |
| `AUTOMACAO_WEBHOOK_EVENTOS_URL` | informar o valor | Destino do repasse. Cadastro no ambiente |

## 2. Os dois tokens têm direções opostas

```
n8n  ──  X-Synapse-Token: SYNAPSE_TOKEN_INTERNO  ──▶  CRM
CRM  ──  AUTOMACAO_TOKEN                         ──▶  n8n
```

| Token | Função |
|---|---|
| `SYNAPSE_TOKEN_INTERNO` | Autentica o n8n no CRM. Header `X-Synapse-Token`. O backend recusa a chamada sem ele. |
| `AUTOMACAO_TOKEN` | Prova que o CRM é o remetente do repasse. **Valide este header nos webhooks** — sem isso, qualquer requisição à URL dispara o fluxo. |

Mesmo valor nos dois serviços, um por direção. Trocar um pelo outro resulta em 401.

## 3. Endereços internos

| Variável | Valor | Sentido |
|---|---|---|
| `SYNAPSE_API_URL` | `http://synapse-backend-internal:8080/internal/v1` | n8n → CRM |
| `AUTOMACAO_URL` | `http://synapse-n8n-internal:5678` | CRM → n8n |

Nomes da overlay `synapse-internal`: sem HTTPS e sem domínio. O namespace `/internal/v1` não
possui rota pública — apontar para o domínio do CRM devolve 404. A correção é a URL, nunca a
abertura da rota.

## 4. `AUTOMACAO_WEBHOOK_EVENTOS_URL`

Destino do repasse de eventos. Use a URL interna do webhook do workflow:

```
http://synapse-n8n-internal:5678/webhook/<caminho-do-webhook>
```

- **Opcional por desenho.** Vazia significa repasse desligado, e o CRM opera normalmente. A
  entrada de mensagem nunca depende da Automação estar disponível.
- **Falha de entrega não se resolve sozinha.** O evento vai para a outbox e é retentado com
  circuit breaker. Após 8 tentativas o CRM registra `ALERTA_REPASSE_AUTOMACAO_ESGOTADO`. O
  corpo permanece na outbox, mas **não é reenviado automaticamente** quando o n8n volta.

## 5. Do n8n — não alterar

| Variável | Observação |
|---|---|
| `N8N_DB_NAME`<br>`N8N_DB_USER`<br>`N8N_DB_PASSWORD` | Banco **interno** do n8n: workflows, credenciais, execuções. Não se destina a dado de negócio. Esta role não possui permissão no banco do CRM. |
| `N8N_ENCRYPTION_KEY` | Cifra as Credentials. Alterada, **todas as Credentials salvas deixam de abrir** e precisam ser recadastradas. Por isso o token vai em Credential, e não digitado no nó — valor solto trafega em texto claro no JSON exportado. |
| `N8N_HOST`<br>`WEBHOOK_URL`<br>`N8N_EDITOR_BASE_URL` | Derivam de `AUTOMACAO_DOMINIO`. Mudam com a entrada dos subdomínios reais; webhooks cadastrados em serviços externos precisarão ser refeitos. |

## 6. Não são suas

Presentes no mesmo Environment porque a instância é uma stack única. Nenhuma tem uso legítimo
em workflow.

| Variável | O que é | Por quê |
|---|---|---|
| `POSTGRES_*` | Banco do CRM | Acesso direto contorna RLS, eventos de domínio e outbox |
| `SYNAPSE_JWT_SEGREDO` | Assina o login | Permite forjar sessão de qualquer atendente |
| `WHATSAPP_TOKEN` | Token da Meta | Envia mensagem em nome do cliente, por fora do CRM |
| `WHATSAPP_WEBHOOK_SECRET` | Valida o HMAC da Meta | Do backend |
| `WHATSAPP_WEBHOOK_VERIFY_TOKEN` | Desafio de cadastro | Distinto do anterior; valores iguais fazem toda mensagem ser recusada em silêncio |
| `RABBITMQ_*` · `MINIO_*` | Fila e storage | Internos do CRM |
| `ALERTA_WEBHOOK` | Destino dos alertas | Operação |

Se um fluxo depender de alguma delas, o caminho é solicitar um endpoint em `/internal/v1` —
não utilizar a credencial.

## 7. Isolamento por número

`WHATSAPP_NUMERO` não é o telefone: é o **Phone Number ID** da Meta. Em homologação,
`1307417749115229`.

A Meta inscreve o app no nível da **WABA**, não do número. A conta contém o número oficial e o
de homologação, e o app recebe eventos de ambos. O backend compara o `phone_number_id` de cada
evento com a credencial ativa e descarta o que não pertence ao canal — antes de gravar e
**antes de repassar ao workflow**.

Consequência: evento de outro número nunca chega ao workflow. Repasse esperado que não chega
tem aqui uma das causas possíveis. No go-live o valor passa a ser o ID do número oficial.
