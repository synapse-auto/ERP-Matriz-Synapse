# Prompt E83 — Avisar o n8n após a finalização do atendimento

> Estado: regras do bloco 0 aprovadas pelo Marcondes nesta conversa. O bloqueio de decisão foi resolvido; prosseguir com a implementação e os testes no worktree dedicado, após as pré-verificações abaixo. Não solicitar novamente as mesmas três confirmações.
> Leia integralmente AGENTS.md, docs/13-estado-do-projeto.md e docs/prompts/COMO-ESCREVER-PROMPTS.md.
> Esta etapa não autoriza commit, push, merge, deploy, alteração no n8n nem chamada ao webhook real.
> A credencial enviada na conversa NÃO deve ser copiada para este documento, código, testes ou logs.
> Backend: Java 21, clean verify completo, Spotless, ArchUnit e Testcontainers. CI só pode ser declarado verde com número da run do SHA exato.

## Objetivo

Dylan precisa receber um POST quando um atendimento com responsável humano for realmente encerrado pela finalização individual, para iniciar a avaliação pelo WhatsApp. Finalização em lote não dispara avaliação. Clicar no frontend é a intenção; o fato confiável é a transição persistida para FINALIZADO.

O request de finalização não pode esperar pelo n8n. A intenção de notificar deve ser durável na mesma transação da finalização; o HTTP acontece depois, em processamento separado, sem conexão/transação de banco retida durante a rede.

## Bloco 0 — Regras de produto aprovadas

Marcondes confirmou expressamente as três regras abaixo com "sim", em resposta à proposta de excluir o lote, não avaliar atendimentos sem responsável e preservar a atribuição ao responsável. Esta decisão substitui as perguntas da versão anterior deste prompt.

1. **"Finalizar todos" NÃO inicia avaliações.** Encerrar normalmente os atendimentos permitidos, preservando eventos e notificações existentes, mas não criar intenção de avaliação nem chamar o webhook. A exclusão vale mesmo se o lote contiver somente um atendimento.
2. **Sem responsável humano: encerrar normalmente, sem iniciar avaliação.** Não criar intenção de avaliação, não chamar o webhook e não atribuir artificialmente a nota ao usuário que clicou.
3. **Finalização individual com responsável: iniciar avaliação para esse responsável.** O campo atendente_id é o responsável do atendimento no encerramento, mesmo quando um gestor executa a finalização. Não usar quemFinalizou como substituto. Preservar a semântica já implementada de CSAT.

Prosseguir com essas regras, sem nova parada para confirmá-las. Continuam valendo as condições técnicas de configuração, telefone válido, autorização, atomicidade e idempotência deste prompt. A aprovação não autoriza commit, push, merge, deploy, mudança no n8n ou disparo real.

Não disparar avaliações retroativas ao ativar a integração. Qualquer recuperação de backlog anterior precisa de autorização operacional própria.

## Estado conferido e preparação da branch

Inspeção em 28/08/2026:
- Worktree de documentação: C:/Users/marcondes/Desktop/projeto_matriz.
- Branch ativa na inspeção: feat/novo-contato-whatsapp, HEAD dca54e2f9496dfd7e7a52f8f95c20433130d9ce6.
- Referência local origin/main: aed6f16d711ec39cc3cdfc62a93dc1653a435157.
- Os arquivos de finalização, registro da avaliação e outbox citados abaixo não diferiam entre esses dois commits.
- Havia prompts e arquivos temporários não rastreados; foram preservados. Nenhum arquivo funcional foi alterado por esta revisão.
- Durante a revisão surgiram cinco alterações funcionais concorrentes de novo contato (catálogo, schema, diálogo e testes). Não pertencem à E83 e não foram modificadas nem incorporadas por esta revisão.
- claude/handoff-27-08.md não existe neste worktree. docs/13 ainda descreve E58; não usá-lo como prova de ausência de funcionalidades recentes.
- As skills clean-code, architecture-patterns, api-design-principles e supabaseboaspraticas não estavam disponíveis na sessão de revisão; carregar as aplicáveis se disponíveis na sessão de implementação.

