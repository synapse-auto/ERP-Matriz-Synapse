# 59 — Finalização em massa por atendente e período

Janela em **Atendimentos → ⋯ → Finalizar em massa** (substitui o antigo "Finalizar Todos"). Finaliza, em segundo
plano, os atendimentos abertos de um ou mais atendentes cuja última atividade caiu num período. Este documento é a
fonte para permissões, visibilidade, semântica do período, notificações, falhas parciais e limites.

## 1. O que faz — e o que nunca faz

| Faz | Nunca faz |
|---|---|
| Muda o `status` do atendimento para `FINALIZADO` (mesmo caminho do "Finalizar" individual: lead finalizado, timeline, avaliação, auditoria do atendimento). | **Excluir** lead, atendimento, mensagem, mídia ou histórico. Finalizar ≠ excluir. |
| Registra a operação inteira: quem pediu, quando, filtros, contagens, atendentes afetados, motivo de cada item ignorado/falho. | Reabrir ou mexer em atendimento que já estava finalizado (é só contado como "ignorado"). |
| Avisa **só** quem teve atendimento finalizado. | Avisar a equipe toda, quem foi apenas selecionado sem resultado, nem o solicitante (a menos que ele mesmo tenha sido afetado). |

## 2. Quem pode e o que enxerga

* **Permissão:** `atendimentos.finalizar_lote` (Atendimentos / Gerenciar, sensível). A mesma capacidade do fluxo
  antigo — **nenhuma permissão nova**. É conferida em **todos** os pontos: prévia, criação, consulta de status, itens e
  operações recentes (`@PreAuthorize @capacidades.permite(...)`), e na UI o item de menu só aparece com ela.
  Revogar a permissão do perfil bloqueia a próxima chamada, não só o menu.
* **Visibilidade (RLS, nunca no frontend):**
  * Quem enxerga todos os leads (SUBGESTOR, GESTOR, ADMINISTRADOR) pode escolher qualquer atendente existente.
  * Os demais só podem escolher **a si mesmos**; outro atendente → `403 ATENDENTE_FORA_DO_ESCOPO`.
  * A lista de elegíveis é montada **sob o RLS do solicitante** na criação; o worker roda em contexto de serviço só para
    executar o que já foi congelado.
  * Uma operação só é visível a quem a pediu ou a quem enxerga todos (`404 OPERACAO_NAO_ENCONTRADA` para os outros).
* O servidor **revalida tudo** (atendentes existem e são acessíveis, datas, horas, limites, permissão). A tela só
  evita pedido inútil; payload adulterado cai nas mesmas regras.

## 3. Filtros e semântica do período

Entrada: atendentes (1 a 100), data inicial, data final, hora inicial e hora final **opcionais**.

* **Fuso:** o da instância (`ZoneId`, padrão `America/Sao_Paulo`) — nunca o do navegador. A prévia devolve o fuso.
* **Datas inclusivas.** `de = 01/09`, `ate = 02/09` cobre de `01/09 00:00` até `02/09 23:59:59.999`.
* Sem hora inicial → `00:00` do primeiro dia. Sem hora final → fim do último dia.
* **Hora final inclusiva no minuto:** `18:00` inclui `18:00:59`. O limite superior é exclusivo no minuto seguinte.
* `horaInicio` vale só no **primeiro** dia e `horaFim` só no **último** (intervalo contínuo, não "faixa diária").
* **O que é "do período":** a **última mensagem** do atendimento (qualquer remetente); sem mensagem, `iniciado_em`.
  Abertura antiga com conversa recente fica **fora** de um período antigo.
* Atendimento só entra se estiver `EM_ATENDIMENTO` **e** `atendente_id` ∈ atendentes escolhidos.
* Validações (`422` salvo indicação): `PERIODO_INVALIDO`, `PERIODO_INVERTIDO`, `PERIODO_EXCEDE_MAXIMO`,
  `ATENDENTE_INEXISTENTE`, `ATENDENTE_INVALIDO`, `ATENDENTES_VAZIOS`, `ATENDENTES_DEMAIS` (máx. 100),
  `SEM_ATENDIMENTOS` (nada a finalizar), `LIMITE_EXCEDIDO`, `CHAVE_DE_IDEMPOTENCIA_INVALIDA`. Corpo malformado
  (lista vazia, data ausente) → `400` da validação de bean.

## 4. Fluxo

