# Prompt E83b — Corrigir deadlock entre finalização e mensagens antes de publicar a E83

> Leia integralmente AGENTS.md, docs/13-estado-do-projeto.md, docs/prompts/COMO-ESCREVER-PROMPTS.md e o Prompt E83 aprovado.
> Esta é uma correção da E83 existente, não uma implementação nova do webhook.
> Não autoriza commit, push, merge, rebase, deploy, troca da tag, chamada ao n8n real ou alteração do workflow.
> Preservar as alterações não commitadas da E83. Não recomeçar de origin/main nem transportar trabalho de outras branches.
> Java 21 fixo; verificar o reator completo com clean verify, Spotless, ArchUnit e Testcontainers. CI sem número da run é não verificado.

## Objetivo e parecer da revisão

A E83 ainda NÃO está aprovada para publicação. As três regras comerciais e a separação do HTTP foram conferidas, e a suíte existente passou novamente na revisão. Porém, o novo bloqueio explícito de atendimento pode causar deadlock com mensagem recebida ou mensagem da automação. Isso pode abortar a gravação da mensagem ou a finalização, mesmo com o webhook de avaliação desabilitado.

Corrigir a coordenação de banco sem retirar as garantias da E83 e acrescentar regressões que reproduzam a disputa pelos caminhos reais. A aba Atendimentos não pode ser prejudicada para viabilizar a pesquisa.

## Bloco 0 — Worktree, estado e decisões já aprovadas

Trabalhar somente em:

- Worktree: `C:/Users/marcondes/Desktop/projeto_matriz-e83`.
- Branch: `codex/e83-webhook-avaliacao`.
- HEAD/base conferido: `aed6f16d711ec39cc3cdfc62a93dc1653a435157`.
- Implementação E83: 27 arquivos novos/modificados, ainda sem commit; nenhum frontend alterado.
- O Prompt E83 e este complemento foram gravados no repositório de documentação: `C:/Users/marcondes/Desktop/projeto_matriz/docs/prompts/`. Se não existirem no worktree E83, ler esses arquivos pelo caminho absoluto; não trocar de branch para encontrá-los.

Antes de editar, confirmar `git worktree list`, branch, HEAD, status, diff staged/unstaged e ausência de operação Git em andamento. Registrar o estado real. Se outro agente estiver modificando os mesmos arquivos, parar para coordenação; não descartar, sobrescrever ou incorporar silenciosamente mudanças concorrentes.

As regras abaixo estão aprovadas. NÃO perguntar novamente:

1. Finalização individual elegível gera intenção durável de avaliação.
2. Finalizar todos NÃO gera avaliação, inclusive lote de um item.
3. Sem responsável humano, encerrar normalmente e NÃO gerar avaliação.
4. A avaliação pertence ao responsável do atendimento no encerramento, não ao gestor que clicou.
5. Nada retroativo; o endpoint interno de retorno CSAT e sua escala 1–5 permanecem iguais.

Não alterar payload de seis campos, nomes dos headers, atribuição, política de retry, lease, circuito, autorização ou contrato público para resolver este problema de bloqueios.

## Evidência conferida — o teste de envio manual não cobre o recebimento

### Novo bloqueio da E83

`backend/crm-atendimento/src/main/java/com/synapse/crm/atendimento/application/AtendimentoParaAlteracao.java`, linhas 14–20 na revisão:

```java
Atendimento visivel = atendimentos.porId(id)
        .orElseThrow(() -> new RecursoDeAtendimentoIndisponivelException("atendimento", id));
if (!leads.bloquearParaAtendimento(visivel.leadId())) {
    throw new RecursoDeAtendimentoIndisponivelException("atendimento", id);
}
return atendimentos.porIdParaAlteracao(id)
        .orElseThrow(() -> new RecursoDeAtendimentoIndisponivelException("atendimento", id));
```

`LeadNoCaminhoDeMensagemJdbc.bloquearParaAtendimento` trava a linha do lead com `FOR UPDATE`.

`backend/crm-atendimento/src/main/java/com/synapse/crm/atendimento/infrastructure/persistencia/AtendimentoRepositorioJdbc.java`, linhas 100–103:

```java
public Optional<Atendimento> porIdParaAlteracao(UUID atendimentoId) {
    TransacaoObrigatoria.exigir("porIdParaAlteracao");
    return primeiro(chat.query(SQL_POR_ID + " FOR UPDATE", MAPEADOR, atendimentoId));
}
```

