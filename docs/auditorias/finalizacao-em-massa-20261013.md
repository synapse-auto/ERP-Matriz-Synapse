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

Docker local respondeu “Docker Desktop is unable to start”; `docker desktop start` informou já aberto.
Nenhum restart foi executado para não interromper outros trabalhos. Isso bloqueia Testcontainers e homologação
visual com backend/banco real até recuperação do engine. CI Linux não substitui screenshots nem a ocorrência real.

Nenhuma migration, variável nova, índice ou deploy. Ação necessária no Dokploy: **nenhuma configuração nova**.
Skills clean-code/architecture-patterns/api-design-principles/supabaseboaspraticas não disponíveis; Playwright CLI
carregada para validação real quando o ambiente puder subir. Não há evidência visual fabricada.
