# Incidente — envios Meta sem progresso na Estrutural

Data: 05/11/2026. Horários citados em Brasília (UTC−03:00).

Status: operação recuperada por reinício controlado, conforme confirmação do operador. Causa raiz e correção definitiva pendentes. Este registro não comprova homologação posterior nem ausência de duplicidades.

## Contexto e impacto

Na Estrutural Vidros, mensagens enviadas pelo CRM ficaram com o indicador de relógio. A instância é identificada na infraestrutura como Matriz/HML, mas atende o cliente real.

- Serviço: `erp-matriz-hml-oxj4cd_backend`.
- Imagem observada: `ghcr.io/synapse-auto/erp-matriz-synapse-backend:19cbf81`.
- Banco: `matriz_hml`.
- Provedor configurado: `meta-cloud`.
- `CANAL_TRANSICAO_SAIDAS_SUSPENSAS=false`.

Não foram determinados o início exato, o total de mensagens afetadas ou a duração completa. Não há evidência suficiente para atribuir o incidente à Meta, ao banco ou à rede.

## Evidências coletadas pelo operador

### Outbox

Consulta somente leitura às 09:47:14 mostrou dez eventos recentes do tipo `canal.mensagem.enviar`, criados entre 09:41:23 e 09:47:13, com:

- `tentativas=0`;
- `publicado_em`, `despachado_em` e `esgotado_em` vazios;
- próxima tentativa já elegível para a maioria das linhas;
- nenhum código HTTP ou código de erro extraído nessas linhas.

Isso comprova acúmulo antes do despacho registrado nesses eventos. Não demonstra que nenhuma outra mensagem tenha alcançado o provedor.

### Logs

Havia avisos de listagem lenta e um timeout SQL na leitura da inbox às 09:40:09. Os exemplos de pool fornecidos ainda apresentavam conexões ociosas e nenhuma espera no instante do registro. Esses dados não provam esgotamento do pool nem causalidade com a paralisação do envio.

Às 09:45, os alarmes informavam 217 entradas esgotadas, sete saídas esgotadas e 3.778 repasses à automação esgotados. São contagens agregadas, sem recorte demonstrado para o incidente. Não devem ser apresentadas como quantidade de mensagens afetadas nesta ocorrência. A linguagem definitiva dos alarmes não comprova que o fornecedor jamais recebeu um envio.

Avisos de JWT expirado no handshake WebSocket também apareceram. Não foi demonstrado que expliquem o bloqueio do despacho no servidor.

### Captura de threads

Foi solicitado um thread dump com `kill -3 1` no container Java. A captura mostrou:

| Thread | Ponto de espera observado |
| --- | --- |
| `outbox-envio-1` | `CompletableFuture.get` → `JdkClientHttpRequest.executeInternal` → `MetaCloudApiAdapter.chamar` → envio da outbox |
| `agendado-3` | `CompletableFuture.join` em `PublicadorDaOutboxOperacoes.rodada` |
| `agendado-1` | HTTP em `MetaCloudApiAdapter.consultarIdentidadeDoCanal`, chamado pelo monitor de saúde |
| `agendado-4` | HTTP em `MetaCloudApiAdapter.buscarMidiaRecebida`, chamado pelo processador de entrada |
| `outbox-envio-2`, `3`, `4` | Espera por trabalho na fila do executor |
| `agendado-2` | Espera por tarefa agendada |

A captura localiza o drenador aguardando a conclusão de uma operação de envio que estava esperando HTTP. Os outros workers de envio estavam ociosos: não há evidência de que todos estivessem ocupados.

Uma única captura não mede a duração individual dessas esperas. O campo `elapsed` do dump é idade da thread, não duração da chamada HTTP. Também não distingue DNS, conexão, TLS, resposta, leitura ou defeito do cliente HTTP.

## Recuperação operacional

O operador executou, mantendo a imagem e configuração existentes:

```bash
docker service update --force --update-order start-first --detach=true erp-matriz-hml-oxj4cd_backend
```

