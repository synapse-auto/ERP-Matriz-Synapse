# Acesso a clientes separado do Chat Interno

## Decisão e causa

A leitura `atendimentos.ver` era estrutural e os níveis mínimos de Atendimentos/Contatos
não admitiam negar leitura. Desligar as ações não bloqueava Potenciais. Foi aprovado criar
`clientes.acessar`, ligado por padrão, sem converter usuários nem deduzir papéis pelo nome.
Chat Interno mantém leitura **e escrita** para participantes autorizados, sem ganhar acesso externo.
Detalhamento e rollout: [docs/47 §16](../../47-gestao-permissoes.md#16-acesso-a-clientes-independente-do-chat-interno).

## Evidências visuais reais — 23/10/2026

Banco local separado, somente seed e usuários sintéticos; frontend Next e backend Java reais,
PostgreSQL 15. Não é reprodução HTML e não houve homologação ou alteração de contas reais.
Fonte computada: `Inter, "Inter Fallback", Inter, sans-serif`; `document.fonts.status = loaded`.

- [Antes de negar clientes, Potenciais disponível — 1920px](antes-1920.png).
- [Clientes negados e mensagem enviada no Chat Interno — 1920px](interno-1920.png).
- [Mesma conversa interna — 390px](interno-390.png). Documento e viewport com 390px, sem overflow horizontal.
- [Controle salvo OFF na Gestão — 1920px](gestao-off-1920.png), com confirmação sensível existente.

Na segunda captura, a negação foi salva pela API oficial da Gestão para o Operador sintético.
Após recarregar, Potenciais, Agenda e contagens externas estavam ausentes; foi enviada pela
interface a mensagem sintética visível na imagem. Depois, em duas sessões reais, a Gestora
religou e desligou o controle pela tela e confirmou as alterações sensíveis. Sem F5 na sessão
do Operador, Potenciais reapareceu e sumiu novamente, mantendo a conversa interna aberta.
O socket local estava aberto em `/ws`; nenhum token ou conteúdo externo foi incluído no diagnóstico.

## Verificações

- RED: `AcessoAClientesTest` falhou nos 11 casos antes de existir a capacidade. GREEN: 11/11,
  mais `PoliticaDePermissoesTest` 31/31.
- Primeira rodada HTTP: negação na sessão antiga e novo login; ficha, histórico, mídia,
  download e ações recusados; escrita/leitura internas preservadas. `GestaoPermissoesIT` 24/24.
- Frontend completo, rodada de regressão: 168 arquivos, 1.391 testes passaram. Typecheck,
  lint e build executados; lint com quatro avisos preexistentes. Novas revisões devem ser
  verificadas novamente pelo CI do SHA final, não inferidas desta rodada.
- O ciclo `clean verify` completo identificou fixtures que chamavam helpers sem o contexto
  autenticado/técnico real. Foram ajustadas para passar pela publicação de RLS, sem liberar
  contexto ausente em produção. Resultado final e CI devem ser lidos no relatório da PR.
- Repetição direcionada após esses ajustes: 58/58, incluindo `GestaoPermissoesIT` 25,
  `GestaoTempoRealIT` 3, `ParticipacaoAtendimentoIT` 2, `AjustesDoVinculoE231IT` 15,
  `LeituraPorUsuarioMigrationIT` 2, `NonoDigitoMigrationIT` 10 e listener de avisos 1.
- O primeiro CI também expôs uma fixture de Campanhas que mudava a flag diretamente no SQL,
  sem invalidar o instantâneo de permissões. O preparo e a limpeza agora sincronizam esse
  cache explicitamente, sem mudar a política de produção. `CampanhaPublicoEControleIT` 19/19
  na repetição direcionada, incluindo regressão com a flag desligada previamente em cache.
  A run 39950981516 passou no SHA anterior a esse ajuste adicional da fixture; o resultado
  do SHA final deve ser conferido separadamente na PR.

## Custo de autorização

`desempenhoDoCache`: 50 leituras quentes, zero recálculos, 6,37ms/requisição nessa rodada local;
após alteração, um recálculo (7,01ms). Não é benchmark da VPS e não deve ser comparado como SLA.
[Planos de listagem e contagem](planos-locais.txt) no banco sintético mostram a porta de clientes
como `InitPlan` escalar (uma execução por statement), sem consultar tabelas de permissões por
linha. A amostra tem somente um lead, portanto não comprova escalabilidade de produção.
Não há índice novo, listas completas ou consultas por cartão. O contexto transacional recebe
mais um `set_config`; o cálculo de capacidades reutiliza o cache existente.

## Limites e operação

Nenhuma variável nova no Dokploy e nenhum deploy nesta task. V111 e backend novo precisam de
troca coordenada de réplicas fora do horário crítico; backend antigo com schema novo falha
fechado. Não executar a migration manualmente nem editar versões já aplicadas.

Não é necessário invalidar todos os JWTs por este toggle; a autorização usa revisão/cache do
banco, não permissões embutidas em token. O aviso revalida a interface e cancela caches externos;
outros nós usam também o intervalo existente de revalidação. Não há polling novo.

Não foram alterados responsáveis, disponibilidades da IA ou permissões de contas reais.
URLs de storage assinadas antes da revogação conservam o TTL existente; bytes já baixados
não podem ser apagados pelo CRM. Novas consultas e emissões/downloads autenticados são negados.
Não houve medição de CPU/carga em produção; resultados locais não comprovam desempenho na VPS.