Antes de escrever código, confirmar branch, HEAD, origin/main, worktrees, status e diffs staged/unstaged. Atualizar a informação remota sem alterar o checkout de outro agente. Não usar a main local antiga nem incorporar feat/novo-contato-whatsapp por conveniência.

Trabalhar em branch dedicada codex/e83-webhook-avaliacao, criada da origin/main atual confirmada, em worktree próprio. Se já existir, verificar origem e posse antes de usá-la. Não tocar em main, hotfix, fixtwo, hmlgc ou no trabalho de outro agente. Se os contratos relevantes mudaram, reconferir antes de aplicar este desenho.

## Contexto — código existente e consequências

### Finalização já tem evento, mas não tem esse POST

backend/crm-atendimento/src/main/java/com/synapse/crm/atendimento/application/FinalizarAtendimentoUseCase.java:

```java
Atendimento finalizado = atendimentos.salvar(aberto.finalizar(agora));
leads.marcarStatus(aberto.leadId(), StatusBasicoLead.FINALIZADO);

eventos.publishEvent(new EventoDeAtendimento.AtendimentoFinalizado(
        aberto.leadId(), aberto.id(), quemFinalizou, agora));
```

O método usa Pools.CHAT_TRANSACTION_MANAGER. O evento contém quemFinalizou, não o responsável e o telefone. Listeners existentes atendem timeline, auditoria e tempo real; não foi encontrado o disparo INICIAR_AVALIACAO.

FinalizarAtendimentosVisiveisUseCase.executar() chama finalizar.executar(...) para cada atendimento visível. Como o lote foi excluído da pesquisa, distinguir a origem no backend antes de enfileirar: reagir indiscriminadamente ao evento atual dispararia avaliações indevidas.

### Coleta da nota já existe — não recriar

backend/crm-atendimento/src/main/java/com/synapse/crm/atendimento/application/RegistrarAvaliacaoUseCase.java:

```java
if (atendimento.atendenteId() == null) {
    throw new AtendimentoSemAtendenteParaAvaliacaoException(atendimentoId);
}
// ...
return avaliacoes.salvar(Avaliacao.registrar(
        UUID.randomUUID(),
        atendimentoId,
        atendimento.atendenteId(),
        nota,
        comentario,
        agora));
```

O n8n já pode devolver o resultado por POST /internal/v1/atendimentos/{id}/avaliacao, autenticado com X-Synapse-Token, corpo {"nota": 5, "comentario": "opcional"}. A escala é 1–5 e a nota pertence ao responsável do atendimento. A segunda avaliação do mesmo atendimento é recusada; não alterar isso nesta etapa.

AvaliacaoAtendimentoIT contém testes HTTP de criação/leitura, conversa de colega, duplicata, conversa aberta, nota fora da faixa e autenticação da Automação. Foram lidos, não executados nesta revisão.

### Armadilhas na reutilização

1. OutboxRepositorioJdbc já utiliza outbox_evento e separa canal.mensagem.enviar de automacao.webhook.repassar. O repasse atual encaminha o corpo cru da Meta e X-Hub-Signature-256 para AUTOMACAO_WEBHOOK_EVENTOS_URL. Esse fluxo NÃO é o webhook de avaliação: preservá-lo integralmente.
2. PublicadorDeRepasseWebhookOperacoes.rodada() ainda tem @Transactional envolvendo o HTTP. NÃO copiar essa implementação para avaliação. Usar como referência de separação PublicadorDaOutboxOperacoes + PublicadorDaOutboxTransacoes: reserva curta, rede fora de transação e persistência curta do resultado.
3. AtendimentoRepositorioJdbc lê porId sem bloqueio e salvar usa upsert sem condição sobre estado anterior. O agregado impede repetição sequencial, mas isso sozinho não prova proteção contra duas finalizações concorrentes. Uma pesquisa duplicada afeta diretamente o cliente; testar e resolver a disputa no banco, sem relaxar RLS.

