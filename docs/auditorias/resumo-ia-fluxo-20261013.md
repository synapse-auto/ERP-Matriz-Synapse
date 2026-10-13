# Auditoria do resumo por IA da ficha — 13/10/2026

## Evidência e limites

Base inspecionada: `origin/main` e6b23b6. Este documento não é diagnóstico de uma
execução de produção: não houve acesso a VPS, Dokploy ou n8n publicado. O template
`docs/n8n/resumo-ia-sob-demanda.json` está inativo; não foi publicado/alterado.
Cards de informações do chatbot no histórico são outro recurso e ficaram intactos.

### Defeitos comprovados no CRM e correção

| Defeito anterior | Correção |
|---|---|
| Dois POSTs com chaves diferentes podiam ler “sem ciclo” antes de inserir | Ordem de locks lead → atendimento, reaproveitando `AtendimentoParaAlteracao`; criação e outbox continuam atômicas |
| HTTP ao webhook dentro da transação do pool do chat | Reserva/registro em bean transacional separado; HTTP sem conexão emprestada; timer serial exclusivo do resumo |
| HTTP 401/403 virava `WEBHOOK_INDISPONIVEL` | Código `WEBHOOK_AUTENTICACAO_RECUSADA`, sem retry; outros 4xx → `WEBHOOK_RECUSADO` |
| Timeout podia virar falha definitiva apesar de o n8n estar executando | Suspende a outbox com `WEBHOOK_RESULTADO_INCERTO`, conserva o ciclo ativo e aceita callback válido posterior; não reenviar automaticamente |
| Qualquer resposta sem exceção contava como aceite | Apenas HTTP 200 (replay) e 202 (novo) são aceitos |
| Exceção de rede podia imprimir URL | Log só de classe da falha, IDs, etapa e HTTP; sem mensagem/stack do cliente HTTP |
| Clique após resposta perdida criava outra chave no navegador | Chave estável até estado terminal e reconsulta do ciclo canônico; trava síncrona contra duplo clique |
| Validação de status permitia escrita após troca de responsável | V104 captura responsável na criação; gravação/status recusam ciclo com dono diferente; lock do lead protege corrida com finalização/transferência |

A reserva é de **uma unidade por rodada**: com entrega serial, reservar um lote antes
de fazer HTTP envelhecia as leases dos últimos itens. Usa a frequência já configurada,
sem variável nova, polling adicional ou conexões de IA no request humano.
Falhar a entrega usa compare-and-set apenas em PENDENTE: um PROCESSANDO/CONCLUIDO
concorrente é evidência de callback e não pode ser sobrescrito pelo publisher.

### Contrato aditivo e compatibilidade

Endpoints permanecem os mesmos. `Solicitacao` ganha `diagnosticoCodigo` opcional/null
(inclusive nas respostas internas que usam esse schema). O snapshot OpenAPI acompanha
esse campo. Estados continuam PENDENTE, PROCESSANDO, CONCLUIDO e FALHOU.

- `WEBHOOK_RESULTADO_INCERTO`: entrega sem confirmação; conferir execução antes de nova tentativa.
- `CICLO_OBSOLETO`: atendimento encerrado ou responsável diferente do snapshot.
- O diagnóstico não substitui estado nem significa FALHOU. UI conserva o resumo anterior,
  apresenta orientação, bloqueia geração concorrente e continua consulta canônica de 30 s.
- 503 no POST indica ausência de configuração. Não cria ciclo/outbox.
- CONCLUIDO somente após `/resumo` confirmar texto e estado no mesmo commit.
- Callback explícito FALHOU com `erroCodigo: CICLO_OBSOLETO` pode encerrar o ciclo
  comprovadamente obsoleto, inclusive após finalizar. Não grava texto nem inicia IA.
- Nova tentativa só após estado terminal. Callback tardio de ciclo encerrado continua recusado.
- V104 não preenche snapshots antigos com o dono atual: isso inventaria histórico.
  Legados com snapshot NULL mantêm compatibilidade; **transferência passada não pode ser
  comprovada por essa coluna**. Também não detecta uma ida e volta ao mesmo responsável.
  Nesses casos, conferir a execução/timeline com o operador; não declarar cobertura absoluta.

Textos novos têm fallback no schema para catálogos antigos. Nenhuma família tipográfica,
layout geral, permissão, Specification/RLS ou card do chatbot foi alterado.

## Passagem objetiva para Dylan — template, não produção comprovada

O template versionado tem defeitos verificáveis, mas falta exportação sanitizada do
workflow publicado para afirmar que causaram a ocorrência real:

1. A URL de Contexto usa `$json.atendimentoId` da resposta de PROCESSANDO: funciona no
   2xx, pois `Solicitacao` contém esse campo, mas após RFC 7807 não há `atendimentoId`.
   Sem gate de HTTP, o próximo nó monta uma URL inválida. Ancorar a identidade nos IDs
   de `Validar contrato` e, sobretudo, não avançar após resposta de erro.
2. `neverError: true` sem ramo de inspeção de HTTP permite continuar após 401/403/409/422/5xx.
   Habilitar retorno do status HTTP e validar cada etapa antes de avançar. `Succeeded`
   sozinho não prova persistência; guardar apenas etapa/HTTP/IDs na evidência sanitizada.
3. `retryOnFail`, `maxTries` e `waitBetweenTries` estão dentro de `parameters`, e não como
   propriedades do nó. Conferir configuração efetiva na versão instalada do n8n. Não assumir
   que há retry; implementar rede/5xx limitado, sem retry de 4xx e sem nova geração após resultado ambíguo.