A consulta seguinte mostrou um substituto iniciando e o backend anterior ainda rodando. Depois, o operador confirmou que o envio voltou.

Não foi fornecida a saída final do estado de atualização, uma medição de drenagem ou uma reconciliação de entregas. Não registrar essas verificações como realizadas.

Não houve orientação para replay, limpeza da outbox, alteração de status por SQL ou reenvio manual em massa. Um envio interrompido pode ter sido aceito pelo provedor sem confirmação local; por isso a recuperação não autoriza reprocessamento indiscriminado.

O comando de recuperação é histórico, não um procedimento para execução automática a cada alerta. O `start-first` solicita sobreposição de tarefas; não é garantia de indisponibilidade zero e exige capacidade e coordenação entre réplicas.

## Avaliação técnica

Há evidência de uma falha de contenção no CRM: o progresso do drenador ficou dependente da conclusão de uma operação HTTP. O reinício recuperou a operação, mas não corrigiu o mecanismo que permitiu o bloqueio.

O gatilho externo permanece desconhecido. Circuit breaker não substitui timeout efetivo ou cancelamento de uma chamada já em curso. Não foi comprovado se o problema decorre de timeout ausente, mal aplicado, incompleto ou de outra condição do cliente/transporte.

Falhas e lentidão externas são possíveis; a paralisação das demais conversas não deve ser aceita como consequência inevitável.

## Escopo da correção definitiva

1. Auditar o transporte HTTP Meta na versão implantada: conexão, espera pela resposta, leitura do corpo, cancelamento, uso de proxy e limites efetivamente aplicados. Cobrir envio, autenticação/saúde e recebimento de mídia.
2. Remover a dependência de uma espera sem limite na rodada da outbox. Preservar ordenação por conversa, reservas, locks e idempotência; permitir progresso de outras conversas sem simplesmente aumentar threads.
3. Separar as chamadas externas do monitor de saúde e do processamento de mídia dos recursos necessários ao despacho. Revisar transações mantidas durante rede e cancelamento de operações.
4. Tratar resultado incerto de POST sem marcar sucesso falso nem repetir cegamente um envio potencialmente aceito. Verificar também recuperação de reservas após reinício.
5. Detectar fila sem progresso: idade do evento elegível mais antigo, última rodada concluída, último despacho concluído e operação em curso. Diferenciar backlog, executor ocioso, falha terminal e resultado incerto.
6. Produzir diagnóstico sanitizado por etapa, sem tokens, URLs com credenciais, conteúdo de mensagens, telefones ou payload completo.

## Critérios de aceite da correção

- Testar pelos pontos de entrada reais do agendamento, outbox e adaptador.
- Simular servidor que aceita conexão e não envia headers; resposta com corpo que não termina; conexão lenta ou falha de rede; timeout após possível aceite do POST.
- Demonstrar que o limite encerra ou isola a operação e que mensagens de outra conversa avançam enquanto uma chamada está retida.
- Demonstrar que saúde ou mídia lentas não paralisam o envio.
- Preservar ordem dentro da conversa e não criar despacho concorrente indevido após timeout, lease vencida ou reinício.
- Comprovar comportamento de envio incerto e negativos contra duplicação, sem prometer exatamente uma entrega externa sem suporte do contrato.
- Comprovar que alerta de ausência de progresso dispara antes da intervenção manual, com configuração explícita e sem alarmes recorrentes tratados como ruído.
- Usar esperas por condição e testes determinísticos; executar Java 21, ciclo completo e CI com run identificada conforme AGENTS.md.

## Pendências

- Identificar causa raiz do transporte e duração das chamadas afetadas.
- Quantificar impacto por recorte técnico autorizado.
- Verificar progresso, entregas e eventuais ambiguidades depois da recuperação.
- Revisar os alarmes agregados encontrados, separando passivo anterior desta ocorrência.
- Implementar, revisar e homologar a correção. Até lá, incidente mitigado, não definitivamente resolvido.

Este documento foi preparado a partir de saídas e confirmações compartilhadas pelo operador neste atendimento. O autor não acessou diretamente a VPS nem executou ações em produção.
