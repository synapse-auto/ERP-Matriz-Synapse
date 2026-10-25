# Z-API — saída experimental de texto (Fase 2)

24/10/2026. Branch `codex/z-api-fase-2-saida`, baseada no SHA 1990dbe da
[PR #321](https://github.com/synapse-auto/ERP-Matriz-Synapse/pull/321), ainda aberta.
**Não ativar nem trocar a Uzapi.** Não há autenticação/tradutor de entrada implementados.
Esta entrega é preparação independente; não conclui a integração ou homologação real.

## Contrato implementado e fontes verificadas

- [Texto](https://developer.z-api.io/message/send-text): POST HTTPS na API oficial,
  `/instances/{instanceId}/token/{token}/send-text`, JSON `phone`/`message`, header
  `Client-Token`. Sem campos extras, edição, delay ou Idempotency-Key inventado.
- [IDs](https://developer.z-api.io/message/introduction): `zaapId` é processamento Z-API;
  `messageId` é mensagem WhatsApp. `id`, quando presente, precisa ser igual a messageId.
  Ambos os IDs obrigatórios são preservados distintos e opacos, com a instância.
- [Saúde](https://developer.z-api.io/instance/status): GET `/status`, consulta operacional
  explícita, nunca pré-condição síncrona de cada mensagem. `connected` boolean true é a
  evidência de conexão; `smartphoneConnected` informa a internet do celular, não a conexão
  da instância. **`error` é também texto descritivo**, inclusive “You are already connected”;
  não recusamos conexão comprovada apenas por esse campo preenchido.
- Citação documentada é `/send-text` + `messageId`, não `/reply-message`. Nesta revisão
  é recusada antes da rede porque a origem/namespace do contexto ainda não está garantida.

## Resultado, persistência e recuperação

| Situação | Resultado | Persistência / retry |
|---|---|---|
| 2xx com dois IDs válidos/coerentes | `Enfileirado` | `publicado_em` fecha somente a entrega da intenção ao fornecedor. JSON `aceiteNaFila` guarda provedor/instância/idDoProcessamento/idDaMensagem. Mensagem permanece PENDENTE, não ENVIADO/ENTREGUE/LIDO. |
| Breaker aberto / bulkhead cheio antes do HTTP | `Recusado.temporario` | Backoff existente da outbox, sem chamada nem espera bloqueante no semáforo. |
| Timeout/exceção após iniciar POST, 3xx/4xx/429/5xx, JSON inválido/contraditório/grande demais | `Ambiguo` | E209: `despacho_ambiguo_em` + `esgotado_em`; sem retry automático, diagnóstico “Não foi possível confirmar o envio”. Conferir antes de reenvio humano. |
| Configuração ausente, credencial divergente, destino inválido, mídia/template/citação nesta fase | `Recusado.permanente` | Sem HTTP. Não oferece controles Z-API novos. |

Não há garantia oficial verificada de idempotência dos POSTs: tratamento conservador também
para 4xx evita assumir ausência de efeito somente pelo status. Isso não significa que toda
recusa produziu envio. Status/corpo bruto não são mostrados ao usuário.
Outbox antiga não ganha falsa origem. A variante Aceito e os retries Meta/Uzapi permanecem
inalterados. Nenhum registro histórico ou relacionamento é migrado.

IDs ficam no JSON durável da intenção, não no índice global `mensagem_id_externo`.
Isso preserva evidência sem afirmar correlação isolada G3: callbacks, status antecipados,
eco e aliases exigem a próxima fase. Nesta revisão desligada, aceite fica PENDENTE;
não existe confirmação automática nem polling de mensagens.

## Configuração e segurança

Variáveis opcionais em `.env.example`, README e `docker/dokploy-stack.yml`:

| Variável | Default / exemplo sintético | Regra |
|---|---|---|
| `Z_API_HABILITADO` | `false` | Manter OFF. Não mudar WHATSAPP_PROVEDOR. |
| `Z_API_INSTANCIA_ID` | vazio / `instancia-teste` | Identidade, não telefone. Deve coincidir com `WHATSAPP_NUMERO` para validar a origem capturada na outbox. Divergência bloqueia sem HTTP. |
| `Z_API_TOKEN` | vazio | Segredo no path, somente backend. |
| `Z_API_CLIENT_TOKEN` | vazio | Header de saída, **não** autentica callback. |
| `Z_API_CREDENCIAL_ID` | vazio / UUID sintético | Referência CRM deve coincidir exatamente com a da intenção. |
| `Z_API_LIMITE_RESPOSTA_BYTES` | `4096` | Teto configurável só do JSON pequeno de IDs/saúde, não de mídia. |

Timeout/concorrência reutilizam `WHATSAPP_TIMEOUT` (10s padrão) e `OUTBOX_CONCORRENCIA`
(4 padrão). Timeout deve ficar abaixo de `OUTBOX_RESERVA_EXPIRACAO` (30s padrão).
Sem configuração, bean de saída ausente e zero rede. Habilitar o bean não troca o canal;
selecionar z-api falha fechada por ausência de tradutor, sem verificador permissivo.

Cliente HTTP dedicado, sem interceptadores globais/observation de URI; não usa builder
instrumentado da aplicação. HTTPS somente no host oficial, redirects NEVER, connect/read
timeouts, resposta limitada antes de parsear e sem log de payload. Streaming de request
evita logBody DEBUG do RestClient. Exceções são substituídas **antes** do breaker, sem
causa com URL/token; listeners recebem código sanitizado. Métricas do breaker usam nomes
fixos, não URI. Não executar wire logging/JDK HTTP dumps, gravação de headers ou agente
externo de rede sem redação: essas camadas não são controladas pelo adaptador. APM/JFR e
instrumentação externa exigem verificação antes de qualquer ativação.

O stream é fechado antes do auto-close da resposta: `JdkClientHttpResponse.close()` também
drena bytes restantes (conferido no bytecode Spring Web 6.2.19). Teste negativo reproduziu
leitura acima do teto em 2xx e 5xx; ambos param no limite/sem ler corpo de erro após a correção.

Tokens nunca vão ao banco/outbox/navegador. UUID evita envio com credencial diferente;
não resolve secret store histórico. Rotação deve criar referência versionada, não reutilizar
UUID com outro remetente. Programadas futuras e pendências legadas mantêm o gate do runbook.

## Evidências e limites

- RED: testes novos não compilavam pela ausência de adapter/variantes; log local ignorado.
- HTTP simulado: corpo/headers/IDs, newline/Unicode, JSON inválido, redirects/4xx/429/5xx,
  timeout, limite, breaker/bulkhead, credencial/destino, saúde e DEBUG sem segredos.
- Configuração/seleção: OFF por ausência; não substitui Meta; Z-API sem tradutor falha.
  Não é teste de autenticidade de callback.
- PostgreSQL: worker preserva IDs/instância, não confirma entrega, suspende ambiguidade,
  não sobrescreve resultado encerrado e não reenvia. API + job + adapter + HTTP simulado
  cobrem clique duplicado e negativa de acesso. Tradutor negador existe **só no teste**.
- Primeira iteração do IT específico encontrou FK de limpeza do próprio fixture
  (`mensagem_envio_idempotencia`); limpeza corrigida, sem alterar regra/tabela de produção.
- Revisão detectou drain no close do transporte: teste RED 2/2 antes da correção.
  A primeira execução completa foi interrompida voluntariamente para corrigir; não é
  relatada como sucesso. O clean verify final precisa terminar depois dessa alteração.
- Clean verify Java 21 e CI do último SHA são registrados na PR/relatório de fechamento;
  não usar resultados da predecessora como comprovação desta revisão.

Sem recebimento/status/correlação G3, LID-only/quarentena, mídia/SSRF, automação ponta a ponta
ou homologação real. F3–F5 não concluídas. G1 exige contrato oficial. CI frontend bloqueado
por Zod é pendência aceita para registro, não autorização de upgrade major ou remoção de auditoria.

**Ação no Dokploy agora: nenhuma.** Defaults OFF/vazios; nenhuma variável obrigatória nova.
Futuro preenchimento/ativação exige gates e homologação separados. Não executar runbook.
