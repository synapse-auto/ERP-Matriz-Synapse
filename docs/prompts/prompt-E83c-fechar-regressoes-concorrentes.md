# Prompt E83c — Fechar as validações concorrentes pendentes da E83b

> Leia AGENTS.md, docs/13-estado-do-projeto.md, docs/prompts/COMO-ESCREVER-PROMPTS.md e os prompts E83/E83b aprovados.
> Continuação estrita de testes e documentação: NÃO reimplementar o webhook nem redesenhar a solução de locks já corrigida.
> Não autoriza commit, push, merge, rebase, deploy, troca de tag ou chamada real ao n8n/WhatsApp.
> Preservar todo o patch E83/E83b e os arquivos de outros agentes. Java 21 e Testcontainers obrigatórios.

## Resultado esperado

Fechar os critérios de teste que já constavam da E83b, sem nova rodada de decisões comerciais. O lock corrigido está no código e a nova regressão usa transações reais. A pendência é provar os demais caminhos concorrentes e os efeitos persistidos alegados, não inventar outra arquitetura.

A revisão reexecutou `clean verify` sem `spotless:apply`: BUILD SUCCESS em 28/08/2026 às 17:23:31 -03:00, 5min00s. Foram 569 testes (157 unitários + 412 ITs), sem falhas/erros/skips, incluindo `WebhookAvaliacaoIT` 28/28. Isso confirma a suíte existente, não substitui os cenários ausentes abaixo.

As regras permanecem: finalização individual elegível gera uma intenção; lote, inclusive de um item, não gera; sem responsável não gera; avaliação é do responsável no encerramento, mesmo se o gestor clicar. Nada de retroatividade, mudança de escala CSAT ou alteração do workflow.

## Bloco 0 — Estado e isolamento

Estado conferido em 28/08/2026:

- Worktree de implementação: `C:/Users/marcondes/Desktop/projeto_matriz-e83`.
- Branch: `codex/e83-webhook-avaliacao`.
- HEAD: `aed6f16d711ec39cc3cdfc62a93dc1653a435157`.
- 27 arquivos E83/E83b novos/modificados; nenhum commit dessa implementação.
- Na revisão, após fetch, `origin/main` passou a `d5ba368116c0df2cf8a9c8e20702b0cbaf7fd504`: a branch estava sete commits atrás, não mais quatro. Isso não autoriza integrá-los nesta etapa. A promoção futura precisa revalidar o resultado integrado, incluindo as mudanças recentes de novo contato e templates.
- Os prompts desta sequência estão em `C:/Users/marcondes/Desktop/projeto_matriz/docs/prompts/`; ler por caminho absoluto se não estiverem no worktree E83. Não trocar de branch para encontrá-los.

Antes de editar, confirmar worktrees, branch, HEAD, status, diff staged/unstaged e ausência de operação Git. Não confundir a origem da documentação com o destino da implementação. Não usar reset/clean/checkout/restore global para recuperar arquivos de um patch não commitado.

## O que foi aceito tecnicamente na revisão

Em `backend/crm-atendimento/src/main/java/com/synapse/crm/atendimento/infrastructure/persistencia/AtendimentoRepositorioJdbc.java`, `porIdParaAlteracao` agora usa:

```java
return primeiro(chat.query(SQL_POR_ID + " FOR NO KEY UPDATE", MAPEADOR, atendimentoId));
```

A guarda `WHERE atendimento.status <> 'FINALIZADO'` do upsert continua presente, com recusa quando nenhuma linha é alterada. A ordem lead → atendimento permanece compatível com o envio manual. Não retirar essas garantias.

O teste `recebimentoPausadoAntesDoContador_eFinalizacaoNaoEntramEmDeadlock` efetivamente pausa a porta antes do UPDATE do contador e depois do lock do lead, sobre INSERT/transações reais. Não é um teste que simplesmente mocka uma exceção.

## Lacunas objetivas encontradas

Arquivo: `backend/crm-app/src/test/java/com/synapse/crm/app/atendimento/WebhookAvaliacaoIT.java`, linhas 590–636 na revisão.

Trechos relevantes:

```java
var recebimento = executor.submit(() -> servico(() -> registrarRecebida(lead)));
// ... finalização HTTP, barreiras e espera pelas duas tarefas ...
assertThat(jdbc.queryForObject("SELECT count(*) FROM mensagem WHERE atendimento_id = ?", Integer.class, id))
        .isEqualTo(1);
assertThat(status(id)).isEqualTo("FINALIZADO");
assertThat(total(id)).isEqualTo(1);
```

