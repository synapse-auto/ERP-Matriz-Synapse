# Z-API — progresso e gates

Atualizado: 24/10/2026. Planejamento: [planejamento.md](./planejamento.md).
Estado operacional: **nenhum código de adaptador, desativado em todas as instâncias**.
Base da auditoria atual: origin/main 61a7c6f. Worktree isolado reutilizado; alterações
preexistentes do checkout principal preservadas. Branch `codex/z-api-auditoria-preparacao`.

| Fase | Estado | Entrega / pendência | PR/base |
|---|---|---|---|
| 1 | Incorporada em 13/10; G1 continua bloqueando passagem | Documentação/fixtures, não adaptador; revalidação de 24/10 corrigiu contrato de citação | [PR #301](https://github.com/synapse-auto/ERP-Matriz-Synapse/pull/301), codex/z-api-fase-1 / main |
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
  Decisão de 12/10: **aguardar contrato oficial antes de implementar entrada**, sem fallback
  próprio presumido. [Pendência ao fornecedor e resultado do Checker](./autenticacao-callback-pendente.md).
- **G2 identidade — decidido, não implementado:** phone pode ser LID sem conversão para telefone.
  Aprovado suporte inicial a telefones, com descarte/quarentena rastreável de LID-only e
  limitação explícita. Sem ampliação do modelo ou criação de telefone fictício.
- **G3 correlação:** esquema/portas atuais têm ID global; aprovar extensão isolada por canal/
  instância e aliases, preservando IDs brutos e contratos existentes, sem migrar histórico.
- **G4 envio ambíguo:** resultado tipado atual não possui ambiguidade. Aprovar extensão que
  suspende retry de timeout pós-POST sem afirmar falsa falha/entrega e sem alterar outros canais.
  A main auditada em 24/10 já possui persistência E209 para interrupção após despacho;
  reutilizá-la. Não confundir essa proteção com cobertura dos retornos timeout/5xx do adaptador.
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
Na revisão anterior, SHA 6760ede: runs PR 38208887630/push 38208884379 passaram (backend
clean verify Java 21, frontend 1187 testes, Stack). Essa evidência não substitui os checks
da revisão que registra estas decisões. Checker: duas extrações retornaram sucesso com
zero endpoints, inclusive para cURL explícito; resultado **não avaliativo**, não aprovação
da autenticação. Nada foi homologado na conta real.

## Operação e continuidade

Sem variáveis novas, migrations, VPS/Dokploy, deploy, conta real, registro de webhook ou n8n.
Não abrir PRs vazias para preencher cinco links. Ordem futura: F1 → F2 → F3 → F4 → F5,
com PRs encadeadas enquanto predecessoras estiverem abertas, sem merge automático.
Reler planejamento e progresso ao retomar; decisões ausentes não expiram por compactação.

## Auditoria incremental — 24/10/2026

Pedido atual autoriza corrigir lacunas independentes e preparar operação, não ativação. Somente
F1 foi encontrada na main; nenhuma entrega F2–F5 localizada. Como #301 está mergeada, esta
branch parte de main; não há dependência de PR aberta nem razão para repetir seu diff.

- [Matriz de prontidão e evidências](./auditoria-2026-10-24.md).
- [Documento para automação](./documento-para-automacao.md): caminhos CRM/diretos, reservas,
  IDs, origem, filas e confirmação necessária da equipe n8n. Workflow publicado não auditado.
- [Runbook de corte/rollback bloqueado](./runbook-troca-femina.md): dez passos, consultas somente
  leitura e pré-condições; não contém ativação fictícia nem autoriza executar em produção.
- Correção de código independente: novos payloads de outbox guardam provedor/identificador de
  origem; divergência bloqueia antes de composição/despacho/rede. Não migra payload legado,
  muda segredo, resolve credencial histórica nem redireciona programada futura silenciosamente.
- Testes dirigidos: `PublicadorDaOutboxOperacoesTest` **14/14**;
  `CanalWhatsAppIT` **50/50**, com PostgreSQL real, incluindo origem persistida, dois negativos
  de troca e compatibilidade legada. Reprodução RED anterior demonstrou três falhas do guard
  ausente; iteração intermediária corrigiu consulta JDBC do próprio teste.
- `clean verify` completo Java 21 e CI do SHA desta auditoria: resultados no fechamento da PR.
  Não marcar integração concluída com base apenas nos testes do guard.
- Fonte de citação corrigida: página `reply-message` documenta POST **`send-text`**.
  Checker repetiu `success/endpoints=[]`; não avaliativo. G1 não recebeu contrato oficial.
- Sem frontend alterado, novos endpoints, migrations, variáveis, produção ou chamadas reais.
  Skills equivalentes disponíveis foram utilizadas antes do código: arquitetura hexagonal,
  API design, segurança, Java, Spring Boot TDD e operações GitHub. Skills canônicas nomeadas
  no AGENTS não constam do catálogo desta sessão.

**Pronto no código: não. Homologado real: não. Pode trocar hoje: não nesta revisão.**
Próxima ação material: obter contrato oficial G1; concluir F2–F4/correlação/ambiguidade e validar
automação. Snapshot da outbox é proteção parcial útil, não substituto dessas fases.
