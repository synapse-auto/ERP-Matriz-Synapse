# Prompt E64 — Isolar jobs agendados da suíte de integração

> Leia `AGENTS.md`. Entrega em 25/08.
> Esta etapa corrige uma falha de validação encontrada na revisão da E63b.
> Não faça commit ou push sem autorização explícita do Marcondes.

## Contexto verificado pelo arquiteto

O relatório da E63b dizia que `cd backend && ./mvnw clean verify` havia passado
com 362 testes. A execução real no repositório terminou em `BUILD FAILURE`:

```text
Tests run: 362, Failures: 1, Errors: 0
MensagensProgramadasIT.falhaMantemAgendada:104
expected: "AGENDADA" but was: "ENVIADA"
```

A mesma classe, executada isoladamente, passou em 7/7 testes. A causa provável
já aparece na infraestrutura de testes: [PostgresIT] usa Postgres/Redis
compartilhados e há contextos Spring com `@Scheduled` que continuam executando
contra o mesmo banco. O teste fecha a janela do `CanalFake`, mas outro scheduler
pode reservar a linha com outra instância do canal antes da asserção.

Confirme esse diagnóstico no código e nos relatórios antes de alterar qualquer
arquivo. Não trate a falha como flakiness aceitável.

## Objetivo

Fazer a suíte completa de integração ficar isolada e determinística sem alterar
o comportamento dos jobs em produção:

- nenhum job agendado de um contexto de teste pode modificar dados de outro
  teste ou de outra classe;
- os testes que verificam jobs continuam exercitando o ponto de entrada real do
  job, sem depender da passagem de tempo;
- `MensagensProgramadasIT` continua provando vencida, futura, cancelada,
  concorrência e falha com rollback;
- a execução completa de `clean verify` passa repetidamente no mesmo checkout.

## Bloco 1 — mapear a interferência antes da solução

Inspecione:

- todos os beans de produção com `@Scheduled`;
- `AgendamentoConfig` e a forma como `@EnableScheduling` registra o scheduler;
- `PostgresIT`, `@DynamicPropertySource`, perfis ativos e cache de
  `ApplicationContext`;
- todos os testes que chamam jobs diretamente e todos que dependem de timer;
- especialmente `AgendadorDeMensagensProgramadas`, `PublicadorDaOutbox`,
  processamento de webhook, manutenção de partições e monitoramento de saúde.

Registre no relatório qual contexto/job consegue tocar o banco compartilhado e
qual configuração permite isso. Se o diagnóstico não se confirmar, pare e
relate a causa observada antes de escolher outra correção.

## Bloco 2 — corrigir o isolamento

Implemente a menor correção estrutural que impeça jobs de fundo de rodarem
acidentalmente em testes, preservando os jobs em produção. A solução deve:

- ser controlada por configuração de teste explícita, sem `if` por nome de
  classe ou por tenant;
- não desligar o scheduler na configuração de produção;
- não usar atraso, `Thread.sleep`, ordem presumida ou repetição do teste como
  mecanismo de isolamento;
- não esconder falhas de transação, reserva atômica ou outbox;
- manter a possibilidade de chamar o método anotado com `@Scheduled` diretamente
  pelo teste quando o objetivo for testar o ponto de entrada;
- não criar um segundo caminho de execução que o runtime de produção nunca usa.

Se a solução exigir tornar `AgendamentoConfig` condicional, propriedade de
composição ou configuração específica do perfil de testes, prove que a
configuração usada pelos testes é realmente a que foi carregada. Não basta
testar uma propriedade em um contexto diferente do contexto em execução.

Se algum teste realmente precisar de um scheduler automático, isole o contexto
ou o banco desse teste e demonstre por que ele não pode chamar o ponto de entrada
explicitamente. Não deixe timers habilitados globalmente para a suíte.

## Bloco 3 — regressões obrigatórias

Adicione ou ajuste testes que comprovem, de forma negativa quando aplicável:

1. o perfil/configuração de teste não registra ou não inicia jobs automáticos
   acidentais;
