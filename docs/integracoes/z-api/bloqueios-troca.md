# Z-API — checklist para troca (bloqueada)

## Checklist vigente — complemento de homologacao protegida (25/10/2026)

- Codigo: entrada protegida opt-in, tradutor registrado, midia por URL com SSRF/DNS/limites,
  storage privado, correlacao duravel e repasse autenticado implementados; resultados finais
  de testes/CI constam na PR do complemento. Nao confundir com os checklists historicos abaixo.
- [ ] Operador: confirmar preservacao da query no cadastro/entrega OU header nativo com nome e
  configuracao fornecidos pelo suporte. Client-Token nao foi presumido.
- [ ] Operador: proxy/APM sem query/header sensivel, HTTPS/forwarded confiavel/backend privado.
- [ ] Homologacao: CDN exata, codecs/bytes/download e namespace real de ids[] comprovados.
- [ ] Automacao: parser/token CRM→n8n/dedup e nos diretos/filas/programadas migrados explicitamente.
- [ ] Corte: inventario/pausa comprovada/drenagem de legado/eventos em voo/rollback por provedor,
  sem replay ou redisparo de aceites PENDENTE/ambiguos.
- [ ] CI do SHA final do complemento e release/GHCR aprovados; nao usar CI antigo para esse fim.

O suporte **nao e o unico caminho**: segredo URL proprio foi aprovado. Os itens acima
sao verificacoes concretas da conta/infra/workflows, nao motivos para interromper codigo
independente. [Roteiro e configuracao A–D](./homologacao-protegida.md).

## Historico anterior ao complemento

25/10/2026. **Não trocar provedor nem executar em produção.**

- [ ] **G1 / fornecedor:** “Como nosso receptor comprova a origem de ReceivedCallback,
  DeliveryCallback e MessageStatusCallback? Quais headers/assinaturas são enviados e como
  configurar? Se assinatura: algoritmo, bytes assinados, formato, chave/rotação, timestamp e
  replay, com exemplo sanitizado. Se não há autenticação nativa, confirmem expressamente e
  indiquem mecanismo oficialmente suportado e limitações. Client-Token de chamada à API não
  responde isso.” [Fontes/perguntas completas](./autenticacao-callback-pendente.md).
- [ ] **G3 / fornecedor + CRM:** namespace ids[] (messageId/zaapId/outro); correlação por
  canal/instância/namespace, status antecipado/duplicado/fora de ordem e eco sem nova mensagem
  ou IA. IDs já preservados na outbox não concluem G3 nem tornam índice global isolado.
  A extensão isolada/antecipação/dedup já está implementada em teste local na branch
  `codex/z-api-entrada-local`; namespace real e garantias do fornecedor continuam pendentes.
- [ ] **F3 / CRM:** entrada fail-closed no webhook real, instância, persistência/dedup/status/
  WebSocket e LID-only rastreável. ACL/fila local/eventos implementados com entrada HTTP
  recusada; conexão autenticada de produção após G1. Sem telefone fictício ou remoção de @lid.
- [ ] **F4 recebimento:** origens de mídia comprovadas, downloader assíncrono SSRF/DNS/
  redirect-safe com teto/timeout/MIME real. Saída desta branch não implementa recebimento
  nem recupera JPG/PNG indisponível da Uzapi.
- [x] **Frontend / Zod:** nova autorização de 25/10 permitiu migrar para 4.6.7 na #320,
  preservando defaults dos catálogos com prefault. Auditoria de produção mantida/aprovada;
  testes/typecheck/lint/build passaram. Não equivale a eliminar alertas da árvore dev.
- [ ] **CI final de integração:** conferir Backend/Frontend/Stack do último SHA da #324
  e publicação GHCR da main. #320–#323 já passaram antes dos respectivos merges; resultados
  históricos vermelhos abaixo não aprovam nem reprovam por si sós o SHA final.
- [ ] **Operação:** credenciais históricas, inventário/drenagem de legado e destino das
  programadas não materializadas, sem redirecionamento silencioso.
- [ ] **Automação:** inventário de chamadas diretas/reservas/registros/callbacks/filas no
  [documento para automação](./documento-para-automacao.md). Não duplicar provedores nem
  reenviar aceites PENDENTE. Workflow publicado não auditado.
- [ ] **F5 futura:** aplicação/capacidades, codecs/reprodução, autorização/participação,
  reconexão, carga/memória/disco e logs/APM. Simulador não prova conta/workflow real.
- [ ] **Operação da correlação:** retenção/reconciliação de identidades órfãs e colisões;
  status não confirmado não pode ser apagado com prazo arbitrário nem autorizar redisparo.

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
