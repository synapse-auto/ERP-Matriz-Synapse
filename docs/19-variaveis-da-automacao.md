# 19. Variáveis de ambiente da Automação

Referência para a construção dos workflows do n8n. Complementa o `docs/16`, que descreve o
contrato de acesso ao CRM.

---

## 1. O que é seu

As variáveis abaixo são separadas por direção. As credenciais devem ser cadastradas no ambiente do
Dokploy e nunca no repositório.

| Variável | Origem | Uso |
|---|---|---|
| `SYNAPSE_API_URL` | já no container | Base das chamadas ao CRM |
| `SYNAPSE_TOKEN_INTERNO` | já no container | Credential do tipo **Header Auth** |
| `AUTOMACAO_TOKEN` | já no container | Valida o repasse geral de eventos do CRM; não é usado pelo webhook de resumo |
| `AUTOMACAO_RESUMO_IA_URL` | informar o valor | Webhook que recebe a solicitação assíncrona de resumo; vazio mantém o recurso desligado |
| `AUTOMACAO_RESUMO_IA_TOKEN` | informar o valor no CRM e no n8n | Segredo do webhook de resumo; nunca registrar ou versionar o valor |
| `AUTOMACAO_RESUMO_IA_AUTH_HEADER` | `CRM-Synapse-RES` | Nome do header do webhook de resumo; deve ser igual nos dois serviços |
| `AUTOMACAO_WEBHOOK_EVENTOS_URL` | informar o valor | Destino do repasse. Cadastro no ambiente |

## 2. Os dois tokens têm direções opostas

```
n8n  ──  X-Synapse-Token: SYNAPSE_TOKEN_INTERNO  ──▶  CRM
CRM  ──  AUTOMACAO_RESUMO_IA_TOKEN (CRM-Synapse-RES) ──▶  n8n (resumo)
```

| Token | Função |
|---|---|
| `SYNAPSE_TOKEN_INTERNO` | Autentica o n8n no CRM. Header `X-Synapse-Token`. O backend recusa a chamada sem ele. |
| `AUTOMACAO_TOKEN` | Prova que o CRM é o remetente do repasse geral. **Valide este header no webhook correspondente**. |
| `AUTOMACAO_RESUMO_IA_TOKEN` | Prova que o CRM é o remetente do webhook de resumo; valide no header `CRM-Synapse-RES` (ou no nome configurado). |

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

## 5. Operação do scheduler do CRM (E177)

Estas variáveis pertencem ao container do backend, não ao workflow do n8n. O job nasce desligado por
instância: `configuracao_automacao.atendimento.finalizar_inativos.habilitado` (migration V72) tem
valor `false` em todas as instâncias. Altere essa configuração para `true` no CRUD do CRM somente
após decisão operacional; a leitura ocorre a cada rodada, então não há redeploy. O limiar de negócio
é editado em `configuracao_automacao.atendimento.finalizar_apos_horas` (migration V70, padrão 24
horas). As variáveis abaixo controlam somente cadência e tamanho de lote:

| Variável | Default | Uso |
|---|---:|---|
| `ATENDIMENTOS_FINALIZAR_INATIVOS_LOTE` | `50` | Máximo de candidatos processados em uma rodada. |
| `ATENDIMENTOS_FINALIZAR_INATIVOS_INTERVALO_MS` | `300000` | Intervalo entre rodadas (5 minutos). |

Não é necessário configurar nada no n8n para esse job. O padrão seguro é `false` tanto na Estrutural
quanto na FMNA; somente a gestão de cada instância pode optar por ligar. `EM_IA` não é finalizado automaticamente;
somente conversas humanas sem interação acima do limiar retornam ao estado finalizado, prontas para
que uma nova mensagem do cliente abra outro atendimento em IA.

## 6. Do n8n — não alterar

| Variável | Observação |
|---|---|
| `N8N_DB_NAME`<br>`N8N_DB_USER`<br>`N8N_DB_PASSWORD` | Banco **interno** do n8n: workflows, credenciais, execuções. Não se destina a dado de negócio. Esta role não possui permissão no banco do CRM. |
| `N8N_ENCRYPTION_KEY` | Cifra as Credentials. Alterada, **todas as Credentials salvas deixam de abrir** e precisam ser recadastradas. Por isso o token vai em Credential, e não digitado no nó — valor solto trafega em texto claro no JSON exportado. |
| `N8N_HOST`<br>`WEBHOOK_URL`<br>`N8N_EDITOR_BASE_URL` | Derivam de `AUTOMACAO_DOMINIO`. Mudam com a entrada dos subdomínios reais; webhooks cadastrados em serviços externos precisarão ser refeitos. |

## 7. Não são suas

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

## 8. Isolamento por número

`WHATSAPP_NUMERO` não é o telefone: é o **Phone Number ID** da Meta. Em homologação,
`1307417749115229`.

A Meta inscreve o app no nível da **WABA**, não do número. A conta contém o número oficial e o
de homologação, e o app recebe eventos de ambos. O backend compara o `phone_number_id` de cada
evento com a credencial ativa e descarta o que não pertence ao canal — antes de gravar e
**antes de repassar ao workflow**.

Consequência: evento de outro número nunca chega ao workflow. Repasse esperado que não chega
tem aqui uma das causas possíveis. No go-live o valor passa a ser o ID do número oficial.