FinalizarAtendimentoUseCase e TransferirAtendimentoUseCase usam esse helper. A ordem é lead → atendimento, com bloqueio explícito forte no atendimento.

### Caminho existente de recebimento

`backend/crm-atendimento/src/main/java/com/synapse/crm/atendimento/application/RegistrarMensagemRecebidaUseCase.java`, linhas 71–92:

```java
Atendimento aberto = atendimentos.abertoDoLead(entrada.leadId()).orElse(null);
// ... se já há atendimento, reusa a linha sem bloquear o lead antes ...
Mensagem gravada = mensagens.registrar(/* mensagem com aberto.id() */);
leads.registrarInteracao(entrada.leadId(), agora, abriu ? 1 : 0, 1);
```

`MensagemRepositorioJdbc.registrar` executa INSERT em mensagem. A migration `V5__atendimento.sql`, linha 39, define:

```sql
atendimento_id UUID NOT NULL REFERENCES atendimento(id)
```

O INSERT, pela checagem da FK, mantém bloqueio de chave compartilhada no atendimento até terminar a transação. Depois, `registrarInteracao` faz UPDATE no lead. `resolverPorTelefone` não bloqueia previamente um lead já existente: sua consulta é simples.

O mesmo padrão relevante aparece em:

- `ResponderAtendimentoDaAutomacaoUseCase.java`: INSERT da mensagem na linha 76, UPDATE do lead na linha 94.
- `RegistrarMensagemEnviadaDaAutomacaoUseCase.java`: reserva de idempotência que também referencia atendimento, INSERT na linha 66, UPDATE do lead na linha 67.

O envio manual tem uma ordem diferente: chama `leads.transferirPara` antes, que já bloqueia o lead. Por isso a regressão atual `envioConcorrenteAposFinalizacao_abreOutroAtendimentoSemMudarSnapshot` não captura este problema.

### Interleaving que causa o deadlock

| Passo | Recebimento/automação | Finalização/transferência |
|---|---|---|
| 1 | INSERT da mensagem; mantém KEY SHARE no atendimento pela FK | — |
| 2 | Pausa antes do UPDATE do lead | Obtém FOR UPDATE no lead |
| 3 | — | Tenta FOR UPDATE no atendimento; espera a transação da mensagem |
| 4 | Tenta UPDATE do lead; espera a finalização | Continua esperando a mensagem |

O banco detecta o ciclo e aborta uma transação com SQLSTATE `40P01`. Não depende de falha, lentidão ou configuração do n8n.

### Reprodução independente da revisão

Em 28/08/2026, a revisão executou um diagnóstico JDBC Java 21 contra um container PostgreSQL 15 isolado, com schema reduzido (lead, atendimento e mensagem particionada com a FK), duas conexões e CountDownLatch. Sem Thread.sleep, sem banco do CRM e sem chamada externa.

Resultados:

```text
FOR UPDATE trial=1 receiver=40P01 finalization=COMMIT
FOR UPDATE trial=2 receiver=40P01 finalization=COMMIT
FOR UPDATE trial=3 receiver=COMMIT finalization=40P01
FOR NO KEY UPDATE trial=1 receiver=COMMIT finalization=COMMIT
FOR NO KEY UPDATE trial=2 receiver=COMMIT finalization=COMMIT
FOR NO KEY UPDATE trial=3 receiver=COMMIT finalization=COMMIT
```

O container exclusivo foi encerrado. Esse diagnóstico prova o ciclo de locks; NÃO é uma execução do incidente em produção nem substitui uma regressão com o schema completo, RLS e os casos de uso do CRM.

A suíte anterior, executada independentemente na revisão, terminou BUILD SUCCESS: 157 testes unitários + 411 ITs = 568, sem falhas/erros/skips, 7min06s. A contagem verde não demonstra segurança para o interleaving acima, que não era exercitado.

## Bloco 1 — Reproduzir no código atual antes da correção

Criar regressão com Postgres Testcontainers, schema real, transações e portas reais. Reusar a infraestrutura existente. O teste deve falhar na E83 atual pelo defeito real, não por mock que lança uma exceção de deadlock inventada.

