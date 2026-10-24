# Z-API — checklist para troca (bloqueada)

24/10/2026. **Não trocar provedor nem executar em produção.**

- [ ] **G1 / fornecedor:** “Como nosso receptor comprova a origem de ReceivedCallback,
  DeliveryCallback e MessageStatusCallback? Quais headers/assinaturas são enviados e como
  configurar? Se assinatura: algoritmo, bytes assinados, formato, chave/rotação, timestamp e
  replay, com exemplo sanitizado. Se não há autenticação nativa, confirmem expressamente e
  indiquem mecanismo oficialmente suportado e limitações. Client-Token de chamada à API não
  responde isso.” [Fontes/perguntas completas](./autenticacao-callback-pendente.md).
- [ ] **G3 / fornecedor + CRM:** namespace ids[] (messageId/zaapId/outro); correlação por
  canal/instância/namespace, status antecipado/duplicado/fora de ordem e eco sem nova mensagem
  ou IA. IDs já preservados na outbox não concluem G3 nem tornam índice global isolado.
- [ ] **F3 / CRM:** entrada fail-closed no webhook real, instância, persistência/dedup/status/
  WebSocket e LID-only rastreável, após G1. Sem telefone fictício ou remoção de @lid.
- [ ] **F4 recebimento:** origens de mídia comprovadas, downloader assíncrono SSRF/DNS/
  redirect-safe com teto/timeout/MIME real. Saída desta branch não implementa recebimento
  nem recupera JPG/PNG indisponível da Uzapi.
- [ ] **Frontend / dependência separada:** Zod 3.25.76 vulnerável a GHSA-mrhx-c7xc-m3w5 /
  CVE-2023-54404 (alto, exaustão de memória), faixa <4.6.6. Correção oferecida 4.6.7 é major.
  Decisão mantida: não migrar major, não remover audit, não declarar CI verde.
- [ ] **CI final:** conferir Backend/Frontend/Stack do último SHA. Run #40100071811 (#322,
  fd12a3f) terminou FAILURE: backend/stack SUCCESS, frontend FAILURE em “Auditar dependencias
  de producao”, GHCR SKIPPED. Não herdar resultado local/predecessora para esta PR.
- [ ] **Operação:** credenciais históricas, inventário/drenagem de legado e destino das
  programadas não materializadas, sem redirecionamento silencioso.
- [ ] **Automação:** inventário de chamadas diretas/reservas/registros/callbacks/filas no
  [documento para automação](./documento-para-automacao.md). Não duplicar provedores nem
  reenviar aceites PENDENTE. Workflow publicado não auditado.
- [ ] **F5 futura:** aplicação/capacidades, codecs/reprodução, autorização/participação,
  reconexão, carga/memória/disco e logs/APM. Simulador não prova conta/workflow real.

## Zod também afeta a base

origin/main `61a7c6f93c0b6840caa50f60f27e1ab7c437b0d0` e pilha Z-API possuem blobs idênticos:

| Arquivo | Blob Git |
|---|---|
| frontend/package.json | 0f441b15f8af88fedf218e55803283bc6eed6a2e |
| frontend/package-lock.json | 3fbd28c0f16b0b3fad4e573a3243e5f079d05c6f |
| .github/workflows/ci.yml | 6514ed205b9e4a4eeb801245b4fe51c6713f8e35 |

Repetido npm audit --omit=dev --audit-level=high na worktree e em temporário isolado com
cópias desses mesmos manifests: ambos exit 1, um HIGH em Zod. Sem install/update/mudança no
checkout principal. Falha antes de lint/test/typecheck/build, não é compilação do adapter.
A run histórica #39884940281 da main foi SUCCESS; não substitui auditoria atual.
Fonte de faixa/correção: [GitHub Advisory](https://github.com/advisories/GHSA-mrhx-c7xc-m3w5).

**Dokploy: nenhuma ação; Z_API_HABILITADO=false.** Sem número, webhook, deploy, alterações
de credenciais/n8n, mensagens reais ou reprocessamento.
