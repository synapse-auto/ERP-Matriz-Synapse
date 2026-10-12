# Z-API — progresso e gates

Atualizado: 12/10/2026. Planejamento: [planejamento.md](./planejamento.md).
Estado operacional: **nenhum código de adaptador, desativado em todas as instâncias**.
Base: origin/main e6bc480. Worktree isolado; alterações preexistentes preservadas.

| Fase | Estado | Entrega / pendência | PR/base |
|---|---|---|---|
| 1 | Entrega documental pronta; gates bloqueiam passagem | contrato, desenho, fixtures e gates G1–G4; CI a conferir no último SHA | [PR #301](https://github.com/synapse-auto/ERP-Matriz-Synapse/pull/301), codex/z-api-fase-1 / main |
| 2 | Não iniciada | Gate essencial de autenticação + decisões arquiteturais da Fase 1 | futuramente base fase-1 se PR aberta |
| 3 | Não iniciada | Autenticação/correlação/eco/status antecipado e identidade LID | futuramente base fase-2 |
| 4 | Não iniciada | Mídia/capacidades/SSRF/limites e fontes completas | futuramente base fase-3 |
| 5 | Não iniciada | Regressão/runbook, nenhuma homologação real nesta task | futuramente base fase-4 |

## Fase 1: entregas e fontes

- [Contrato verificado](./contrato-verificado.md): matriz com fontes oficiais por capacidade;
  rotas/métodos conferidos também no HTML, pois Markdown omite componentes de referência.
- [Fixtures e casos negativos](./fixtures/README.md): exemplos fictícios, não dados reais.
- Auditados gateway/tradutor/seletor/controller, V16/V46, repositório de IDs, aplicação de status,
  resultado de envio/publicador da outbox, docs/38 e acesso à automação docs/16.
- Fontes descobertas por llms.txt → índice API, sem contrato copiado da Meta/Uzapi.
- Não há skill de clean-code/architecture-patterns/api-design-principles disponível no catálogo
  desta sessão. Nenhuma skill disponível se aplica à pesquisa documental/fixtures desta fase.

## Gates — parar antes de escrever o adaptador

- **G1 essencial:** autenticação/origem do callback não documentada nas páginas verificadas.
  Aguardar contrato oficial ou decisão explícita sobre proteção própria e suas limitações.
- **G2 identidade:** phone pode ser LID sem conversão para telefone; decidir suporte parcial
  com descarte/quarentena rastreável ou uma ampliação separada do modelo de contato.
- **G3 correlação:** esquema/portas atuais têm ID global; aprovar extensão isolada por canal/
  instância e aliases, preservando IDs brutos e contratos existentes, sem migrar histórico.
- **G4 envio ambíguo:** resultado tipado atual não possui ambiguidade. Aprovar extensão que
  suspende retry de timeout pós-POST sem afirmar falsa falha/entrega e sem alterar outros canais.
- Confirmar namespace de ids[] do status, garantias de evento/reentrega e domínios/segurança
  de URL de mídia antes de depender dessas propriedades. Sem confirmação, registrar lacuna.

## Verificações

Validação local Node: 10/10 arquivos JSON parseados, 15/15 verificações de consistência
passaram (IDs distintos/alias, callback correlacionável, ordem invertida/repetição, eco,
grupo, outra instância, LID, áudio sem caption, 2xx sem ID/contraditório, newline).
`git diff --check` limpo. Isso não prova autenticação/deduplicação de um adaptador inexistente.
Fase exclusivamente documental: backend/frontend não alterados. Clean verify local não se
aplica à entrega sem código segundo o planejamento; Docker local está indisponível. A CI
existente roda a suíte completa independentemente de paths; resultado do último SHA será
conferido e registrado no relatório/PR antes de qualquer passagem.
Os checks da [PR #301](https://github.com/synapse-auto/ERP-Matriz-Synapse/pull/301/checks)
são a fonte do resultado do último SHA; não usar uma run anterior como prova do commit atual.

## Operação e continuidade

Sem variáveis novas, migrations, VPS/Dokploy, deploy, conta real, registro de webhook ou n8n.
Não abrir PRs vazias para preencher cinco links. Ordem futura: F1 → F2 → F3 → F4 → F5,
com PRs encadeadas enquanto predecessoras estiverem abertas, sem merge automático.
Reler planejamento e progresso ao retomar; decisões ausentes não expiram por compactação.