O helper chama diretamente o caso de uso:

```java
new TransactionTemplate(manager).execute(tx -> registrar.executar(
        new RegistrarMensagemRecebidaUseCase.MensagemRecebida(
                lead, null, null, "mensagem recebida durante finalizacao")))
```

Portanto:

1. Esse teste é válido para a disputa transacional isolada, mas não executa a entrada e o processamento real do webhook.
2. As três asserções finais verificam quantidade de mensagens, status do atendimento e quantidade da intenção; não verificam `lead.num_mensagens`, `num_atendimentos`, instante de interação, responsável ou o snapshot da intenção nesse cenário concorrente.
3. A nova cobertura é recebimento × finalização individual. Não foram encontrados os cenários concorrentes pedidos de recebimento × transferência, recebimento × lote, resposta IA × finalização e registro de mensagem já enviada × finalização.
4. Os testes existentes de transferência × finalização e envio manual × finalização não substituem os pares acima. Os testes sequenciais de automação/lote também não.
5. O XML de `WebhookAvaliacaoIT` contém 28 casos totais, com uma ocorrência do novo teste. “28/28” é a suíte, não 28 repetições da regressão.
6. A documentação informa o vermelho do diagnóstico SQL reduzido anterior, mas não registra a execução vermelha da regressão do CRM reintroduzindo o lock antigo. Não afirmar que essa evidência existe sem produzi-la.

## Bloco 1 — Completar a regressão existente

- Preservar o teste transacional e fortalecer suas asserções: capturar estado inicial da fixture e verificar exatamente o incremento esperado de mensagens, número de atendimentos sem incremento indevido, última interação coerente com o Clock, responsável do atendimento/lead conforme a operação e payload da intenção com o responsável correto.
- Verificar conteúdo/identidade da mensagem e ausência de duplicata, não apenas que existe qualquer linha.
- Acrescentar evidência vermelho → verde da mesma regressão no CRM. Reintrodução temporária e cirúrgica do `FOR UPDATE` é somente diagnóstico local; executar o teste direcionado, registrar a falha real e restaurar `FOR NO KEY UPDATE` antes de qualquer validação final. Não restaurar o arquivo inteiro por Git, pois contém outras mudanças da E83 ainda não commitadas. Não usar mock que fabrica 40P01.
- Usar um diretório de diagnóstico isolado ou garantir exclusividade e restauração por patch específico. Conferir o diff após restaurar. Não deixar a mutação no resultado final.
- Uma execução comprovadamente vermelha seguida da verde vale mais que repetir 28 vezes sem forçar o interleaving. Não é requisito executar 28 repetições.
- Manter liberação das barreiras e término das tarefas em todos os caminhos de falha; não engolir exceção de deadlock/timeout.

## Bloco 2 — Completar os cenários já exigidos

Pode usar testes parametrizados e helpers pequenos. Cada cenário deve ter fixture própria, concorrência efetiva no mesmo lead/atendimento e asserções dos efeitos que ocorreram e dos que NÃO ocorreram.

| Cenário | Entrada a exercitar | Evidência mínima |
|---|---|---|
| Recebimento × finalização individual | Webhook local e processador real concorrentes com POST de finalizar | Mensagem persistida uma vez, contadores corretos, finalização e uma intenção quando elegível; sem deadlock ou erro oculto/reprocessamento silencioso |
| Recebimento após finalização confirmada | Caminho real de recebimento | Comportamento atual de abrir/reusar conversa preservado, sem alterar o snapshot do encerramento anterior |
| Recebimento × transferência | Recebimento e POST de transferência | Sem ciclo de locks; dono/timeline/contadores coerentes; nenhuma intenção de avaliação |
| Recebimento × lote | Recebimento e POST `/api/v1/atendimentos/finalizar-lote` | Finalização permitida e mensagem preservadas; ZERO intenções de avaliação e ZERO HTTP de pesquisa |
| Resposta da IA × finalização | POST `/internal/v1/atendimentos/{id}/responder` e finalização | Estado inicial permitido pela regra atual, autenticação e idempotência reais, nenhuma mensagem/outbox duplicada; sem 40P01 |
| Registro de mensagem já enviada × finalização | POST `/internal/v1/atendimentos/{id}/mensagens-enviadas` e finalização | Registro sem duplicação por wamid, contadores corretos, responsável/snapshot estáveis; não provocar segundo envio externo |