- Criar lead e atendimento próprios do teste. Para recebimento, usar atendimento já aberto: esse é o cenário que não obtém lock prévio ao inserir a mensagem.
- Coordenar duas transações reais com latches/barreiras e observação de estado de banco. Pausar depois do INSERT real da mensagem, antes do UPDATE real do lead; em paralelo, iniciar a finalização real.
- Liberar a mensagem quando a outra transação já tiver obtido o lead. Não depender de uma corrida probabilística.
- Acionar a finalização pela entrada HTTP autenticada e o recebimento pela cadeia real usada no processamento de webhook. Um teste transacional adicional pode isolar a disputa, mas não substituir toda a cobertura de ponto de entrada por SQL manual.
- Manter assinatura/autenticação do webhook conforme os fixtures existentes. Não usar n8n ou WhatsApp real.
- Garantir liberação dos latches e término das tarefas em finally, mesmo se falhar. Contexto de serviço e segurança devem seguir o caminho legítimo de cada operação; não executar todos os cenários como superusuário e declarar RLS validada.
- Observar o banco por IDs/conexões desta fixture. Evitar uma condição global como “existe qualquer sessão esperando lock”, que pode ser satisfeita por outro teste/contexto.
- Não capturar 40P01 e tratar como sucesso; não repetir o teste até passar; não aumentar timeout para mascarar bloqueio.

Registrar evidência vermelho → verde: teste exato, falha anterior e resultado após a correção. Não degradar a suíte inteira para obter o vermelho; executar a regressão direcionada primeiro.

## Bloco 2 — Corrigir com a menor mudança segura

A alternativa menor indicada pelo diagnóstico é avaliar `FOR NO KEY UPDATE` na leitura de atendimento para alteração de estado/responsável, em vez de `FOR UPDATE`. Esse modo continua serializando escritores, mas pode coexistir com a validação da FK que apenas referencia a chave do atendimento.

Essa é uma candidata, não autorização para uma substituição mecânica em todo o projeto. Antes de escolhê-la:

1. Conferir quais colunas a operação altera, índices/constraints que possam causar bloqueio mais forte e os locks efetivos do upsert `salvar`.
2. Provar a correção com a query completa e transações do CRM, não apenas com o schema reduzido.
3. Manter a leitura após obtenção do lock e a condição `WHERE atendimento.status <> 'FINALIZADO'` de `salvar`, ou outra garantia equivalente demonstrada. Cópia antiga não pode reabrir atendimento nem trocar seu responsável após o fechamento.
4. Preservar a serialização entre duas finalizações, transferência e envio manual. Nenhuma pesquisa ou troca de responsável duplicada.
5. Conferir recebimento, resposta da IA, registro de mensagem já enviada pela IA e finalização em lote: todos coexistem com os mesmos registros e FKs.

Se o modo de lock compatível não bastar, mapear a ordem completa e propor uma coordenação consistente nas portas/casos de uso estritamente necessários. Não inverter apenas finalização para atendimento → lead, pois isso pode recriar deadlock com o envio manual, que trava lead primeiro.

Não remover FK, RLS, autorização, transação, guarda de estado final ou idempotência para “liberar” a disputa. Não recorrer a lock global, lock de tabela, synchronized local, pool maior, timeout maior ou retry cego de toda finalização como solução principal.

**Ponto de parada real:** se a solução exigir mudar quando uma mensagem recebida abre novo atendimento, permitir resposta da IA em atendimento finalizado, mudar atribuição comercial ou refazer amplamente o pipeline, apresentar o conflito e a alternativa antes de implementar essa mudança de negócio. Isso não é motivo para reabrir as três decisões já aprovadas da avaliação.

## Bloco 3 — Matriz de regressão e documentação

Cobrir, com IDs próprios e coordenação determinística:

1. Mensagem recebida iniciada antes da finalização: sem deadlock, sem perda/duplicação da mensagem, contadores e estado final consistentes, uma intenção de avaliação quando elegível.
2. Recebimento iniciado após a finalização confirmada: comportamento existente de abertura/reuso preservado; snapshot da pesquisa anterior não muda.
3. Resposta da automação concorrente com finalização, partindo de estado permitido pela regra atual. No conflito, respeitar a precedência/validação vigente; não criar novo direito da IA de responder depois do fechamento. Provar ausência de 40P01 e não duplicação de mensagem/outbox.
4. Registro de mensagem já enviada pela automação concorrente com finalização: manter semântica de registro e idempotência por wamid; não confundir esse endpoint com ordem de enviar outra mensagem.
5. Transferência concorrente com mensagem recebida: sem ciclo de locks; responsável, timeline e contadores coerentes. Transferência não dispara avaliação.
6. Finalização em lote concorrente com recebimento no mesmo lead: sem deadlock e nenhuma intenção de avaliação; preservar tratamento dos itens recusados e atomicidade já testada.
7. Disputa entre finalizações, disputa com transferência nas duas ordens e envio manual já cobertos pela E83 continuam verdes.
8. Reexecutar os negativos de privacidade/RLS, rollback de outbox, responsável diferente do gestor, lote de um item, sem responsável, reserva concorrente/expirada, resultado tardio, esgotamento, timeout e isolamento do HTTP.