4. Não há ramo que comunica FALHOU nem atualização final de status na Data Table. Implementar
   falha definitiva de contexto/IA/escrita, checar o callback HTTP e registrar falha de callback
   para reconciliação. Não enviar CONCLUIDO depois de escrita recusada.
5. `Validar contrato` compara o header ao segredo sem exigir segredo não vazio. Se ambos
   estiverem ausentes, a comparação pode aceitar a chamada. Exigir configuração válida;
   preferir credential Header Auth do nó Webhook, sem desativar autenticação.
6. A sequência GET → INSERT na Data Table não prova reserva exclusiva. Modo fila não é,
   sozinho, prova de serialização por solicitacaoId. Demonstrar reserva atômica/exclusiva na
   infraestrutura do n8n e teste de duas entregas simultâneas da mesma chave.
7. Não há contrato do provedor de IA no repositório que comprove `$json.resumo`. Conferir
   endpoint, autenticação e formato real; não inventar campo/mapeamento nem publicar template genérico.
8. Usar `contextoAte` exatamente como recebido. Persistir via EV-05 e conferir 2xx/aplicado
   antes de concluir a Data Table; callback CONCLUIDO é replay, não a escrita.

Solicitado: exportação sanitizada **do publicado**, versão do n8n e, para uma única
solicitacaoId, horários/status HTTP dos nós PROCESSANDO, Contexto, IA, Gravar resumo e
callback final. Sem token, URL privada, prompt, resumo, mensagem ou payload completo.

## Coleta operacional somente leitura

Operador escolhe o serviço e uma janela UTC; não exportar env nem logs brutos:

```bash
python3 coletar-resumo-ia.py \
  --servico '<nome-do-servico-backend>' \
  --solicitacao '<UUID-da-solicitacao>' \
  --desde '2026-10-13T00:00:00Z' --ate '2026-10-13T01:00:00Z'
```

Script: `docs/diagnosticos/coletar-resumo-ia.py`. Saída sanitizada JSON em stdout;
redirecionar para um arquivo local e compartilhar por canal privado. Logs antigos
sem solicitacaoId/status correlacionável não são reconstruídos por inferência.

No DbGate/psql, substitua **apenas** o UUID abaixo (nenhum texto/conteúdo é selecionado):

```sql
BEGIN READ ONLY;
SET LOCAL statement_timeout = '5s';
SET LOCAL TIME ZONE 'UTC';
SELECT s.solicitacao_id, s.atendimento_id, s.lead_id, s.status,
       s.solicitado_em, s.atualizado_em,
       CASE WHEN s.erro_codigo ~ '^[A-Z0-9_]{1,80}$' THEN s.erro_codigo END AS erro_codigo,
       a.status AS status_atendimento,
       l.resumo_ia IS NOT NULL AND btrim(l.resumo_ia) <> '' AS resumo_presente,
       l.resumo_ia_atualizado_em,
       o.tipo, o.tentativas, o.publicado_em, o.esgotado_em, o.proxima_tentativa_em,
       CASE WHEN o.ultimo_erro IN ('WEBHOOK_RESULTADO_INCERTO', 'WEBHOOK_INDISPONIVEL',
               'WEBHOOK_RECUSADO', 'WEBHOOK_AUTENTICACAO_RECUSADA', 'CICLO_OBSOLETO')
            THEN o.ultimo_erro END AS diagnostico_entrega
FROM solicitacao_resumo_ia s
JOIN atendimento a ON a.id = s.atendimento_id
JOIN lead l ON l.id = s.lead_id
LEFT JOIN outbox_evento o ON o.id = s.solicitacao_id
WHERE s.solicitacao_id = '<UUID-da-solicitacao>'::uuid;
ROLLBACK;
```

Não executar UPDATE/DELETE, reiniciar, limpar chave nem reprocessar. Compare imagem/SHA,
POST público, outbox, execução publicada e callbacks pelos mesmos IDs. Ausência de log
não prova ausência de tentativa (retenção, tarefas antigas, logging driver).

## Recuperação segura de solicitações antigas

1. Conferir se houve escrita e se a execução ainda pode prosseguir; timeout não basta.
2. Se o texto foi confirmado por EV-05, consultar o GET público e ficha; investigar evento/cache.
3. Sem escrita e execução comprovadamente encerrada, Dylan/operador autorizado comunica
   FALHOU pelo `/resumo-status`, IDs originais e código técnico sanitizado. Se obsoleto,
   usar CICLO_OBSOLETO. Confirmar 2xx e GET terminal antes de novo clique.
4. Se callback falhar ou execução for desconhecida/ativa, escalar e conservar ciclo;
   não liberar geração concorrente nem fabricar estado na UI.

Fontes oficiais verificadas sobre comportamento dos nós (não sobre o workflow publicado):
[HTTP Request: status e Never Error](https://docs.n8n.io/integrations/builtin/core-nodes/n8n-nodes-base.httprequest),
[queue mode e concorrência](https://docs.n8n.io/deploy/host-n8n/configure-n8n/scaling/enable-queue-mode.md).

Nenhuma variável nova no Dokploy. A futura implantação requer a migration nova V104:
o boot normal a aplica quando a V73 não está pendente. O runner one-shot existente é
específico da V73, não da V104. **Não houve deploy nem alteração de dados de produção**.
Homologação n8n/IA real e causa da solicitação real permanecem pendentes do Dylan.