2. chamar manualmente o ponto de entrada de `AgendadorDeMensagensProgramadas`
   ainda processa a mensagem vencida;
3. falha fora da janela mantém a mensagem em `AGENDADA` e não cria outbox;
4. duas execuções concorrentes não duplicam o pipeline;
5. o publisher da outbox continua sendo exercitado explicitamente nos testes
   que precisam dele;
6. nenhum contexto encerrado continua alterando o Postgres compartilhado;
7. a execução completa não depende da ordem das classes.

Use Testcontainers real. Para efeitos assíncronos, espere condição com
Awaitility; nunca use `Thread.sleep` nem asserção imediata para mascarar corrida.

> O teste deve falhar se um job de fundo voltar a processar uma linha criada por
> outro cenário. Uma proteção que só verifica que o scheduler existe, mas não
> prova que ele está isolado, não encerra esta etapa.

## Bloco 4 — validação obrigatória

Execute e registre os comandos completos:

- `cd backend && ./mvnw clean verify` com Java 21 e Testcontainers;
- pelo menos duas execuções consecutivas do ciclo completo, sem limpar
  seletivamente o teste que falhou;
- a classe `MensagensProgramadasIT` isolada, informando o resultado;
- suíte frontend completa, typecheck, lint e build, para garantir que a E63b
  não regrediu;
- `git diff --check`;
- branch, `HEAD`, `origin/main`, `git status` e diff antes/depois.

O relatório deve informar os números exatos de testes, falhas, erros e skips de
cada execução. Execução local não é CI verde: sem push, CI remoto é
`não verificado`.

## Restrições

- Não alterar migration aplicada.
- Não alterar o contrato da inbox unificada, paginação, RLS ou participação do
  chat interno.
- Não remover a reserva atômica, o rollback de `AGENDADA` ou a outbox.
- Não desabilitar permanentemente o agendamento em produção.
- Não usar `Thread.sleep`, retries cegos ou aumento de timeout como correção.
- Não introduzir variável obrigatória nova no Dokploy. Se uma configuração nova
  for necessária, use default seguro, atualize `.env.example`, `README.md` e
  registre a ação necessária no Dokploy.
- Java 21 é fixo.
- Não fazer commit ou push sem autorização explícita.

## Definição de pronto

- [ ] A interferência entre jobs/contextos foi confirmada no código e explicada.
- [ ] Jobs automáticos de teste não alteram dados de outros cenários.
- [ ] Produção continua com os jobs agendados habilitados.
- [ ] O ponto de entrada manual dos jobs continua coberto.
- [ ] `MensagensProgramadasIT` passa isolada e dentro da suíte completa.
- [ ] Duas execuções completas consecutivas de `clean verify` passam.
- [ ] Frontend da E63b continua passando: testes, typecheck, lint e build.
- [ ] `git diff --check` passa.
- [ ] Java 21 e Testcontainers foram usados.
- [ ] Nenhum commit ou push sem autorização explícita.
- [ ] CI remoto fica `não verificado` enquanto não houver push e número da run.

## Relatório obrigatório

Siga os sete itens de `AGENTS.md`:

1. branch, SHA, quantidade de arquivos e confirmação de commit/push;
2. cada checkbox acima com evidência concreta;
3. decisões tomadas sozinho e por quê;
4. divergências entre documentação e realidade;
5. bugs encontrados, inclusive fora do escopo;
6. o que ficou de fora;
7. decisões necessárias do Marcondes.

Inclua também:

- causa confirmada da falha em `MensagensProgramadasIT.falhaMantemAgendada`;
- configuração exata que impede o scheduler acidental nos testes;
- resultado separado das duas execuções completas e da execução isolada;
- qualquer ação necessária no Dokploy antes do próximo deploy;
- confirmação de que a E63b não perdeu a validação visual pendente;
- CI remoto com número da run somente se houver push autorizado.

## Fora desta etapa

- novas funcionalidades da inbox;
- criação de contato WhatsApp;
- redesign visual;
- alteração de regra comercial ou de janela da Meta;
- deploy ou alteração de produção.