Testes de fluxo devem validar efeito persistido e efeitos que NÃO ocorreram, não somente status HTTP ou ausência de exceção. Usar barreiras/Awaitility por condição. Manter `synapse.agendamento.habilitado=false` nos testes compartilhados; o job é exercitado explicitamente conforme E83.

Atualizar `docs/35-runbook-webhook-avaliacao.md` na explicação dos locks e `docs/relatorio-E83-webhook-avaliacao.md` (ou relatório complementar claramente identificado), distinguindo a cobertura anterior da nova. Não transformar uma evidência do diagnóstico reduzido em afirmação de teste real do CRM.

## Validação final

- Java 21 e Docker/Testcontainers ativos.
- Executar regressões direcionadas e o reator completo: `cd backend` e `./mvnw clean verify` (no PowerShell, `./mvnw.cmd clean verify`).
- Spotless e ArchUnit precisam executar, sem skips. Não apenas `test`, `test-compile` ou módulo isolado sem dependências.
- Reexecutar a regressão concorrente sob o estado corrigido sem retries ocultos; confirmar que o cenário antigo de disputa continua sendo forçado.
- `git diff --check` e conferência da lista completa de arquivos. Comparar o que já era da E83 com o que foi alterado nesta correção.
- Frontend não deve mudar. Se alguma necessidade surgir, justificar antes e executar as verificações pertinentes; não ampliar escopo por conveniência.
- Nenhuma chamada real ao n8n, nenhum envio WhatsApp e nenhum segredo em log, fixture ou relatório.

## Definição de pronto

- [ ] Deadlock original reproduzido em regressão do CRM antes da mudança.
- [ ] Regra de locks corrigida e justificada com FK, upsert e ordem dos caminhos envolvidos.
- [ ] Recebimento, automação, transferência e lote não provocam o ciclo reproduzido.
- [ ] Mensagens/contadores e responsável preservados; sem duplicação ou reabertura por cópia antiga.
- [ ] Intenção de avaliação permanece atômica, individual, idempotente e atribuída corretamente.
- [ ] HTTP permanece fora da transação, em executor/circuito próprios; nenhuma regressão de lease/retry.
- [ ] Suíte completa verde com contagens reais e testes negativos nomeados.
- [ ] Runbook/relatório corrigidos e diff limpo; frontend e demais worktrees preservados.
- [ ] Sem commit, push, merge, deploy ou disparo real. CI informado como não verificado quando não houver run.

## Relatório final — sete itens de AGENTS.md

Informar estado, branch/base/HEAD, arquivos e ausência de publicação; depois critérios com evidência, decisões técnicas, divergências, bugs, exclusões e decisões realmente pendentes.

Incluir obrigatoriamente:

- O teste que falhava antes, o erro observado, o modo de bloqueio adotado e por que não enfraquece as garantias da finalização.
- Quais pontos de entrada foram exercitados e quais ainda não foram. Não reportar apenas “concorrência coberta”.
- Números do clean verify e diferença entre diagnóstico SQL reduzido, IT do CRM e validação externa.
- Ação necessária no Dokploy: expectativa de NENHUMA variável adicional nesta correção. As 14 variáveis opcionais da E83 continuam documentadas; ativação permanece separada.
- A integração ainda depende de configurar URL/header/segredo seguro, combinar deduplicação persistente com Dylan e autorizar teste real. Não afirmar que n8n deduplica sem evidência. Não repetir a credencial compartilhada no chat; recomendar sua rotação antes da ativação.

Fora: refatoração do repasse legado, novos endpoints, alteração do workflow, política comercial nova, dados retroativos, remoção de testes, edição de migrations aplicadas, upgrade Java e publicação sem autorização.