Referências para reuso, após ler o código:

- `CanalWhatsAppIT` já usa `processador.processarPendentes()` e fixtures de canal local. Manter assinatura/filtro conforme o provedor usado pelo teste; não alterar produção para facilitar fixture.
- `TransferenciaAutomacaoInternalController` encaminha responder para `ComandosAutomacaoUseCase`, não diretamente para o caso de uso de envio. Exercitar essa camada e seus locks/idempotência.
- `RegistroMensagemAutomacaoIT` já cobre o contrato de mensagens-enviadas. Estender a evidência concorrente sem enfraquecer o contrato.

Não confundir uma resposta da IA que começou validamente antes do encerramento com um novo comando iniciado depois. Preservar a política atual; se um cenário revelar necessidade de decisão comercial, expor o caso preciso, sem mudar silenciosamente quem pode responder ou a quem pertence o lead.

Para sincronização, usar latches/Awaitility por condição. Se consultar `pg_stat_activity`/`pg_locks`, filtrar pelas conexões/IDs da fixture; não deixar uma sessão de outro teste satisfazer a espera. O helper existente `esperarDisputaNoBanco` usa contagem global de qualquer lock: corrigir sua precisão se ele for usado nos novos cenários, sem aumentar timeouts.

Não repetir operação inteira para esconder falha, não ligar scheduler compartilhado globalmente, não usar Thread.sleep e não rodar contra banco ou webhook reais. Assegurar que o teste não passa porque a camada de processamento capturou e adiou o erro: conferir estado de processamento e logs/efeitos pertinentes.

## Bloco 3 — Relatório fiel e validação final

- Atualizar `docs/relatorio-E83-webhook-avaliacao.md`: nomes dos cenários, ponto de entrada de cada um, asserções dos contadores e evidência vermelho → verde. Separar diagnóstico SQL reduzido, teste transacional, teste de fluxo do CRM e operação externa.
- Identificar `WebhookAvaliacaoIT 28/28` anterior como total de casos. Informar os números novos reais, sem manter contagem esperada hardcoded.
- Manter o runbook tecnicamente correto. Não declarar deduplicação no n8n, envio WhatsApp ou CI sem evidência.
- Executar a seleção dirigida e depois `cd backend; ./mvnw.cmd clean verify` com Java 21 e Docker/Testcontainers. Spotless/ArchUnit devem rodar, sem skips ou relaxamento de qualidade.
- Executar `git diff --check`; conferir que a mutação de diagnóstico foi restaurada e que não houve mudança indevida em frontend, APIs, migrations, RLS, regras comerciais, payload ou configurações.

## Definição de pronto

- [ ] Regressão do CRM comprovadamente vermelha com o lock antigo e verde com o corrigido.
- [ ] Contadores, responsável e snapshot explicitamente verificados no cenário concorrente.
- [ ] Caminho de recebimento via entrada/processamento real coberto, além do teste transacional direto.
- [ ] Todos os pares da tabela executados, ou referência exata a testes equivalentes existentes que realmente forcem a concorrência exigida.
- [ ] Guardas de finalização, privacidade/RLS, individual/lote, rollback, retry, lease e isolamento do HTTP preservados.
- [ ] Clean verify completo e diff check aprovados; relatório distingue o que foi e não foi exercitado.
- [ ] Nenhuma publicação, integração de branch, chamada real ou alteração em outro worktree.

## Relatório e limites

Seguir os sete itens de AGENTS.md. Expectativa de alteração apenas em testes e documentação; se um novo defeito funcional for reproduzido, apontar a evidência e manter a correção estritamente ligada a ele, sem refazer o que já funciona.

Ação necessária no Dokploy nesta complementação: nenhuma variável nova. A ativação da E83 continua separada e depende da configuração opcional documentada, de segredo seguro e da confirmação de deduplicação persistente com Dylan. Não repetir credenciais em código/relatório.

Não pedir novamente autorização das três regras comerciais já aprovadas. Commit, push e promoção seguem pendentes de autorização específica após revisão; CI não verificado enquanto não houver run do conteúdo publicado.
