# Z-API — progresso e gates

Atualizado: 24/10/2026. Planejamento: [planejamento.md](./planejamento.md).
Estado operacional: **saída experimental desativada; entrada/correlação implementadas para
teste local, sem bean/verificador de produção e sem downloader externo de mídia**.
Base da auditoria atual: origin/main 61a7c6f. Worktree isolado reutilizado; alterações
preexistentes do checkout principal preservadas. Branch `codex/z-api-auditoria-preparacao`.

| Fase | Estado | Entrega / pendência | PR/base |
|---|---|---|---|
| 1 | Incorporada em 13/10; G1 continua bloqueando passagem | Documentação/fixtures, não adaptador; revalidação de 24/10 corrigiu contrato de citação | [PR #301](https://github.com/synapse-auto/ERP-Matriz-Synapse/pull/301), codex/z-api-fase-1 / main |
| 2 | Saída/texto implementados nesta revisão, ainda sem liberação operacional | Testes HTTP e outbox; entrada bloqueada por G1, CI frontend bloqueado por Zod | [PR #322](https://github.com/synapse-auto/ERP-Matriz-Synapse/pull/322), base #321 |
| 3 | Parcial local autorizada; produção fechada | Tradução/correlação/status antecipado/eco/LID com fixtures; G1/namespace/repasse pendentes | codex/z-api-entrada-local, base #323 |
| 4 | Parcial: saída em avanço independente autorizado | Imagem/áudio/vídeo/documento; recebimento/SSRF bloqueados G1 | codex/z-api-midia-saida, base #322 |
| 5 | Preparação documental/regressão parcial; não concluída | Runbook/passagem; nenhuma homologação/corte | depende F3/F4 e CI |

## Fase 1: entregas e fontes

### Integração sequencial autorizada em 25/10/2026

A missão mais recente autoriza corrigir e integrar #320 → #321 → #322 → #323 → #324,
um merge por vez após CI do último SHA. #320 foi integrada em `ba381ab`, com runs
PR **40169686971** e push **40169684615** SUCCESS no SHA `b68e73c`.
Zod 4.6.7 corrigido foi incorporado da main nesta #321; auditoria não foi removida.
Frontend local da correção: 168 arquivos / 1.400 testes, typecheck/lint/build/audit
aprovados (quatro avisos preexistentes de lint). O CI da revisão #321 será conferido
separadamente e registrado na PR. Isso não conclui homologação ou integração Z-API.
Sem deploy; provedor continua OFF e segurança de callbacks permanece bloqueadora
da entrada de produção. Nenhuma autenticação permissiva será registrada.

#321 integrada em `5ceccf6`, após PR **40170618025** e push **40170615484** SUCCESS
no SHA `ad45d65`. #322 incorpora essa main por merge, sem rebase destrutivo;
sua base passa a main após a integração da predecessora. A validação do novo SHA
da #322 é independente e será registrada na PR antes do merge. O código de saída
continua experimental/OFF; HTTP 200 não confirma entrega e não reabre a outbox.

#322 integrada em `ecec625`, após PR **40171435859** e push **40171431231** SUCCESS
no SHA `f6c8e74`. Repetição local dirigida: 54/54 testes Java 21, sem ignorados.
#323 incorpora a main e passa a tê-la como base, mantendo o diff apenas de mídia.
O CI próprio do novo SHA será conferido antes do merge. Não há ativação,
homologação do fornecedor ou liberação do recebimento de mídia em produção.

- [Contrato verificado](./contrato-verificado.md): matriz com fontes oficiais por capacidade;
  rotas/métodos conferidos também no HTML, pois Markdown omite componentes de referência.
- [Fixtures e casos negativos](./fixtures/README.md): exemplos fictícios, não dados reais.
- Auditados gateway/tradutor/seletor/controller, V16/V46, repositório de IDs, aplicação de status,
  resultado de envio/publicador da outbox, docs/38 e acesso à automação docs/16.
- Fontes descobertas por llms.txt → índice API, sem contrato copiado da Meta/Uzapi.
- Não há skill de clean-code/architecture-patterns/api-design-principles disponível no catálogo
  desta sessão. Nenhuma skill disponível se aplica à pesquisa documental/fixtures desta fase.

## Gates — não ativar; histórico e complemento de autorização

- **G1 essencial:** autenticação/origem do callback não documentada nas páginas verificadas.
  Decisão de 12/10 aguardava contrato antes de implementar. Complemento mais recente permite
  ACL/testes locais, **não** conectar entrada de produção nem presumir autenticação.
  [Pendência ao fornecedor e resultado do Checker](./autenticacao-callback-pendente.md).
- **G2 identidade — decidido, não implementado:** phone pode ser LID sem conversão para telefone.
  Aprovado suporte inicial a telefones, com descarte/quarentena rastreável de LID-only e
  limitação explícita. Sem ampliação do modelo ou criação de telefone fictício.
- **G3 correlação:** esquema/portas atuais têm ID global; aprovar extensão isolada por canal/
  instância e aliases, preservando IDs brutos e contratos existentes, sem migrar histórico.
- **G4 envio ambíguo:** resultado tipado atual não possui ambiguidade. Aprovar extensão que
  suspende retry de timeout pós-POST sem afirmar falsa falha/entrega e sem alterar outros canais.
  A main auditada em 24/10 já possui persistência E209 para interrupção após despacho;
  reutilizá-la. Não confundir essa proteção com cobertura dos retornos timeout/5xx do adaptador.
  Nesta revisão `ResultadoDeEnvio.Ambiguo` suspende retry, usando E209 e diagnóstico explícito
  de envio não confirmado; Meta/Uzapi não passaram a produzir essa variante.
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

## Operação e continuidade (registro inicial; estado atual ao fim)

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
  Blog oficial menciona assinaturas sem especificação; pendência ao fornecedor foi atualizada.
- CI frontend encontrou bloqueio preexistente Zod 3/GHSA-mrhx-c7xc-m3w5; atualização major
  não foi presumida nem auditoria ignorada. Resultado do SHA final no fechamento da PR.
- Sem frontend alterado, novos endpoints, migrations, variáveis, produção ou chamadas reais.
  Skills equivalentes disponíveis foram utilizadas antes do código: arquitetura hexagonal,
  API design, segurança, Java, Spring Boot TDD e operações GitHub. Skills canônicas nomeadas
  no AGENTS não constam do catálogo desta sessão.

**Pronto no código: não. Homologado real: não. Pode trocar hoje: não nesta revisão.**
Próxima ação material: obter contrato oficial G1; concluir F2–F4/correlação/ambiguidade e validar
automação. Snapshot da outbox é proteção parcial útil, não substituto dessas fases.

## Continuação autorizada — saída experimental (Fase 2)

Pedido “pode seguir para concluir a integração” permite avançar na saída independente, não
revoga a decisão de obter autenticação oficial antes da entrada. [Entrega detalhada](./fase-2-saida.md).
O título antigo de gates dava a entender que toda saída dependeria de G1: agora o bloqueio
está explicitamente na entrada/ativação. F3–F5 não avançam com contrato essencial ausente
nem com CI vermelho ignorado.

- `ZApiAdapter` implementa texto sem citação e saúde; bean ausente por padrão.
- Seleção real continua falhando fechada: não há tradutor Z-API fictício em produção.
- `Enfileirado` preserva instância/zaapId/messageId na mesma outbox e mantém PENDENTE;
  não grava aliases no índice global nem confirma envio/entrega pelo HTTP 200.
- `Ambiguo`/E209 suspende retry de POST incerto; somente breaker/bulkhead antes da rede
  devolvem recusa temporária. Resultados tardios não sobrescrevem uma linha encerrada.
- Timeout/concurrency isolados, resposta limitada, redirects proibidos, observação HTTP
  desabilitada e erro sanitizado **antes** dos listeners do circuit breaker.
- Configuração opcional em .env.example/README/stack; nenhum valor novo obrigatório,
  migration, frontend, callback real ou mudança em n8n/produção.
- Dirigidos iniciais: adaptadores 161/161 (Z-API 34, configuração/seleção 3, Meta 59,
  Uzapi 65); fluxo genérico PostgreSQL 52/52. Teste específico de saída, clean verify
  completo e CI final são registrados no fechamento desta revisão.
- Revisão final: 164/164 nos adaptadores (Z-API 37, seleção 3, Meta 59, Uzapi 65) e
  55/55 integrações PostgreSQL (`CanalWhatsAppIT` 52, `ZApiSaidaIT` 3). Novos negativos
  provaram o drain no close antes da correção e bloqueio de instância HTTP divergente.
  `clean verify` completo foi reiniciado depois desses ajustes, sem contar a execução
  interrompida como sucesso. Resultado final e CI do último SHA no fechamento da PR.
- PR #321 continua draft/aberta. Ordem de merge: #321 → PR de `codex/z-api-fase-2-saida`.
  PR de saída: [#322](https://github.com/synapse-auto/ERP-Matriz-Synapse/pull/322), também draft.
  O diff da segunda PR usa a branch #321 como base, não main.
- CI da predecessora (SHA 1990dbe): PR 40071927205 e push 40071925667 com backend e
  Stack aprovados, frontend bloqueado em auditoria Zod, GHCR skipped. Decisão explícita:
  registrar pendência, **não** migrar Zod nem ignorar auditoria. Não chamar esse CI de verde.

**Integração completa: não. Troca da Femina: não liberada. Homologação real: não executada.**

## Continuação após pacote Markdown — F4 independente

Planejamento/progresso relidos em 24/10; worktree isolada preservada. Autorização mais recente
permite mídia/testes/capacidades/passagem independentes de G1. Não cria F3 fictícia nem trata
CI vermelho como sucesso. Ordem: #321 → #322 → PR de `codex/z-api-midia-saida`, sem merge.

- [Implementação, fontes e testes](./fase-4-midia-saida.md).
- [Bloqueios da troca](./bloqueios-troca.md): causa/base Zod, G1/G3, entrada/mídia recebida,
  capacidades, credenciais/agendas, automação e homologação.
- ZIP com 23 Markdown lido sem execução; hash registrado. Sugestões de segredo URL/stickers
  não substituem decisão/contrato. Checker extraiu um endpoint de documento, sem campos;
  resultado parcial, não certificação de callbacks.
- Saída usa storage privado e limites existentes no worker, spool limitado/Base64 incremental,
  sem URL pública ou cópia Base64 integral. Sem migration/endpoint/variável nova.
- HTTP 200 mantém PENDENTE mas fecha outbox. IT força lease vencido, repete API após aceite
  e job, exigindo apenas um POST. Não há polling/retry adicional.
- Dirigidos: adaptadores 194/194 (Z-API 37, mídia 30, seleção 3, Meta 59, Uzapi 65),
  `ZApiSaidaIT` 9/9 com PostgreSQL/Redis/MinIO reais e transporte HTTP simulado.
  Mídia ampliada depois para 32/32, incluindo leitura parcial com falha e DEBUG sem
  Base64/nome/legenda/telefone/segredos; suíte completa deve exercitar novamente todos.
  Clean verify completo Java 21 e CI do último SHA no relatório/PR de fechamento.
- F3 bloqueada antes de implementação; F4 parcial e F5 não concluída. Não há cinco PRs
  concluídas nem liberação operacional. Dokploy: nenhuma ação; Z-API permanece OFF.

## Complemento autorizado — F3 local, produção fechada

Pedido mais recente autoriza tradução, recebimento, status e correlação com fixtures/testes
locais antes da definição de autenticação. Planejamento e progresso relidos; nenhuma decisão
permite registrar verificador permissivo ou abrir entrada de produção. [Entrega e fontes](./fase-3-entrada-local.md).

- Branch `codex/z-api-entrada-local`, base #323 (07d7db4), mantendo a cadeia #321 → #322 →
  #323 → PR desta branch. Todas draft; não fazer merge automaticamente.
- ACL sem bean de produção, verificadores false; HTTP real recusa 403 sem gravar fila.
  Testes injetam somente fixtures sintéticas na fila local e exercitam o job real.
- Recebimento de texto/tipos de mídia, preservação DOCUMENTO, eco/LID/tipos não suportados;
  mídia usa porta simulada com MinIO/download CRM reais, não downloader externo homologado.
- Correlação durável por provedor/instância/namespace/ID, status antecipado e monotonia;
  V111 nova, sem editar migrations/histórico. RLS exclusiva SERVICO e negativos.
- Colisão não rouba alias, não desfaz aceite nem reabre outbox; diagnóstico técnico sanitizado.
  Worker Z-API não drena backlog de outro provedor. Caminhos Meta/Uzapi mantidos.
- Dirigidos 94/94; integrações 48/48 (entrada local 39, saída 9), inclusive 25 pares de
  estado, duplicação, status antes do aceite, RLS, autorização e colisão sem redisparo.
  Suíte completa Java 21 e CI do último SHA serão registrados no fechamento da PR.
  Identidade pura 7/7 e caso de uso com portas mockadas 3/3 também aprovados.
- G1 e namespace real de ids[] continuam pendentes. F4 downloader/SSRF, repasse n8n,
  retenção/reconciliação, capacidades/carga/reconexão e homologação real não concluídos.
- CI da predecessora #323: runs PR 40108269410 / push 40108250305 FAILURE; Backend/Stack
  SUCCESS, Frontend FAILURE em audit Zod, GHCR SKIPPED. Não transferir esse resultado
  para o SHA desta PR. Zod major/auditoria preservados conforme decisão explícita.
- Sem frontend alterado, variável nova, proxy/VPS/Dokploy, webhook real, número conectado,
  n8n, mensagens reais, merge ou deploy. **Dokploy: nenhuma ação; Z-API OFF.**

**F3 parcial local: sim. Integração completa: não. Troca/homologação real: não liberadas.**

### Ajuste após primeira CI desta entrega

SHA cf4811e: PR run 40140501165 reprovou backend em
`SchemaMigracoesIT.Estrutura.tabelas_bancoMigrado_existemTodas` (lista esperada sem V111),
além do bloqueio independente Zod; Stack passou, GHCR skipped. Acrescentada a tabela à lista,
mantida comparação exata. Reforçados negativos de escrita RLS (42501) e asserts de mapeamento.
Não é flake nem foi ignorado/reexecutado sem correção. Clean verify local interrompido após
diagnóstico, com nova execução completa após o ajuste; resultado final na PR/relatório.

Revisão adicional antes da entrega: dois negativos provaram que apenas conservar o alias
antigo ainda deixava um callback futuro confirmar a mensagem errada. V111 não mergeada
ganhou marcador de conflito; aplicação de status fica bloqueada nesse alias, conservando
vínculo/aceite para auditoria e sem redisparo. Sem desfazer estados anteriormente comprovados
nem resolver conflito automaticamente. Novo clean verify completo após essa correção.

Rodada final dirigida: 104 unidades/regressões e 71 integrações aprovadas (schema 21,
entrada local 41, saída 9). Negativos de colisão reprovavam antes; agora exigem bloqueio
durável, nenhum status falso e nenhum novo evento. LID-only persistido como descarte e eco
sem mensagem/despacho também exercitam a fila/job reais. Suíte completa/CI do SHA final
no fechamento da PR; não usar CI de predecessor para aprovar essa revisão.