## Bloco 1 — Contrato de saída e configuração

Destino informado por Dylan, somente para configuração operacional:
https://n8n.187.77.47.30.sslip.io/webhook/estrutural-vidros/avaliacao-atendimento

Método POST, Content-Type application/json. Header exigido pelo destinatário: crm-synapse-marc-auth. Valor obtido exclusivamente de segredo do ambiente, nunca do navegador, banco, outbox ou catálogo público.

Corpo com EXATAMENTE os nomes abaixo, em snake_case, sem barras invertidas:

```json
{
  "modo": "INICIAR_AVALIACAO",
  "status_finalizacao": "FINALIZADO",
  "atendimento_id": "<UUID do atendimento encerrado>",
  "lead_id": "<UUID do lead desse atendimento>",
  "atendente_id": "<UUID do responsável humano do atendimento no encerramento>",
  "wa_id": "<telefone canônico do lead: DDI e dígitos, sem +>"
}
```

O exemplo é documental: UUIDs e telefone devem vir do backend, não aceitar esses valores do cliente da API de finalização. Não copiar os UUIDs do exemplo de Dylan para fixtures.

- Obter telefone pela porta existente LeadNoCaminhoDeMensagem.contatoParaEnvio, respeitando o contexto RLS e a transação do chat. Não criar consulta irrestrita à ficha de lead.
- Capturar responsável e contato consistentes com a finalização, não reler o responsável atual do lead no momento de um retry.
- Manter payload da integração no adaptador/ACL. Não colocar URL, nome de cliente ou JSON externo no domínio.
- Sem telefone válido: não inventar número, não enviar HTTP inválido e não impedir encerramento por falta de destino. Registrar motivo operacional sem PII.
- Configuração opcional por instância, com nomes sugeridos AUTOMACAO_AVALIACAO_URL, AUTOMACAO_AVALIACAO_TOKEN e AUTOMACAO_AVALIACAO_AUTH_HEADER. Default desabilitado; nome do header pode variar por filho.
- Configuração incompleta/desabilitada: não chamar serviço externo; deixar condição observável sem vazar valores. Não usar um token vazio para tentar chamadas.
- Atualizar application.yml, .env.example, README.md e docker/dokploy-stack.yml. Variáveis opcionais usam default vazio; não tornar esta integração requisito de boot/deploy.
- Não publicar segredo em /api/v1/config, NEXT_PUBLIC_*, serialização de properties ou mensagens de erro. Não seguir redirecionamento HTTP levando o header a outro host.

## Bloco 2 — Finalização e intenção durável

- Enfileirar intenção somente para finalização individual elegível, na mesma transação que efetiva a transição. HTTP somente depois do commit. Lote e ausência de responsável não geram intenção de avaliação.
- Não usar apenas AFTER_COMMIT + @Async como fila: processo pode morrer depois do commit e antes de registrar a intenção.
- Reutilizar outbox_evento com tipo próprio, métodos explícitos de porta e filtragem por tipo. Não disfarçar avaliação de mensagem WhatsApp ou repasse cru da Meta.
- Chave durável determinística por tipo de notificação + atendimento_id: o domínio atual não reabre o mesmo atendimento. Novo atendimento do mesmo lead pode ter nova pesquisa. Não deduplicar por telefone ou apenas lead_id.
- Resolver concorrência da transição com operação condicional/bloqueio apropriado ao repositório e à RLS; não depender só de get seguido de upsert. Preservar responsável e instante vencedores, sem sobrescrevê-los com cópia desatualizada.
- Provar disputa com transferência/envio concorrente quando o ajuste alcançar essas operações. Não fazer refatoração ampla de todos os repositórios.
- Falha de persistência da intenção não deve produzir um encerramento confirmado com evento perdido. Preservar atomicidade local; diferenciar erro de banco de indisponibilidade do n8n.
- Rollback não deixa intenção publicável. Evento duplicado/repetição não gera segunda intenção.
- Preservar timeline, auditoria, notificações, permissões e finalização individual/lote. Representar a origem individual/lote explicitamente no backend, definida pelo ponto de entrada, sem booleano livre que o frontend possa manipular para contornar a política. Não alterar nem suprimir eventos de finalização existentes para excluir o lote da pesquisa.
- Não notificar em sair do atendimento, transferência, devolução à IA ou conversa interna.
- Se exigir migration, criar nova; não editar migration aplicada. Justificar necessidade, índice e impacto.

