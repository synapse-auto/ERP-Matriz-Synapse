# Z-API — auditoria de prontidão (24/10/2026)

**Pode trocar a Uzapi na Femina nesta revisão: não.** Pronto no código: não.
Homologado na conta Z-API real: não. Nenhum acesso à VPS/Dokploy, conexão de número,
registro de callback, envio real, replay, merge ou deploy foi realizado.

## 1. Entrega encontrada

- Base auditada: `origin/main` **61a7c6f93c0b6840caa50f60f27e1ab7c437b0d0**.
- [PR #301](https://github.com/synapse-auto/ERP-Matriz-Synapse/pull/301) incorporada em
  13/10/2026: planejamento, contrato e dez fixtures sintéticas. Não é um adaptador.
- A pesquisa de PRs abertas/fechadas e branches encontrou somente `codex/z-api-fase-1`.
  Não foram localizadas entregas das fases 2–5. Nenhum `ZApiAdapter`/`TradutorDeCanal`
  Z-API está registrado no código da base auditada.
- O seletor exige gateway **e** tradutor com a mesma chave. Selecionar `z-api` nesta base
  falha no startup; não ativa um canal funcional. Não alterar `WHATSAPP_PROVEDOR` agora.
- Esta entrega é uma **preparação incremental**, não cinco fases concluídas. A fase 1 já
  foi mergeada, portanto a branch desta auditoria parte de main, sem duplicar a PR #301.

## 2. Matriz de paridade

“Nenhum teste Z-API” significa que fixtures/documentação não exercitam o ponto de entrada.
Os testes de gateway fake e de Meta/Uzapi não comprovam funcionamento da conta Z-API.
Todas as fontes abaixo foram consultadas em 24/10/2026.

| Capacidade | Implementação atual | Fonte oficial | Evidência de teste / lacuna | Bloqueia troca? |
|---|---|---|---|---|
| Texto, acentos, emoji, newline | Sem adaptador Z-API | [send-text](https://developer.z-api.io/message/send-text) | Fixture apenas; falta HTTP simulado pelo worker, IDs e erro ambíguo | Sim |
| Imagem + legenda | Sem envio/recebimento Z-API | [imagem](https://developer.z-api.io/message/send-message-image) | Falta storage + transferência limitada e validação de bytes | Sim |
| Áudio composer/anexo | Sem envio/recebimento Z-API | [áudio](https://developer.z-api.io/message/send-message-audio) | Falta testar codec/duração/reprodução; `async` não confirma entrega | Sim |
| Vídeo + legenda | Sem envio/recebimento Z-API | [vídeo](https://developer.z-api.io/message/send-message-video) | Falta teste de bytes, H.264/limite real, memória e download | Sim |
| Documento, inclusive JPG/PNG como documento | Sem adaptador; preservar tipo DOCUMENTO do envelope, não inferir por MIME | [documento](https://developer.z-api.io/message/send-message-document), [entrada](https://developer.z-api.io/webhooks/on-message-received-examples) | Falta MIME/nome/bytes/storage/autorização ponta a ponta | Sim |
| Citação | `contextoWamid` existente não prova namespace compatível | [resposta](https://developer.z-api.io/message/reply-message) | POST real é `/send-text` + `messageId`; falta correlação e política para histórico Uzapi | Sim |
| Encaminhar, inclusive Chat Interno → cliente | Reutiliza conteúdo/storage e gateway atuais, não ID de mensagem interna na Z-API | [forward](https://developer.z-api.io/message/forward-message) | Forward nativo exige mensagem do provedor; falta teste do fluxo CRM pelo novo gateway | Sim |
| Programadas | Materializam outbox no momento de execução | Gateway e worker do CRM; [envio](https://developer.z-api.io/message/send-text) | Origem futura ainda depende da configuração na execução; corte exige decisão/inventário | Sim |
| Contato/localização/reação | Sem implementação Z-API/capacidades correspondentes | [contato](https://developer.z-api.io/message/send-message-contact), [localização](https://developer.z-api.io/message/send-message-location), [reação](https://developer.z-api.io/message/send-message-reaction) | Mapear cada controle real; não habilitar por semelhança com Uzapi | Sim, se controle oferecido |
| Foto de perfil | Infra CRM existe, sem consulta Z-API | [foto](https://developer.z-api.io/contacts/get-profile-picture) | GET `/profile-picture?phone=...`; falta download seguro e cache | Sim para oferecer a ação; pode ficar explicitamente indisponível |
| Autenticação/conexão | Sem cliente Z-API | [status](https://developer.z-api.io/instance/status), [Client-Token](https://developer.z-api.io/security/client-token) | Credenciais de saída documentadas; não comprovam origem dos callbacks | Sim |
| Entrada, eco, tipos adicionais | Sem tradutor Z-API | [recebimento](https://developer.z-api.io/webhooks/on-message-received), [exemplos](https://developer.z-api.io/webhooks/on-message-received-examples) | G1 aberto; G2: telefone apenas, LID-only rastreável sem lead fictício | Sim |
| Aceite/processamento/entrega/leitura | Sem mapper Z-API | [mensagens](https://developer.z-api.io/message/introduction), [status](https://developer.z-api.io/webhooks/on-whatsapp-message-status-changes) | Falta guardar `zaapId` separado de `messageId`; `ids[]` requer confirmação de namespace | Sim |
| Callback repetido/antecipado/fora de ordem | IDs globais atuais não bastam para garantir corte | [webhooks](https://developer.z-api.io/webhooks/introduction) | Falta chave canal/instância/namespace e armazenamento de status antecipado | Sim |
| Timeout/5xx sem idempotência comprovada | E209 cobre interrupção do worker, não todo retorno ambíguo | [texto](https://developer.z-api.io/message/send-text) | Não retentar POST incerto; falta resultado tipado integrado à persistência E209 | Sim |
| n8n | Workflows publicados não disponíveis no repositório | Contratos CRM docs/16, docs/50, docs/51 | Envelope Uzapi não equivale ao Z-API; chamadas diretas e filas externas não auditadas | Sim |

## 3. Correção independente entregue

Cada **nova** intenção `canal.mensagem.enviar` passa a gravar no JSON da mesma outbox:

- `provedorOrigem`: chave de `synapse.canal.whatsapp.provedor`;
- `identificadorCanalOrigem`: `numero-principal`, normalizado apenas de null para vazio;
- `credencialId` preexistente é preservado, sem alteração de UUID/relacionamentos.

O publicador compara a origem com gateway/configuração ativos **antes de compor a mídia,
marcar despacho ou chamar rede**. Divergência encerra aquela tentativa com
`CANAL_DE_ORIGEM_DIVERGENTE`, registra falha e emite o evento de status existente. A linha
fica para inspeção; não é apagada, redirecionada nem reenviada automaticamente, nem mesmo
após voltar a configuração. Uma operação futura de recuperação exige conferência humana.

Sem migration, índice, consulta extra, nova variável, provider externo, polling ou mudança
de autorização. Aplica-se genericamente aos gateways, sem condicional por cliente.

**Limites deliberados:**

1. Payload legado sem os dois campos permanece legível para não interromper filas do canal
   atual. Não se infere sua origem. **É obrigatório zerar/conferir essas pendências antes do
   corte**; esta compatibilidade não é proteção automática de legado entre provedores.
2. O UUID da credencial não resolve sozinho o segredo histórico: os adaptadores usam
   configuração global. Rotação no mesmo provedor/identificador e atendimento antigo com
   credencial antiga ainda exigem resolução versionada/validação antes da ativação Z-API.
3. Agendamento ainda não materializado não tem snapshot de canal; não foi silenciosamente
   cancelado ou redirecionado. Definir destino por programação antes do corte.
4. URLs-base/segredos não são copiados ao payload. Esta defesa não detecta troca indevida de
   endpoint mantendo provedor/identificador; configuração operacional deve ser controlada.

## 4. Contrato e segurança

- G1 continua aberto: as páginas de introdução, Client-Token e configuração de callbacks
  verificadas não especificam autenticação de entrada no CRM. HTTPS e `instanceId` no JSON
  não provam autoria. Aguardar resposta oficial, conforme decisão anterior do responsável.
  Não implementar HMAC inventado, Client-Token de callback presumido ou segredo em URL próprio.
- O [blog oficial de segurança](https://z-api.io/blog/como-garantir-seguranca-e-estabilidade/)
  menciona assinaturas, mas não especifica algoritmo/header/configuração. Isso é uma pista
  para o suporte, não um verificador implementável. Não afirmar que a capacidade inexiste;
  ver [pendência oficial atualizada](./autenticacao-callback-pendente.md).
- Correção documental: `/message/reply-message` é página; operação documentada é
  `POST /instances/{instanceId}/token/{token}/send-text` com `messageId`.
- `Client-Token` é header de saída. Token da instância fica no path da API: futuro adapter
  deve suprimir URL/exception/body cru de logs, tracing, diagnóstico e métricas.
- Recebimento exige download assíncrono, limites de bytes/tempo, DNS e redirects validados,
  rede privada/loopback/metadata recusadas, MIME real e storage privado; nenhum token no CDN.
  Allowlist de CDN e contrato de autenticação ainda não foram comprovados.
- API Documentation Checker disponível foi usado para extrair operação explicitamente
  descrita. Retornou `success` com `endpoints: []`: **não avaliativo**, não valida o contrato.
  Leitura manual das fontes foi mantida; nenhuma chamada à API de negócio da Z-API ocorreu.

## 5. Testes e evidências

- Reprodução anterior à correção: `CanalWhatsAppIT$Envio`, 17 casos, com 1 falha e 2 erros
  esperados dos três testes novos: origem ausente e duas divergências enviadas indevidamente.
- Iteração seguinte: as divergências foram bloqueadas, mas uma consulta do próprio teste
  usava operador JSONB `?` como placeholder JDBC. Corrigida para `jsonb_exists`.
- Resultado final dirigido: `PublicadorDaOutboxOperacoesTest` **14/14** e
  `CanalWhatsAppIT` **50/50**, incluindo quatro casos adicionados nesta auditoria.
  `clean verify` completo e CI: resultados no fechamento da PR desta auditoria.
  Não interpretar resultados intermediários como aprovação.
- Sem mudança frontend; nenhuma captura UI demonstra Z-API funcional. CI frontend do último
  SHA deve ser conferido mesmo sem diff de interface.
- O CI desta auditoria encontrou vulnerabilidade preexistente em Zod 3
  ([GHSA-mrhx-c7xc-m3w5](https://github.com/advisories/GHSA-mrhx-c7xc-m3w5), correção ≥4.6.6).
  Migração major fora do escopo solicitada ao responsável; não suprimir `npm audit` nem
  declarar frontend aprovado enquanto o gate reprovar. Resultado do SHA final na PR.
- Main auditada: run [39884940281](https://github.com/synapse-auto/ERP-Matriz-Synapse/actions/runs/39884940281)
  concluída com Backend, Frontend, Stack e publicação GHCR backend/frontend aprovados.
  Inspeção local dos manifests `:61a7c6f` recebeu HTTP 403: acesso/pull e digest pelo operador
  **não verificados**. Essa main não contém adaptador Z-API nem a correção incremental daqui.

## 6. Próximos bloqueios e responsabilidades

| Bloqueio concreto | Próxima ação |
|---|---|
| Autenticação oficial de callback ausente (G1) | Responsável/fornecedor apresentar contrato sanitizado; implementar somente mecanismo comprovado |
| Adaptador/entrada/mídia/capacidades Z-API inexistentes | Continuar fases 2–4 após gates essenciais; HTTP simulado, storage e PostgreSQL reais |
| IDs globais e status antecipado (G3) | Estender correlação por canal/instância/namespace sem reescrever IDs históricos; testar colisão e eventos adiantados |
| Resultado ambíguo de POST (G4) | Integrar resultado tipado à persistência E209; não duplicar fila nem reenviar cegamente |
| Credenciais/agendamentos/legado | Resolver identidade histórica e destino futuro; executar inventário somente leitura do runbook |
| Workflows não auditados | Equipe de automação validar inventário e evidências do documento de passagem |
| Homologação real ausente | Operador obter autorização específica e executar roteiro controlado somente após código/CI prontos |

Não há variável nova a adicionar ao Dokploy nesta entrega. Ação imediata: **nenhuma**;
manter configuração atual da Femina e Z-API desativada. Configurações futuras ainda não
implementadas não são apresentadas como instruções válidas de produção.
