# Z-API — progresso e gates

## Respostas interativas e referências recebidas — 26/10/2026

- Worktree isolada, branch `codex/z-api-botoes-citacoes`, baseada em `origin/main`
  `c91efc3b31a6e899da3cf80cad25df5c35ac11b3` (merge da #330). Não duplica suas proteções
  de citação de saída, aliases/RLS ou idempotência.
- Lacunas comprovadas: tradutor ignorava botão/lista sem `text.message`, impedindo também
  o repasse; `referenceMessageId` não era traduzido e a resolução recebida consultava
  somente o índice legado em vez da identidade exata Z-API/instância/WHATSAPP/lead.
- [Contrato, metadados, instruções para automação e diagnóstico por etapa](./respostas-interativas.md).
  Sem migration, nova variável, alteração de workflow, deploy, replay ou envio real.
- Rodada anterior à sincronização: tradutor 42/42; ZApiInteracoesIT 36/36 e regressão ZApiCitacaoIT 28/28,
  com HTTP/PostgreSQL/MinIO reais e somente transporte externo simulado. A primeira rodada
  revelou um estado inválido na fixture negativa; corrigida e repetida integralmente.
- Frontend 1.417/1.417, typecheck aprovado, lint sem erros (quatro avisos preexistentes).
  Primeiro executor de testes não inicializou; a suíte completa passou com threads limitadas.
- Build frontend aprovado. A primeira execução de clean verify foi interrompida junto com
  o ambiente local, sem resultado final. A execução integral posterior e o CI do último SHA
  são registrados no fechamento da PR/relatório, separadamente dos testes dirigidos.
- [Evidência visual e limites](../../../frontend/output/playwright/z-api-interacoes/README.md):
  aplicação real com Inter carregada (1920×1080), escolhas/citações visíveis e origem fora
  da página carregada por ação autorizada. Nova sessão de captura após o build recusada
  pelo ambiente, sem contorno. Tela menor e reconexão/cancelamento com anexos não têm
  comprovação visual completa. Aviso de chave repetida na inbox dev registrado sem causa
  estabelecida e sem alegação de resolução; inbox não alterada.
- Homologação da conta/Configurações Beta e execução real do workflow são separadas.
- O CI inicial 40440803480 falhou em `DashboardVisaoGeralIT`: o oráculo usava
  `date_trunc('day', now())` da sessão PostgreSQL UTC, mas a API usa o fuso configurado
  da instância e intervalo com fim exclusivo. A virada de dia expôs a divergência.
  Correção restrita ao teste: recebe `synapse.tenant.timezone`, usa limites explícitos e
  inclui fixtures imediatamente antes do início e no fim do dia para provar exclusão.
  Nenhum código/consulta de dashboard em produção foi alterado; não é mera repetição do CI.
- A suíte completa local também identificou colisão do telefone fixo da fixture de outro
  lead com dados de outra suíte. Somente essa fixture passou a gerar telefone sintético
  derivado de UUID e conferir ausência antes do INSERT, seguindo o padrão dos ITs.
  Não remove dados de outras suítes nem afrouxa unicidade/asserções de isolamento.
- Sincronizada em 27/10/2026 com `origin/main` 4d2d600, preservando a #331 e seus testes
  de proxy/CORS herdados. O único conflito era este registro de progresso; as duas seções
  foram mantidas. Não houve alteração de migration, force push ou merge da PR para main.
  Resultados do novo SHA integrado são registrados no fechamento da PR.

## Proxy produtivo persistente — 26/10/2026, atualização 27/10

- Causa informada pelo operador: `403 Invalid CORS request` nos callbacks com Origin externo.
- Router HTTPS dedicado cobre os dois webhooks, remove Origin somente nesses paths e desativa
  access logs/tracing; CORS global da API permanece.
- As labels agora estao em `docker/dokploy-stack.yml`, Compose Path unico. Os overrides produtivo
  e de homologacao estao vazios por compatibilidade; nenhum Command customizado e necessario
  para reaplicar as labels em novos deploys.
- [Acao unica no Dokploy](./webhooks-no-dokploy.md): um deploy normal da branch de release apos
  o merge. A CI valida o base isolado, stack config isolado, compatibilidade do override e
  executa o router derivado do Compose Path base num Traefik de teste.
- Prova com composicao real, Traefik 3.6.7/TLS e Spring/PostgreSQL; resultados do clean verify
  e CI no fechamento da PR. Nenhuma env, migration, deploy ou operacao na VPS do agente.
- A configuracao atualmente ativa no Dokploy ainda exige conferencia operacional apos deploy;
  testes locais nao sao homologacao da conta real.

## Resposta citada — 26/10/2026

- Branch `codex/z-api-citacao`, baseada na main653a66b apos merge da #329; worktree isolada.
- [Causa, contrato e comportamento](./citacao-de-mensagens.md): recusa incondicional no adapter,
  hash interno versus messageId bruto e leitura da correlacao no ciclo migrado.
- Resolucao autorizada, revalidacao antes do HTTP e V114 somente de leitura RLS; escrita de
  aliases continua exclusiva de servico. Sem aliases de legado, reprocessamento ou alteracao
  de historico/participantes/responsaveis. Diagnostico produtivo exige IDs/SQL do operador.
- Testes focados e clean verify/CI do ultimo SHA serao registrados no fechamento da PR.
  Nenhum deploy, mensagem real ou env nova; homologacao com conta real separada.

## Auditoria de mensagens e midias — 26/10/2026

- Base `origin/main` 810647e, branch `codex/z-api-message-media-audit`, worktree isolada.
  A task anterior foi cancelada; seu trabalho local foi preservado em stash antes desta
  auditoria. Somente alteracoes pertinentes foram reaproveitadas e verificadas.
- [Contrato, matriz, diagnostico e roteiro curto](./auditoria-fluxo-mensagens-midias-2026-10-26.md).
  Fontes oficiais conferidas no commit 0b8d420f1f592a7b5e55d28f3e9412ed785880d6 e site atual.
- Corrigidos: recusa explicita de Client-Token versus envio ambiguo; diagnostico seguro por
  etapa/HTTP/codigo; repasse indevido durante somente-coleta; Ogg/Opus valido recusado pelo
  upload; validacao do container gravado antes do POST; delivery sem timestamp documentado.
- Nenhuma migration, identidade, env nova, ativacao, envio real ou operacao produtiva.
  A conta ainda recusa /status e /send-text sem Client-Token (evidencia do operador).
  O ponto exato do 403 produtivo precisa do novo diagnostico; nao declarado corrigido.
- Resultados completos e CI do ultimo SHA registrados na PR e no documento de auditoria.
  Homologacao de codec/dispositivo, CDN, proxy e namespace na conta real permanece separada.

## Complemento em validacao — transicao no banco existente, 26/10/2026

- Base origin/main 7ceee5e (#326), worktree isolada reutilizada; branch
  codex/z-api-transicao-produtiva. Novo cenario autorizado: numero produtivo conectado pelo
  operador; nao exigir banco vazio/fechar atendimentos. Nenhuma operacao produtiva do agente.
- V113 aditiva (numeração novamente conferida em origin/main): controle de transicao,
  rota auditada por ciclo aberto, retencao individual de agenda. FKs historicas preservadas,
  incluindo ciclos sem canal/credencial; somente novas outboxes recebem destino novo.
- Pausa opt-in de saida/publisher/programadas, try-lock local nao bloqueante, nenhum lock
  durante HTTP. Fila velha nao e reescrita/reservada pelo provedor novo. AceitePENDENTE nao
  autoriza novo despacho. Defaults false nas demais instancias.
- Coleta inicial Z-API autenticada/duravel sem reservar eventos/baixar bytes; permite confirmar
  CDN real antes de consumir o trafego produtivo, sem descartar midias com allowlist vazia.
- URL/segredo Uzapi preservados exclusivamente para status antigos da identidade original;
  HTTPS/limite/tempo constante/JSON estrito/redacao, sem mensagem/repasse/IA nessa entrada.
- [Roteiro produtivo incremental](./transicao-produtiva.md), cadastro inativo existente,
  router persistente no Compose Path base para ambas as URLs, envs/ativacao/agenda/recuperacao
  e [passagem para automacao](./documento-para-automacao.md). Nenhuma identidade/token real
  versionado. Cadastros/ativacao HML anteriores mantem suas protecoes.
- Primeira rodada apontou composicao SQL sem separador no novo guard de despacho: corrigida.
  Fixtures ajustadas para precisao de timestamp PostgreSQL, FK composta e limpeza de auditoria.
  Rodada seguinte nao executou integracoes porque Docker local estava fechado apos interrupcao;
  Docker iniciado sem reiniciar processo alheio, nova rodada com Postgres/MinIO em andamento.
  Nao tratado como flake nem CI verde. Resultados finais serao registrados na PR/relatorio.
- Teste negativo SQL real identificou heranca de escrita por DEFAULT PRIVILEGES: GRANT SELECT
  sozinho nao bastava. V113 revoga ALL antes de conceder leitura, e a suite viola a protecao
  de proposito com papel synapse_app (nao superusuario). Banco da transicao isolado dos
  ciclos deixados por outras suites, sem alterar a validacao do script de producao.
- Na primeira revisao, frontend nao alterado. Node Windows24.12.0 encerrou subprocessos com codigo3221226505;
  suite afetada aplicar-marca18/18 no runtime24.22.1. Execucao completa local repetida nesse
  runtime, com pool threads para contornar travamento dos forks Windows; nenhuma auditoria
  ou teste removido. Resultado final/CI ainda pendentes desta anotacao.
- Dirigidos finais: TransicaoProdutivaZApiIT **22/22**, incluindo os dois tipos de ciclo,
  participacao consentida/negativo, aceitePENDENTE sem novo despacho, fila antiga intacta,
  agenda individual, status legado HTTP real, RLS real/negacao de escrita, coleta sem efeitos,
  seis tipos de midia em storage privado e download autorizado. BUILD SUCCESS, 5m05s.
  SchemaMigracoesIT21/21, ZApiSaidaIT9/9 e StatusDeEntregaUzapiWebhookIT8/8 tambem passaram
  na rodada anterior; o clean verify integral recompila/exercita o conjunto final.
- Stack+override produtivo validados localmente por docker compose config --quiet com envs
  sinteticas; isso nao prova o router instalado nem a redacao na cadeia real. git diff --check
  aprovado. clean verify integral Java21 iniciado; resultado/CI do SHA final na PR/relatorio.
  Nenhuma ativacao, envio, workflow publicado, merge/deploy ou operacao VPS executados.
- Revisao do composer encontrou limpeza imediata do texto antes da recusaHTTP. Complemento
  restrito aos dois codigos409 da transicao recupera texto na conversa/sessao atual, preserva
  o digitado durante a espera e retira a bolha otimista sem mensagem real; nao reenvia nem
  muda tratamento de outros4xx/timeouts. Sem texto/cor/fonte nova. Skills frontend-patterns
  e de seguranca aplicadas; testes especificos e CI do SHA posterior a da28ce5 conferidos
  separadamente. Runs40267013011 e40266962551 aprovaram o primeiro SHA, nao o complemento.
- Complemento do rascunho: testes focados63/63 (composer e hook), incluindo nao misturar
  conversas/sessoes e nao duplicar envio. Typecheck/lint aprovados, build e conjunto completo
  repetidos no fonte final. A rodada anterior local teve1399/1400, com um teste do modal de
  encaminhamento do Chat Interno que passou isolado23/23 e no CI1400/1400; divergencia nao
  chamada de corrigida. Nenhum componente do Chat Interno foi alterado para mascara-la.

Atualizado: 25/10/2026. Planejamento: [planejamento.md](./planejamento.md).
Estado operacional: **Z-API OFF; complemento de entrada protegida/midia/correlacao implementado
para homologacao, nao executado em conta real**. [Roteiro vigente A–D](./homologacao-protegida.md).
Base do complemento: origin/main `4c8955c`, apos integrar #321–#324 com CI aprovado.
Worktree isolada reutilizada; checkout principal e producao preservados.
Branch atual `codex/z-api-homologacao-protegida`; fechamento/CI registrados abaixo.

| Fase | Estado | Entrega / pendência | PR/base |
|---|---|---|---|
| 1 | Contrato/desenho incorporados; nova decisao explicita de seguranca | Segredo URL aprovado; header nativo ainda sem nome/configuracao, nao presumido | [PR #301](https://github.com/synapse-auto/ERP-Matriz-Synapse/pull/301), complemento atual |
| 2 | Saída/texto integrados, sem liberação operacional | CI corrigido/aprovado; entrada bloqueada por G1 | [PR #322](https://github.com/synapse-auto/ERP-Matriz-Synapse/pull/322), integrada em main |
| 3 | ACL anterior integrada; complemento de producao protegido OFF | Rota HTTP autentica antes do corpo/fila; namespace real ainda exige coleta | [PR #324](https://github.com/synapse-auto/ERP-Matriz-Synapse/pull/324) + complemento |
| 4 | Saida integrada; recebimento implementado no complemento | URLs documentadas, SSRF/DNS/bulkhead/spool/storage privado; CDN/codecs reais pendentes | [PR #323](https://github.com/synapse-auto/ERP-Matriz-Synapse/pull/323) + complemento |
| 5 | Codigo validado para homologacao de novos ciclos; operacao nao executada | Handoff/coleta/checklist; CI funcional aprovado, fechamento documental na PR #325 | [PR #325](https://github.com/synapse-auto/ERP-Matriz-Synapse/pull/325) |

## Complemento de homologacao protegida — 25/10/2026

### Preparacao operacional B–D apos merge da #325

- Branch `codex/z-api-roteiro-operacional`, baseada em `origin/main` merge `944536c`.
  Checkout principal e alteracoes alheias preservados. Nenhum merge/deploy nesta entrega.
- [Roteiro executavel](./roteiro-operacional.md): preflight, cadastro/UUID, envs, proxy,
  painel/callbacks, testes reais e plano separado de ciclos/filas/programadas da Femina.
- Cadastro separado simula por padrao e confirma apenas canal/credencial INATIVOS; UUID
  gerado no PostgreSQL, referencia `env://Z_API_TOKEN`, nenhuma credencial antiga alterada.
  Ativacao HML e explicita, exige banco vazio/visibilidade integral e nao serve para producao.
- Repositorio oficial fornecido pelo usuario auditado estaticamente no snapshot
  `0b8d420f1f592a7b5e55d28f3e9412ed785880d6`; confirma campos/painel, mas nao informa
  nome/configuracao de header de autenticacao de callbacks. Client-Token e de SAIDA.
  Typos receive/received e instanceId duplicado em exemplos antigos registrados no roteiro.
- Coleta somente leitura ampliada com host HTTPS sem caminho/query. Inventario separado
  de corte exige BYPASSRLS para nao apresentar filas/ciclos falsamente zerados por RLS.
- Testes dirigidos exercitam arquivos reais pelo psql em PostgreSQL migrado, nao copia
  da logica. Primeira rodada revelou incompatibilidade preexistente commons-compress/
  commons-io no empacotador TAR do harness; fixture transferida por execucao local com
  argumentos separados, sem TAR, em validacao. Positivos e existencia do arquivo evitam
  confundir falha do harness com rejeicao de seguranca.
  Nao foi escondida nem resolvida com upgrade de dependencias fora de escopo.
- Dirigidos finais: `CadastroCanalZApiIT` **19/19** (psql real, idempotencia/concorrencia,
  simulacao, preservacao integral Uzapi, conflitos, injecao, banco errado, ativacao HML
  com dados/sem BYPASSRLS recusada, inventario sem conteudo/segredo) e
  `ZApiWebhookProtegidoIT` **11/11**, incluindo coletor com host sem URL/path/query.
  Encontrado e corrigido cast JSONB ausente na ampliacao do coletor antes do fechamento;
  nova rodada completa dirigida BUILD SUCCESS, 2m23s. Nao tratado como flake.
- Stack+overlay `docker compose ... config --quiet` aprovados com valores sinteticos;
  isso nao valida versao/schema Traefik instalado. `git diff --check` aprovado.
- Release de aplicacao `944536c` confirmada na main: run **40216865849 SUCCESS**, inclusive
  publicacoes GHCR backend/frontend. Nao confundir com CI desta PR ou digest implantado.
- Clean verify Java 21 completo em execucao; resultado e CI do SHA final na PR/relatorio.
  Nenhum acesso VPS/Dokploy/Z-API, numero conectado, callback real, mensagem ou dado de
  producao alterado. Homologacao real e tratamento de ciclos antigos continuam pendentes.

- Autorizacao de segredo proprio URL substitui espera exclusiva pelo suporte. Header nativo
  preparado com nome vazio e teste sintetico; nome/prefixo/configuracao reais ainda pendentes.
- Recebimento/instancia/JSON/teto/dedup, status/correlacao/eco, download por URL com IP publico
  fixado no transporte, MIME real e streaming ao MinIO implementados, todos opt-in/OFF.
- Documento PNG/JPEG permanece DOCUMENTO. Sem resolvedor mediaId, proxy/redirect/credencial
  API para CDN. Compensacao de storage em rollback; nenhuma migration nova.
- Repasse n8n opt-in com token proprio e origem persistida; origem divergente/legado na Z-API
  bloqueados sem HTTP. A assinatura Meta continua preservada; Z-API nao recebe HMAC ficticio.
- Rodada dirigida aprovada antes do fechamento: 150 unidades e 64 integracoes
  (entrada protegida 9, entrada local 41, saida 9 e repasse Meta 5), sem falhas/erros.
  Depois foram acrescentados coletor/HTTP sem TLS/limite sem Content-Length e streaming/OOXML;
  resultados finais devem vir do clean verify completo, nao desses numeros anteriores.
- Frontend inalterado: 168 arquivos/1.400 testes, typecheck/lint/build aprovados, quatro
  avisos preexistentes; audit de producao total 0. Arvore dev tem 16 alertas preexistentes
  (6 moderate/9 high/1 critical), nao chamados de resolvidos nem removidos da auditoria.
- Primeiro clean verify parou no teste WebhookCanalControllerTest: mock esperava overload
  antigo sem origem. Fixture atualizada para exigir meta-cloud e conservar corpo/assinatura;
  nova execucao completa Java 21 em andamento. Nao e flake nem CI aprovado.
- Uma fixture Ogg/Opus sintetica completa revelou `application/ogg` no tika-core, recusado
  pela allowlist de audio. O downloader normaliza para audio/ogg somente apos validar
  estrutura Opus/EOS/duracao; Ogg truncado continua recusado. Dirigidos de midia 27/27 e
  controller Meta 2/2 aprovados; fixtures de audio/PDF agora completas, nao de cliente.
- Encontrada limitacao operacional preexistente: provisionador padrao e Meta/numerico;
  ciclos antigos preservam credencial original. Novo ciclo Z-API pode ser homologado, mas
  continuidade de ciclos antigos nao foi migrada silenciosamente. Detalhes no roteiro B/D.
- Ambiente: Docker estava fechado numa rodada dirigida, foi iniciado sem parar processo
  alheio. A rodada repetida usou Postgres/Redis/MinIO reais. Sem VPS, callbacks/numeros reais,
  envio a cliente, workflow publicado, troca de provedor ou deploy.

### Fechamento de codigo e evidencias

- Commit funcional `e4f3fcd320012bdd5f2d3cfdc6f4c34ad140422e`, push confirmado ao origin,
  PR [#325](https://github.com/synapse-auto/ERP-Matriz-Synapse/pull/325), base main `4c8955c`.
  As PRs anteriores ja estavam integradas; nenhum diff foi duplicado por encadeamento indevido.
- `clean verify` completo Java 21 local: **BUILD SUCCESS, 27m47s**, 405 relatorios,
  **2.700 casos, zero falhas/erros, 3 ignorados por ausencia de FFmpeg** (2 em
  `FfmpegConversorDeAudioTest`, 1 em `MinioArmazenamentoDeMidiaIT`). Nao chamados de resolvidos.
  A rodada local iniciou antes do ajuste final do retorno RFC 7807; o CI funcional abaixo
  recompilou/executou o codigo desse ajuste. Nao confundir execucao local com CI.
- No fechamento: HTTP protegido **11/11**, downloader **27/27**, filtro **9/9**, segredo
  **2/2**, DNS **2/2**, origem do repasse **9/9**, repasse autenticado **1/1**, entrada local
  **41/41**, saida **9/9**, detector OOXML **17/17**. MinIO **6 executados + 1 condicionado
  a FFmpeg**, incluindo escrita em fluxo sem leitura integral.
- Frontend **1.400/1.400 em 168 arquivos**, typecheck/build aprovados; lint sem erros e
  quatro avisos preexistentes. Audit de producao **0**. Sem alteracoes em arquivos frontend.
- Stack + overlay validados com `docker compose ... config --quiet`, ambiente sintetico.
  Nao comprova versao/configuracao do proxy real nem ausencia de captura por APM externo.
- CI funcional e4f3fcd: PR [40213592039](https://github.com/synapse-auto/ERP-Matriz-Synapse/actions/runs/40213592039)
  e push [40213271747](https://github.com/synapse-auto/ERP-Matriz-Synapse/actions/runs/40213271747)
  **SUCCESS**: Backend, Frontend e Stack aprovados; GHCR skipped conforme workflow de branch/PR.
  O complemento documental final tem seu proprio SHA/CI, registrados no fechamento da PR
  e em seus [checks atuais](https://github.com/synapse-auto/ERP-Matriz-Synapse/pull/325/checks).
- Zero deploy/merge/ativacao real. Homologacao real, header nativo, CDN/namespace, workflows e
  tratamento dos ciclos antigos continuam explicitamente separados do codigo aprovado.

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

#323 integrada em `832847d`, após PR **40172391861** e push **40172388439** SUCCESS
no SHA `bb5ec82`. Repetição local: mídia 32/32 + saída integrada 9/9, sem ignorados.
#324 incorpora essa main, preservando as permissões e a proteção de origem da outbox.
Ao combinar as branches, a verificação encontrou duas V111: a de acesso a clientes e
a de correlação. Renomeada somente a de correlação ainda não integrada para **V112**,
com SQL/hash preservados; schema existente não foi reescrito. Referências V111 nas
anotações históricas abaixo designam o nome anterior dessa migration de correlação.
O clean verify combinado e o CI do SHA final da #324 são registrados na própria PR;
resultado de predecessora não os substitui. Z-API permanece OFF e produção fechada.

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

## Complemento — recebimento de respostas interativas Z-API (27/10/2026)

Branch `codex/z-api-respostas-interativas`, criada sobre `main` em `bc277200a9f84baff5f94b1e9348f3144a5e4e8f`; não inclui commits das branches encadeadas anteriores. A auditoria da versão atual confirmou que `buttonsResponseMessage` e `listResponseMessage` já são traduzidos, persistidos como texto visível com metadado estruturado e repassados no envelope original. Esses caminhos foram mantidos; não foi criada segunda implementação.

- A documentação oficial atual também apresenta `pollVote` em ReceivedCallback, sem exigir `text.message`. O tradutor agora reconhece um único tipo interativo por evento, valida `pollMessageId` e `options[].name`, persiste os rótulos na ordem recebida e conserva `pollMessageId` como metadado — sem confundi-lo com o `messageId` do voto ou com uma citação.
- Identidade/deduplicação continua sendo o `messageId` próprio do callback. O envelope original segue para a outbox de automação. Referência ausente não impede persistir uma resposta válida; não foram criados aliases nem alterado o workflow.
- Testes direcionados: `ZApiWebhookTradutorTest` 48/48; `ZApiInteracoesIT` 38/38 e `ZApiWebhookProtegidoIT` 16/16 (54/54), com PostgreSQL, Redis, MinIO e proxy Traefik locais; transporte n8n simulado. Teste do componente da bolha 47/47. Typecheck passou. Lint passou sem erros e manteve quatro avisos preexistentes.
- A suíte frontend completa, executada no Windows, falhou em 10 testes não relacionados de `src/lib/config/aplicar-marca.test.ts`: os processos Node filhos encerraram com `0xC0000409`. O arquivo e seus recursos não foram alterados. Ainda é necessário determinar/reproduzir sem concorrência antes de atribuir a causa a limitação do ambiente; não classificar a suíte completa como aprovada.
- `./mvnw clean verify -Dmaven.compiler.release=21` com Java 21 e Testcontainers terminou em BUILD FAILURE após 27m49s: um teste fora do escopo, `RetornoDoLeadFinalizadoIT.finalizadoQueVolta_apareceNoGrupoPotenciaisParaColega`, esperava `EM_IA` e obteve `FINALIZADO`. A repetição isolada do mesmo caso passou 1/1 em BUILD SUCCESS; isso não torna a rodada completa verde nem prova estabilidade da suíte. Os testes Z-API executados antes da falha passaram.
- Frontend: componente direcionado 47/47; TypeScript e lint passaram (4 avisos preexistentes). Suíte completa: 10 falhas no arquivo preexistente `src/lib/config/aplicar-marca.test.ts`, subprocessos Node encerrados com `0xC0000409`; isolado repetiu 10/18 com o mesmo erro. Demais testes, excluído somente esse arquivo não alterado, passaram 1400/1400 em 167 arquivos. Build de produção e CI desta PR serão registrados no fechamento.
- A causa do incidente de produção relatado não foi comprovada: não foi fornecido callback sanitizado/ID técnico da ocorrência. A lacuna reproduzida localmente é o tipo oficial `pollVote`, antes classificado como não suportado; isso não prova que o incidente Femina era um voto de enquete. Nenhum replay, chamada externa ou mudança de produção foi realizado.
- `pollVote.pollMessageId` e `pollVote.options[].name` são uma ampliação separada do contrato para enquete; não explicam por si só a ocorrência de resposta de botão/lista. Não solicitar alteração ao Dylan sobre o incidente de botão sem uma captura do clique que demonstre formato/contrato incompatível. Botões/listas mantêm o contrato já existente. Nenhum workflow publicado foi alterado.
- Commit inicial `b6d16f5c007402ddb856af0f9ee629ad36dc5196`, branch `codex/z-api-respostas-interativas`, push confirmado e PR #334 aberta: https://github.com/synapse-auto/ERP-Matriz-Synapse/pull/334. Após este registro documental, será publicado novo SHA; o número/resultado do CI deverá corresponder a esse último SHA. Nenhum deploy.

## Diagnóstico específico do incidente de botões/listas após merge da PR #334

Branch `codex/zapi-button-diagnostic`, baseada na main pós-merge `a5be00f39a3663d8cf0642e707b9693de0bbaa1a`. A inspeção da main confirmou que o tradutor já lê `buttonsResponseMessage.buttonId/message` e `listResponseMessage.selectedRowId/message/title` antes do caminho `text.message`; a referência original é opcional e separada do ID próprio do clique. Não foi encontrado requisito de `text.message` nem filtro por referência ausente nesses dois formatos. Guards legítimos e pré/pós-persistência, diagnóstico somente leitura e diferença entre HTTP 2xx e execução do n8n estão documentados em `diagnostico-clique-interativo.md`.

Foi fortalecida a integração para afirmar explicitamente a ausência de `text`, a preservação dos campos interativos no HTTP de destino e o fluxo com `Origin` externo através do Traefik de teste, com segredo válido. O repasse é simulado; homologação produtiva permanece não verificada. A revisão atual de `main` tem o suporte. A imagem efetivamente implantada na Fêmina não foi consultada, portanto deve ser confirmada pelos comandos de inspeção da documentação.

Nenhum contrato do workflow foi mudado e nenhuma ação foi pedida ao Dylan para o caso de botão/lista sem evidência produtiva. `pollVote` permanece documentado como capacidade separada. Nenhum replay, deploy, alteração de n8n ou chamada de produção foi executado.

Validação desta branch: `ZApiInteracoesIT` 43/43 (PostgreSQL/Testcontainers; entrada HTTP real, proxy Traefik sintético, repasse HTTP simulado e consulta de diagnóstico); `./mvnw clean verify -Dmaven.compiler.release=21` terminou BUILD SUCCESS em 22m14s com Java 21.0.12, 1.522 testes, zero falhas/erros e um teste skipped, Spotless limpo. A primeira execução completa foi interrompida após a asserção de privacidade detectar que a consulta incluía o valor de `buttonId` e `referenceMessageId`; esses dois valores foram removidos do relatório, mantendo o `messageId` do clique e IDs internos de persistência/repasse, e a suíte dirigida e a verificação completa passaram depois da correção. `git diff --check` passou.

CI de `push` run 40562573746 no SHA `2878e75d7d6348a08541eea897153fa10a9412ad`: frontend e Stack passaram; backend falhou apenas em `ZApiInteracoesIT.escolhaSemTextoComOriginAtravessaProxyEEntregaCamposOriginaisAoDestino`, cuja prontidão aceitava HTTP 404 do health path sem confirmar que a rota dinâmica estivesse carregada. A falha observada foi 404 onde o callback esperava 200. O teste foi corrigido para aguardar a resposta 403 do filtro protegido a um segredo inválido antes de enviar o callback válido; esse probe não persiste evento. A validação direcionada pós-correção passou 43/43. O CI da revisão corrigida deve ser conferido pelo SHA mais recente da PR, sem considerar a run anterior como verde.
## Audio recebido sem EOS — 29/10/2026

- Diagnostico produtivo somente leitura encontrou OGG/Opus mono/48k, paginas completas e
  granule positivo, sem EOS na ultima pagina. A recusa do validador foi reproduzida localmente.
- [Compatibilidade restrita de entrada](audio-recebido-sem-eos.md): preserva bytes e uploads,
  exige CRC, sequencia/serial, headers/pacotes completos e granule no perfil sem EOS.
- Novos codigos fixos distinguem recusas sem expor URL/segredo/conteudo. Sem host novo,
  migration, env, replay ou deploy. Operador confirmou CRC valido, sequencia contigua,
  stream unico e pacotes encerrados no arquivo observado. Homologacao de novo audio
  permanece pendente e distinta dos testes sinteticos/CI.

### Recorrencia com taxa original 16k — 29/10/2026

- Imagem `bd32f48`: nova entrada falhou com `etapa=validacao codigo=OGG_INVALIDO`.
  Coleta posterior encontrou HTTP 200, OGG/Opus mono, 13 paginas/47258 bytes, CRC,
  sequencia, stream, pacotes e OpusTags validos; somente o perfil do OpusHead falhou
  pela taxa original 16000. A exigencia anterior de 48000 confundia metadado de
  entrada com taxa de reproducao (RFC 7845, 5.1). A primeira correcao era incompleta.
- O perfil recebido Z-API ignora esse metadado, sem converter bytes, e permite EOS
  somente no final ou ausente. Upload compartilhado e Meta/Uzapi ficam inalterados.
- Regressao reproduzida antes da correcao; depois, 44/44 testes dirigidos e 5/5 do
  coletor passaram. Integracao protegida inclui 16k com/sem EOS e taxa nao informada.
- `clean verify` Java 21 local foi executado e parou em
  `MinioArmazenamentoDeMidiaIT.iniciarMinio`: Docker indisponivel. Isso nao comprova
  a integracao; consultar CI do ultimo SHA. Sem replay/deploy e sem homologacao real.