## Bloco 3 — Entrega assíncrona isolada

- Job real com reserva persistida/lease em transação curta; chamada HTTP fora de transação; conclusão em outra transação curta.
- Limitar lote, concorrência, fila, duração de HTTP e tentativas. Parâmetros configuráveis, sem alterar limites das mensagens normais.
- Executor e circuit breaker próprios da avaliação. N8n lento não pode ocupar os workers do WhatsApp nem manter conexões do chat enquanto aguarda.
- Usar ContextoDeServico só no processamento de fundo necessário; não usá-lo para ampliar acesso na requisição de finalização.
- Respeitar synapse.agendamento.habilitado e isolamento de PostgresIT. Não criar scheduler ativo globalmente durante toda suíte.
- Reserva vencida após morte do processo deve voltar a ser processável. Evitar que resultado atrasado de reserva antiga sobrescreva o de outra execução; justificar mecanismo e testar.
- Sucesso 2xx confirma recebimento HTTP, não que a avaliação já foi enviada ao cliente.
- Classificar timeout, desconexão, 429 e 5xx como falhas recuperáveis; erros permanentes/configuração precisam de estado e diagnóstico úteis, sem retry infinito. Respeitar backoff e limites.
- Pendência esgotada permanece inspecionável. Não apagar nem marcar como publicada para esconder erro.
- Se integração for desabilitada com pendências, não marcar como entregue; explicar no runbook como pausar/retomar e autorizar eventual backlog.
- Logs sem token, telefone completo, payload ou resposta bruta do n8n; usar id do evento, atendimento, classe de falha e status HTTP sanitizado.

Garantia honesta: entrega é pelo menos uma vez dentro da política de tentativas, não exatamente uma vez. O n8n pode aceitar e a resposta se perder. Dylan precisa deduplicar persistentemente por modo + atendimento_id ANTES de iniciar a avaliação. Não assumir que o workflow faz isso, nem modificá-lo nesta tarefa. Se combinar header extra de idempotência, documentar como extensão acordada, não como parte já existente.

## Bloco 4 — Testes e runbook

Testes obrigatórios novos, além da regressão existente:

1. POST autenticado real de finalizar individualmente atendimento com responsável e contato válido, integração configurada: transição + uma intenção; corpo e header corretos capturados por servidor HTTP fake local.
2. Fechamento pelo gestor de atendimento atribuído a outro usuário: atendente_id do webhook é o responsável original, não quemFinalizou; a futura nota continua atribuída ao mesmo responsável.
3. POST real de finalizar-lote: encerra os itens permitidos e preserva os eventos existentes, mas cria ZERO intenções de avaliação e ZERO chamadas ao webhook. Cobrir lote com um e com vários itens, itens recusados e rollback. Após encerrar em lote, nova tentativa individual do mesmo atendimento não deve disparar pesquisa retroativa.
4. Repetição sequencial e finalização concorrente: uma transição vencedora e uma intenção, sem duplicação do efeito.
5. Atendente que não alcança atendimento de colega: negativa de autorização/RLS; nada alterado, nada enfileirado, nenhum HTTP. Incluir sem autenticação.
6. Rollback após preparar intenção: nem encerramento confirmado nem evento publicável.
7. Finalização individual sem responsável: encerra normalmente, sem intenção de avaliação ou HTTP e sem atribuir o atendimento a quem clicou. Também cobrir sem telefone e integração desabilitada/incompleta, sem destino fictício e sem quebrar chat.
8. N8n indisponível/lento: finalizar não espera HTTP; mensagens normais continuam processáveis; não manter transação ativa durante chamada fake bloqueada.
9. Job agendado pelo ponto de entrada usado em runtime, reserva concorrente, recuperação de lease, retry, esgotamento e resultado tardio.
10. Timeout depois de aceite: mesmo identificador lógico no retry. Não simular isso como prova de deduplicação no n8n real.
11. Testar retorno real pelo endpoint interno de avaliação, 1–5, responsável preservado e negativa de token/duplicata. Não enviar WhatsApp real nos testes.
12. Regressão do envio normal e repasse cru: destino, assinatura e isolamento preservados.

