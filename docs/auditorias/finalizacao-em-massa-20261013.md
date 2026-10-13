# Auditoria — finalização em massa (13/10/2026)

## Escopo e ocorrência

Relato de Jardel/Andrílene não é prova de falha no worker. Não houve acesso à VPS nem SQL/replay em produção.
Sem ID da operação e dos ciclos afetados não é possível atribuir o incidente ou afirmar que casos antigos precisam
de reparação. Não usar nome, telefone ou responsável histórico como critério de correção.

## Caminho auditado

Menu da lista → JanelaFinalizacaoEmMassa → prévia/POST com Idempotency-Key → filtro de última mensagem (ou abertura)
no fuso da instância → RLS do solicitante → operação + itens congelados atomicamente → AgendadorDeFinalizacaoEmMassa
em contexto de serviço → lease e lote configurado → transação independente por item, locks lead→atendimento →
FinalizarAtendimentoUseCase → lead finalizado/sem responsável atual, dono histórico do ciclo preservado → evento
canônico e timeline após commit → conclusão/avisos restritos aos afetados → polling da operação ou fila pessoal →
invalidação da inbox/contadores. O lote não recebe IDs da página visual; todos os elegíveis entram até o limite.

Período não é data de criação do lead nem intervalo diário: datas inclusivas, hora inicial no primeiro dia e final
no último, hora final inclusiva no minuto. Atividade recente exclui ciclo aberto antigo de período antigo.
Negociação sem resultado é bloqueio comercial: não há escolha automática do CRM nem alteração da classificação IA.

## Defeitos comprovados no código e reprodução

1. Teste do caminho da finalização em massa antes da correção: ResultadoVendaObrigatorioException em negociação
   sem resultado. O worker convertia isso em FALHA/ERRO_INESPERADO. Correção: retorno IGNORADO com motivo específico,
   sob os locks existentes; não altera estado comercial, responsável, histórico nem publica finalização.
2. Teste frontend com 51 itens falhou por inexistência de botão de próxima página. Isso limitava **a inspeção do
   resultado**, não a seleção/processamento backend. Correção: paginação real de detalhes, sem baixar tudo.
3. Teste frontend EM_ANDAMENTO→CONCLUIDA com mesma contagem falhou: nenhuma invalidação. Correção: comparar
   operação/status/processados; atualização terminal e troca de operação são reconhecidas.
4. Contagem e INSERT SELECT rodam em snapshots distintos READ COMMITTED. Antes, LIMIT podia congelar só parte de
   um recorte que crescesse além do limite. Correção: sentinela limite+1 + rollback em excesso/zero; sem aumentar
   limite comercial, lock global de leads ou consulta extra. IT altera o recorte entre as duas consultas reais.

Não comprovado: qualquer um desses defeitos ter ocorrido no sábado relatado. Possíveis explicações adicionais:
fora do período de última atividade, transferência, mensagem posterior, negociação pendente, reabertura e tela
desatualizada. Conferir itens persistidos, IDs de ciclos e horários antes de recuperar qualquer caso.

## Verificação somente leitura pelo operador

Sem IDs e sem data exata do sábado, começar por
[localizar-finalizacao-em-massa.sql](../diagnosticos/localizar-finalizacao-em-massa.sql): nome apenas como entrada
de busca (não retornado), escolher UUID do usuário, informar início/fim locais explicitamente, localizar operações,
percorrer itens com cursor e verificar os novos ciclos em 1–3 IDs. Saída só técnica. Executar um bloco por vez
no banco Estrutural `matriz_hml` confirmado pelo operador; não deduzir identidade em caso de múltiplos candidatos.

Compartilhar IDs técnicos por canal privado. Escolher uma operação exata e 1–3 atendimentos exatos; não exportar
nomes, telefone, conteúdo, payload, segredo ou URLs. Substituir UUIDs e janela UTC pelos da ocorrência.
Rodar em DbGate/psql, sem alterar dados; timeout não autoriza aumentá-lo sem revisar o plano/recorte.

```sql
BEGIN READ ONLY;
SET LOCAL statement_timeout = '5s';
SET LOCAL TIME ZONE 'UTC';
SELECT id, solicitante_id, atendente_ids, periodo_inicio, periodo_fim, fuso,
       status, encontrados, finalizados, ignorados, falhas, criada_em, iniciada_em, concluida_em, lease_ate
  FROM finalizacao_em_massa
 WHERE id = '<UUID-OPERACAO>'::uuid
   AND criada_em >= '<INICIO-UTC>'::timestamptz AND criada_em < '<FIM-UTC>'::timestamptz;

SELECT i.operacao_id, i.atendimento_id, i.atendente_id AS responsavel_congelado,
       i.status AS resultado_item, i.motivo, i.processado_em,
       a.lead_id, a.status AS estado_ciclo, a.atendente_id AS responsavel_historico,
       a.iniciado_em, a.finalizado_em, a.em_negociacao, a.resultado_venda,
       l.status_basico AS estado_lead, l.atendente_responsavel_id AS responsavel_atual,
       (SELECT max(m.enviado_em) FROM mensagem m WHERE m.atendimento_id=a.id) AS ultima_mensagem,
       atual.id AS ciclo_aberto_atual, atual.status AS estado_atual, atual.iniciado_em AS abertura_atual
  FROM atendimento a JOIN lead l ON l.id=a.lead_id
  LEFT JOIN finalizacao_em_massa_item i ON i.atendimento_id=a.id AND i.operacao_id='<UUID-OPERACAO>'::uuid
  LEFT JOIN LATERAL (
    SELECT id,status,iniciado_em FROM atendimento WHERE lead_id=a.lead_id AND status<>'FINALIZADO'
     ORDER BY iniciado_em DESC,id DESC LIMIT 1
  ) atual ON TRUE
 WHERE a.id IN ('<UUID-CICLO-1>'::uuid, '<UUID-CICLO-2>'::uuid)
 ORDER BY a.id;
ROLLBACK;
```