1. **Prévia** `POST /api/v1/atendimentos/finalizacoes-em-massa/previa` — total, contagem por atendente (inclusive
   zero), fuso, limite e se excede. Não grava nada.
2. **Confirmação explícita** na UI (aviso de que não se desfaz em massa + resumo dos filtros). Cada confirmação gera uma
   `Idempotency-Key`.
3. **Criação** `POST /api/v1/atendimentos/finalizacoes-em-massa` → `202` com a operação `PENDENTE` (ou `200` com
   `repetida=true` se a mesma chave/filtros já criou uma). Na **mesma transação**: grava a operação, **congela** os
   atendimentos elegíveis como itens e audita (`FINALIZAR_ATENDIMENTOS_EM_MASSA`).
4. **Worker** (`AgendadorDeFinalizacaoEmMassa`, a cada ~1 s): reivindica a operação com lease e processa em **lotes
   curtos**; **cada item é uma transação própria** (finaliza + marca o item juntos).
5. **Acompanhamento** `GET .../{id}` (status, contagens, percentual) e `GET .../{id}/itens?status=&pagina=&tamanho=`
   (detalhe). `GET .../` lista as operações recentes. A UI faz polling a cada 1,5 s; fechar a janela **não** interrompe,
   e reabrir leva direto à operação ativa (ou ao resultado em "Operações recentes").
6. **Conclusão:** registra `CONCLUIR_FINALIZACAO_EM_MASSA` e grava os avisos (outbox) **na mesma transação**.

Operação única ativa por instância (índice parcial único): enquanto uma roda, outra recebe `409 OPERACAO_EM_ANDAMENTO`.

### Reconferência na execução

O mundo muda entre a confirmação e a execução. Cada item é reavaliado e **ignorado, com motivo**, se não vale mais:

| Motivo | Quando |
|---|---|
| `JA_FINALIZADO` | já estava finalizado (outra pessoa, outra operação). |
| `TRANSFERIDO` | mudou de atendente. |
| `ATIVIDADE_POSTERIOR` | recebeu mensagem que o tira da janela do período. |
| `INDISPONIVEL` | ficou inacessível. |
| `RESULTADO_VENDA_PENDENTE` | negociação ainda sem resultado explícito: permanece aberta, deve ser finalizada individualmente com a escolha comercial. Não é falha técnica e o lote nunca inventa “Não vendeu”. Negociação com resultado já registrado pode finalizar normalmente. |
| `ERRO_INESPERADO` | exceção ao finalizar (status `FALHA`, ver §6). |

## 5. Notificações

* Canal: outbox `finalizacao_em_massa_aviso` (PK `(operacao_id, usuario_id)`) → publicador (`SKIP LOCKED`, retry com
  backoff, máx. 5 tentativas, depois `ESGOTADO`) → Redis `synapse:aviso-usuario` → `/user/queue/notificacoes`.
* **Quem recebe:** só o usuário com **≥ 1** atendimento finalizado sob sua responsabilidade. Não recebem: selecionado
  sem resultado, equipe, nem o solicitante (salvo se afetado).
* **Sem duplicidade:** a PK impede dois avisos por usuário/operação; o `eventoId` (`operacao:usuario`) é o mesmo em todo
  reenvio e a UI deduplica por ele (entrega é *at-least-once*).
* **Texto** (`textos.json` → `atendimentos.finalizacaoEmMassa.aviso`): "63 atendimentos foram finalizados em uma
  finalização em massa. Usuários afetados: Clayton e Nayara." + uma linha por atendente + (se houve) "N ignorado(s) e
  M com falha." — o **parcial** é informado.

## 6. Falhas parciais

* Falha de um item **não desfaz** os outros: o item vira `FALHA` (`ERRO_INESPERADO`) numa transação própria e o lote
  segue. A operação termina `CONCLUIDA` com `falhas > 0`; a UI mostra "concluída com pendências" e a aba "Falhas".
* O erro cru nunca sai pela API (só motivo categorizado; o detalhe vai para o log do servidor).
* **Queda do worker:** a lease (2 min) vence e outra rodada retoma do que está `PENDENTE`; item já finalizado não é
  finalizado de novo (idempotência por item).
* **Retry de criação:** mesma `Idempotency-Key` + mesmos filtros → mesma operação (`200`, `repetida=true`, **sem**
  nova auditoria); mesma chave com outros filtros → `409 CHAVE_DE_IDEMPOTENCIA_REUTILIZADA`. A ordem/duplicidade dos
  atendentes não altera a impressão dos filtros.