Usar Postgres Testcontainers, relógio controlado, latches/Awaitility por condição; nada de Thread.sleep, sleeps longos ou timeout aumentado para esconder instabilidade.

Criar runbook enxuto em docs/ com:
- sequência CRM encerra → outbox → n8n → coleta → POST interno já existente;
- tabela do contrato, headers distintos nas duas direções e configuração sem valores de segredo;
- ativação em ambiente controlado e lead de teste, dependente de autorização;
- evidência por atendimento_id/evento, distinção entre enfileirado, recebido pelo n8n e avaliação enviada;
- retry, pausa, rotação de segredo, backlog e reprocessamento sem pesquisas duplicadas;
- lembrete de que opt-in, janela/template WhatsApp e envio da pesquisa pertencem ao workflow responsável; não enviar mensagem direta paralela pelo CRM nesta etapa.

## Definição de pronto

- [x] Decisões do bloco 0 registradas e aprovadas pelo Marcondes; implementação ainda deve prová-las.
- [ ] Finalização individual autorizada, elegível e confirmada cria intenção durável idempotente.
- [ ] Lote e atendimento sem responsável encerram sem gerar avaliação; nota pertence ao responsável, mesmo se gestor finalizar.
- [ ] POST reproduz o contrato, dados reais e credencial só no ambiente.
- [ ] N8n fora do ar não bloqueia a finalização nem envio/recebimento de mensagens.
- [ ] Concorrência, rollback, privacidade, lease e retry provados pelos testes nomeados.
- [ ] Coleta CSAT existente preservada e sem duplicação de pipeline.
- [ ] Configuração opcional e runbook documentados; segredo ausente do diff/logs.
- [ ] Java 21, clean verify completo e git diff --check aprovados.
- [ ] Frontend não alterado; se precisar mudar contrato/UX, justificar e executar suíte frontend completa.
- [ ] CI/deploy descritos pelo que foi realmente verificado, sem autorizar publicação implicitamente.

## Relatório de entrega

Seguir os sete itens de AGENTS.md. Informar:
- branch/base/HEAD, lista de arquivos e estado de commit/push, respeitando autorização separada;
- testes executados e não executados, números e negativos;
- definição implementada de atendente_id e regra individual/lote/sem responsável;
- estratégia de atomicidade, concorrência, lease, idempotência e falha permanente;
- evidências locais versus validação real do workflow, explicitamente separadas;
- divergências: docs/13 defasado; evento em memória não é webhook durável; repasse legado ainda executa HTTP em transação;
- ação necessária no Dokploy, nomes de variáveis e exemplos sem segredo;
- número da run do SHA exato, se houver publicação autorizada; caso contrário, CI não verificado.

Fora: alterar workflow n8n, disparar pesquisa real, criar escala nova/atribuição nova de CSAT, refatorar repasse legado, reformar UI, retroagir eventos, commit/push/merge/deploy sem autorização específica. Recomendar ao operador rotacionar a credencial compartilhada no chat; não rotacioná-la por conta própria.