Interpretação: item FINALIZADO + ciclo FINALIZADO = concluído; outro ciclo aberto mais recente = reabertura,
não desfazimento. Dono histórico não é dono atual do lead. IGNORADO explica uma reconferência/regra comercial;
FALHA exige investigar logs técnicos. Item ausente não prova falha: conferir se pertencia ao recorte/RLS/status
no momento da criação. Operação PENDENTE/EM_ANDAMENTO exige verificar agendamento/lease e imagem implantada.

Coletar imagem/tag/digest por `docker service inspect <SERVICO> --format '{{.Spec.TaskTemplate.ContainerSpec.Image}}'`.
Não exportar env ou logs brutos: restringir operação, janela e marcador ALERTA_FINALIZACAO_EM_MASSA, fornecendo
apenas horários, IDs e classe técnica do erro. Exceções existentes podem conter SQL/dados: sanitizar antes de gravar.

## Homologação segura

Ambiente descartável, dados sintéticos: lote >50, dois responsáveis, dois períodos, bordas em Brasília, negociação
com/sem resultado, disputa com transferência/finalização, atividade posterior, falha de um item, repetição da chave,
reabertura, negativo de colega e avisos apenas a afetados. Conferir banco + API + tela, não somente toast.
Não reprocessar operação antiga: mesmo com causa conhecida, é necessário verificar estado atual e efeitos
posteriores, além de autorização operacional específica. Nunca fabricar Não vendeu ou remover responsável por SQL.

## Evidências e limites da entrega

Resultados finais e CI do último SHA são anexados à PR. Testes anteriores ao patch registraram 2 falhas frontend
(16 existentes passaram) e 1 erro backend (8 existentes passaram). Após patch, testes dirigidos devem ser
rerodados; não chamar compilação ou teste com portas mockadas de integração HTTP/PostgreSQL.

Inicialmente Docker local respondeu “Docker Desktop is unable to start”; `docker desktop start` informou já aberto.
Nenhum restart foi executado por esta task. O operador recuperou o engine e as validações PostgreSQL foram retomadas.
Na primeira execução real passaram 46/47 ITs; o novo teste de finalização individual enviava `{}` onde o contrato
normal usa corpo ausente, recebendo 400. Fixture corrigida para `null`, sem relaxar validação ou autorização.

Playwright CLI headed, Next/Spring/PostgreSQL/Redis reais, fixture descartável: 54 pedidos, 53 finalizados,
1 IGNORADO/RESULTADO_VENDA_PENDENTE, 0 falhas, confirmado por banco e UI. Contador TODOS caiu de 54 para 1 sem F5.
Página 2 dos detalhes mostrou os três últimos finalizados, acionada por teclado. Desktop 1920×1080,
800×900 e 390×844; Inter computada, fontes loaded, DPR 1, sem overflow global medido em desktop e celular.
Screenshots em `frontend/output/playwright/massa-*.png`, somente dados sintéticos; sem mocks de API/browser.
Na comparação visual, o título legado “Finalizando atendimentos” continuava mesmo no resultado terminal.
Agora usa o título neutro do catálogo e o conteúdo mostra o estado canônico, sem nova consulta/polling.
Calendários permanecem abertos após escolher data (comportamento compartilhado existente): fechar pelo próprio
gatilho. Capturas durante preparação/CORS/boot incompleto não foram consideradas evidência de aprovação.

Testes dirigidos frontend 60/60, typecheck/lint/build aprovados (4 avisos anteriores); suíte completa local
interrompida após falhas no script de marca sob Node/Windows. Suíte Linux aprovada no CI da revisão 4343d2d,
run PR 38435955152. Backend dessa revisão também aprovado no CI, sem dispensar o clean verify local repetido.
Resultado definitivo local e CI do último SHA serão anexados à PR; revisão anterior verde não confirma SHA posterior.

`clean verify` Java 21 completo após recuperação do Docker: **BUILD SUCCESS, 20m45s**. FinalizacaoEmMassaApiIT
19/19, WorkerIT 11/11, RegressaoIT 6/6 e RecebimentoIT 2/2 (38 testes específicos); FinalizadosEReaberturaIT 9/9,
OpenApiIT 8/8. Sem alterações backend após essa execução. A segunda rodada visual repetiu 54/53/1/0 para outro
atendente sintético, preservando o atendimento ignorado do primeiro. Todas as sete capturas foram refeitas após
o ajuste do título. SQL de descoberta executado somente no banco descartável para validar sintaxe/colunas,
com UUIDs/janela sintéticos e nenhuma saída de dados pessoais.

Nenhuma migration, variável nova, índice ou deploy. Ação necessária no Dokploy: **nenhuma configuração nova**.
Skills clean-code/architecture-patterns/api-design-principles/supabaseboaspraticas não disponíveis; Playwright CLI
carregada para validação real quando o ambiente puder subir. Não há evidência visual fabricada.