## 7. Limites (configuráveis — `configuracao_automacao`)

| Chave | Padrão | Efeito |
|---|---|---|
| `atendimento.finalizacao_em_massa.limite_por_operacao` | 5000 | acima disso a prévia marca `excedeLimite` e a criação recusa (`LIMITE_EXCEDIDO`). |
| `atendimento.finalizacao_em_massa.periodo_maximo_dias` | 31 | período maior → `PERIODO_EXCEDE_MAXIMO`. |
| `atendimento.finalizacao_em_massa.lote` | 50 | itens por rodada do worker (mantém a conexão do chat livre; o painel segue respondendo). |

Intervalos do agendador: `synapse.atendimento.finalizacao-em-massa.intervalo-ms` (1000) e `.avisos-intervalo-ms` (2000).

## 8. Auditoria

`FINALIZAR_ATENDIMENTOS_EM_MASSA` (criação: solicitante, filtros, atendentes, total) e `CONCLUIR_FINALIZACAO_EM_MASSA`
(conclusão: contagens), entidade `FINALIZACAO_EM_MASSA`. Cada atendimento finalizado também passa pela auditoria normal
de finalização. A operação, os itens (com motivo) e os avisos ficam nas tabelas `finalizacao_em_massa*`.

## 9. Operação (runbook)

* **Operação parada em `EM_ANDAMENTO`:** conferir `lease_ate`; vencida, a próxima rodada assume. Se o agendador estiver
  desligado (`synapse.agendamento.habilitado=false`) nada processa.
* **Aviso `ESGOTADO`:** o Redis ficou fora por mais de 5 tentativas; o resultado continua consultável na janela.
  Conferir diagnóstico antes de uma recuperação operacional autorizada. Não executar SQL de reparação ou replay
  como parte da investigação; ausência de toast não comprova ausência de finalização.
* Log de alarme: `[ALERTA_FINALIZACAO_EM_MASSA]` (rodada de processamento ou de avisos que falhou).

## 10. Compatibilidade e limites conhecidos

* `GET/POST /api/v1/atendimentos/finalizar-lote` (síncrono, sem período) **continua no backend** por compatibilidade; a
  UI não o usa mais para finalizar. A lista de atendentes da janela vem do `GET` desse endpoint (atendentes com
  atendimento aberto visível ao usuário).
* Não há estimativa de tempo na prévia: o servidor processa em lotes de ritmo configurável e a UI mostra progresso real.
* Uma operação ativa por instância; recusar a segunda é intencional (evita concorrência de lotes sobre os mesmos
  atendimentos).

## 11. Auditoria e correções de 13/10/2026

* O processamento já congela o recorte no servidor, independentemente das páginas da inbox. A interface dos
  **detalhes do resultado** só mostrava os primeiros 50 itens; agora permite percorrer as páginas em cada categoria.
* A conclusão revalida lista, contadores e operações recentes mesmo quando todos os itens já estavam processados
  na leitura anterior; o dashboard é invalidado ao concluir ou receber o aviso pessoal, sem novo polling.
* Sob READ COMMITTED o recorte pode crescer/esvaziar entre contagem e congelamento. Congela-se no máximo limite+1
  (sentinela, não aumento do limite). Excesso → 422 LIMITE_EXCEDIDO; zero → 422 SEM_ATENDIMENTOS. Toda a criação
  é desfeita nessas situações: nenhum lote truncado ou sucesso vazio é aceito. A prévia é estimativa, o resultado
  congelado é o recorte autorizado efetivo. Não há locks de todos os leads, histórico completo ou nova varredura.
* Negociação sem resultado anteriormente caía como ERRO_INESPERADO/FALHA. Agora é IGNORADO com
  RESULTADO_VENDA_PENDENTE. Contrato aditivo de motivo; mesmos endpoints, payloads, estados e autorização.
* Propriedade: o caminho existente finaliza o lead e limpa **lead.atendente_responsavel_id**, preservando o
  responsável histórico **atendimento.atendente_id**. Ver esse responsável no atendimento encerrado não significa
  ciclo aberto. Uma mensagem/reativação posterior pode abrir **outro UUID de atendimento** sem desfazer o anterior.

Diagnóstico somente leitura, limitações da ocorrência relatada e evidências:
[auditoria de finalização em massa](auditorias/finalizacao-em-massa-20261013.md).
