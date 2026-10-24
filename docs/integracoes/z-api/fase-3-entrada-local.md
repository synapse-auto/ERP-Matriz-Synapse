# Z-API — F3 local: tradução, recebimento e correlação (entrada fechada)

24/10/2026. Branch `codex/z-api-entrada-local`, base `codex/z-api-midia-saida` (#323).
Complemento autorizado: implementar/testar entrada local antes da confirmação de G1.
**Não autoriza ativação, autenticação presumida, endpoint permissivo ou homologação real.**
Ordem de merge: #321 → #322 → #323 → PR desta branch. Todas continuam draft; sem merge/deploy.

## Contratos e fixtures

Fontes oficiais reconferidas nesta etapa:

- [ReceivedCallback: campos e exemplos](https://developer.z-api.io/webhooks/on-message-received-examples).
- [DeliveryCallback: campos e exemplos](https://developer.z-api.io/webhooks/on-message-send-examples).
- [MessageStatusCallback](https://developer.z-api.io/webhooks/on-whatsapp-message-status-changes).

Os JSON em `backend/crm-app/src/test/resources/z-api/` reproduzem a estrutura documentada,
com dados inteiramente sintéticos. Não são capturas reais nem prova de comportamento da conta.
`momment` é tratado como milissegundos, sem heurística para segundos ou substituição por agora.
Não foi obtido novo contrato oficial de autenticação. Client-Token continua autenticando
CRM → API, não Z-API → CRM. Não há assinatura HMAC, segredo de URL ou protocolo inventado.

| Evento/campo comprovado | Tradução local | Limite explícito |
|---|---|---|
| ReceivedCallback / text.message | TEXTO; Unicode/quebras preservados | Somente phone numérico, sem converter LID |
| image/audio/video/document | IMAGEM/AUDIO/VIDEO/DOCUMENTO | Tipo do envelope preservado; PNG como document continua DOCUMENTO |
| fromMe | Ignorado como entrada/repasse | Não cria eco nem nova solicitação à IA |
| grupo/newsletter/Status/edição/waitingMessage | Descarte rastreável | Sem interpretação silenciosa como conversa direta |
| DeliveryCallback sem erro | ENVIADO | Não significa entregue/lido |
| DeliveryCallback com error/errorCode | FALHOU; diagnóstico genérico | Conteúdo bruto do erro não entra na mensagem/log |
| MessageStatusCallback SENT/RECEIVED/READ | ENVIADO/ENTREGUE/LIDO | Somente com namespace explicitamente definido nos testes |
| READ_BY_ME/PLAYED/desconhecido | Sem confirmação falsa; descarte | Não são leitura pelo destinatário |

`ids[]` está documentado apenas como identificadores da mensagem; não há confirmação inequívoca
de namespace messageId versus zaapId. Construtor padrão deixa-o indefinido e não aplica esses
status. O teste escolhe WHATSAPP explicitamente para exercitar a correlação, **não** como
afirmação sobre o fornecedor. Delivery usa messageId (documentado como WhatsApp ID), sem
inferir equivalência com zaapId. Confirmação da relação/namespace continua bloqueio da troca.

## Entrada de produção bloqueada

- `ZApiWebhookTradutor` é ACL sem `@Component`/`@Bean` de produção. O seletor continua
  recusando configuração Z-API sem tradutor registrado; `ZApiDesativadaTest` verifica isso.
- Seus verificadores retornam false, mesmo com Client-Token, Bearer, segredo na query ou
  assinatura inventada. Nenhuma rota alternativa/filtro permissivo foi criada.
- O único bean do tradutor está em `@TestConfiguration`. O HTTP real `/webhook/canal` deve
  devolver 403 e não gravar fila. Fixtures entram diretamente na porta durável, sob contexto
  SERVICO e transação de teste; depois chama-se o job como o runtime chama.
- Repasse Z-API para n8n permanece vazio. Não encaminhar payload novo sem alinhar contrato,
  autenticação, reservas e origem no [documento para automação](./documento-para-automacao.md).

## Correlação aditiva e persistência

V111 cria `mensagem_correlacao_canal`: chave composta provedor/instância/namespace/ID bruto,
referência composta de mensagem particionada e status comprovado. Não reescreve tabelas/IDs
históricos. RLS FORCE, exclusiva de SERVICO; nenhum endpoint de usuário dá acesso aos aliases.
Numeração conferida em origin/main (V110 maior) e nas predecessoras; reconferir no rebase.

Hash interno com separadores serve apenas à deduplicação legada; os IDs brutos são preservados
separadamente, sem serem apresentados como IDs nativos do fornecedor. Sem consulta por telefone
para correlacionar status, varredura de outbox ou SELECT de histórico completo.

O status pode chegar antes do aceite: o UPSERT guarda-o sem mensagem, e o vínculo posterior
o aplica na mesma transação da outbox. Locks são por identidade; monotonia segue exatamente
`StatusEntrega.ehPosteriorA`, inclusive FALHOU/LIDO terminais. Duplicados não emitem nova mudança.
Os eventos existentes de mensagem/status alimentam o mecanismo atual de tempo real; teste
observa publicação após commit, não simula cliente STOMP real/reconexão.

Colisão de alias não rouba mensagem e não desfaz aceite externo: registra
`CORRELACAO_RECUSADA` com UUIDs internos e conserva o aceite bruto na outbox para diagnóstico.
A mensagem não confirmada continua PENDENTE; não existe retry automático dessa outbox já
publicada. Falha real de banco/commit depois de efeito externo continua ambiguidade, não
promessa de exactly-once. Não há reconciliação automática por telefone/payload aproximado.

No worker Z-API, a reserva passa a exigir provedor correspondente: backlog Uzapi/Meta não é
traduzido pela ACL Z-API após uma troca futura. Os workers antigos mantêm o caminho existente;
o problema genérico de mudança entre outros provedores não foi ampliado nesta etapa.

## Mídia local não é downloader homologado

O tradutor reconhece referência HTTPS e metadados; a checagem de sintaxe **não protege SSRF**.
Não foi habilitado fetch de URL externa. `ZApiAdapter.baixarMidiaRecebida` continua indisponível.
O IT injeta bytes sintéticos por uma porta simulada, mas usa fila/job, PostgreSQL, MinIO privado
e download autenticado reais. Prova armazenamento/classificação/bytes, não DNS, redirects,
allowlist CDN, codecs, expiração nem entrega real Z-API. Áudio/vídeo sintéticos não provam
reprodução. Downloader limitado/SSRF-safe e fontes de mídia continuam pendentes da F4.

## Evidências e pendências

Dirigidos: tradutor 30/30, seleção fechada 3/3, outbox 14/14, worker 29/29 e regressão Uzapi
18/18 (94); integrações `ZApiEntradaLocalIT` 39/39 e `ZApiSaidaIT` 9/9 (48), PostgreSQL/Redis/
MinIO reais. Incluem isolamento/RLS, os 25 pares de estados e colisão sem reenvio. Clean verify
completo Java 21 e CI do último SHA são registrados no fechamento da PR; execução em curso
não é declarada aprovada por este documento.
Unidades puras adicionais: identidade 7/7 e caso de uso com portas mockadas 3/3.
A primeira iteração falhou por ID fixo compartilhado das fixtures e expectativa da exceção
traduzida pelo Spring; corrigidos no teste. A revisão removeu o rollback por conflito de
alias para não transformar aceite em redisparo. Não chamar isso de falha do provedor.

Antes de ativar: G1 oficial; namespace/reentrega/eventos; downloader seguro; retenção de status
órfãos e reconciliação operacional; repasse n8n/capacidades; carga/reconexão/homologação real;
CI final incluindo bloqueio Zod. Não foi adicionada expiração arbitrária que apague status
antecipado nem polling que tente conciliá-lo por toda a outbox.

**Dokploy: nenhuma ação agora. Z-API OFF.** Nenhuma variável nova, credencial alterada,
produção acessada, número conectado, webhook registrado, mensagem real, merge ou deploy.
