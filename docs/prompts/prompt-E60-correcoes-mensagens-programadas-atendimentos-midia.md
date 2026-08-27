# Prompt E60 — Corrigir mensagens programadas, aviso persistente, ações do atendimento e áudio

Você é o agente implementador do Synapse CRM / Base PAI. Trabalhe no repositório
`C:\Users\marcondes\Desktop\projeto_matriz`.

Leia por inteiro, nesta ordem, antes de alterar código:

1. `AGENTS.md`;
2. `docs/13-estado-do-projeto.md`;
3. `docs/prompts/COMO-ESCREVER-PROMPTS.md`;
4. `docs/03-modelo-dados-postgres.md` e `docs/04-adrs-e-api.md` quando tocar no
   agendamento ou no contrato de mensagens.

O relatório do agente anterior não é evidência. Confira o estado atual no código,
nos testes e, quando possível, no fluxo ponta a ponta. Não faça `push` sem
autorização explícita do Marcondes. Não altere migration já aplicada.

## Contexto e evidências de reprodução

Há quatro sintomas observados na homologação:

1. Ao abrir Mensagens programadas, pela navegação ou pelo relógio do composer,
   a superfície da conversa fica bugada ou desaparece. Ao voltar, o chat não
   pode ficar engolido por overlay, altura incorreta ou estado stale.
2. Mensagens programadas continuam `AGENDADA` e não são enviadas no instante
   escolhido.
3. O aviso `Atendimento devolvido para a Automação` permanece no canto superior
   direito e não desaparece.
4. Uma gravação de áudio chega ao backend como `video/quicktime` e é rejeitada
   com `tipo de arquivo nao permitido: video/quicktime`.

Não trate as imagens de referência como especificação de dados. Elas documentam
os sintomas e a posição visual desejada.

## Achados já conferidos no repositório

Não os aceite cegamente: use-os como pontos de partida e confirme durante a
implementação.

- `backend/crm-core/.../MensagemProgramadaController.java` expõe apenas o CRUD
  de `/api/v1/mensagens-programadas`.
- `backend/crm-core/.../MensagemProgramadaRepositorio.java` possui listar,
  criar, editar e cancelar, mas não possui operação de reservar/processar uma
  mensagem vencida.
- `backend/crm-core/.../MensagemProgramadaRepositorioJdbc.java` insere sempre
  `AGENDADA`, e só altera para `CANCELADA`; não há publisher ou consumidor que
  transforme uma programada vencida em mensagem real.
- `StatusMensagemProgramada` contém apenas `AGENDADA`, `ENVIADA` e `CANCELADA`.
- `backend/crm-app/src/test/java/com/synapse/crm/app/core/MensagensProgramadasIT.java`
  testa CRUD e privacidade, mas não testa o envio de uma programada vencida.
- `frontend/src/components/atendimentos/pagina-atendimentos-cliente.tsx`
  mantém a notificação em `notificacao` sem descarte automático e sem botão
  explícito de fechar.
- `frontend/src/components/atendimentos/cabecalho-conversa.tsx` renderiza
  `MoreHorizontal` depois dos botões Transferir e Finalizar.
- `TiposDeMidiaPermitidos` permite `audio/mp4`, mas rejeita literalmente
  `video/quicktime`; o teste atual prova um M4A sintético e um vídeo MP4
  sintético, não necessariamente os bytes produzidos pelo `MediaRecorder` do
  navegador que gerou o incidente.
- `frontend/src/components/atendimentos/use-gravador-audio.ts` escolhe os tipos
  que o navegador suporta e nomeia o arquivo como `.m4a`; confirme o MIME real
  de `Blob`, `File`, multipart e Tika antes de escolher a correção.

Se algum achado estiver diferente na árvore atual, atualize a estratégia e
registre a divergência no relatório final.

## Objetivo

Corrigir os quatro sintomas sem criar um segundo caminho de envio, sem liberar
vídeo disfarçado de áudio e sem quebrar RN-CRM-01, RN-CRM-04, RN-CRM-06,
outbox, RLS, janela de 24 horas ou a disponibilidade da aba Atendimentos.

## Decisões aprovadas pelo Marcondes — não parar novamente nestes pontos

O Marcondes autorizou seguir as recomendações abaixo. Implemente-as e registre
qualquer divergência concreta no relatório; não devolva a tarefa apenas pedindo
uma nova escolha.

### Agendamento

- Não criar um segundo sistema de retry para `mensagem_programada`. O retry,
  backoff e esgotamento continuam pertencendo à outbox existente.
- Não adicionar `PROCESSANDO`, `FALHA` ou lease persistente ao enum nesta etapa.
  A reserva será atômica por atualização condicional:
  `status = 'AGENDADA'`, `data_envio <= agora` e transição para `ENVIADA` na
  mesma transação que materializa a `Mensagem` e grava o evento da outbox.
  `ENVIADA` significa que a programada entrou no pipeline durável; a entrega
  ao provedor continua sendo representada pelo status da mensagem/outbox.
- Se duas instâncias encontrarem o mesmo ID, somente a transação que conseguir
  o `UPDATE ... WHERE status = 'AGENDADA'` poderá criar a mensagem e a outbox.
  A outra deve obter zero linhas e seguir sem erro. Não fazer chamada externa
  enquanto essa transação estiver aberta.
- O job deve buscar IDs vencidos em lote limitado, chamar um caso de uso
  transacional separado por item e ser um bean `@Scheduled` separado desse caso
  de uso. Use configuração já existente de scheduler; se precisar de novo
  parâmetro operacional, coloque-o em `application.yml` com valor padrão
  seguro e documente-o, sem variável obrigatória nova no Dokploy.
- O payload da outbox deve carregar a origem `mensagem_programada_id` para
  auditoria e diagnóstico. Não crie coluna na tabela particionada `mensagem`
  apenas para resolver idempotência.

### Áudio

- Não aceitar `video/quicktime` por allowlist genérica e não converter por
  extensão ou pelo MIME declarado pelo navegador.
- Implementar a detecção segura do contêiner ISO-BMFF: quando o Tika retornar
  `video/quicktime`/`video/mp4`, inspecionar as trilhas e aceitar somente um
  contêiner que tenha trilha de áudio e nenhuma trilha de vídeo. O resultado
  normalizado para a Meta deve ser `audio/mp4`; qualquer trilha de vídeo deve
  continuar sendo rejeitada antes do storage.
- Manter limites, storage, legenda, outbox e webhook. A inspeção deve ser
  limitada aos bytes do upload e não pode executar binário externo.
- Adicionar fixture de áudio-only ISO-BMFF semelhante aos bytes do incidente e
  fixture de vídeo real, além do M4A/AAC já existente. O teste do incidente só
  estará resolvido quando o áudio-only for aceito ponta a ponta e o vídeo com
  os mesmos marcadores de contêiner for recusado.

### Aviso temporário

- Manter botão acessível de dispensa.
- O tempo automático não pode ficar como `8_000` no componente. Expor um
  parâmetro numérico de interface pela configuração existente da aplicação,
  com default operacional documentado e limite mínimo/máximo validado; o
  frontend deve consumir esse valor e usar fallback seguro apenas quando a
  configuração não estiver disponível.
- O parâmetro controla somente apresentação. Não deve alterar evento, estado de
  atendimento ou persistência.

---

## Bloco 1 — Fazer mensagens programadas serem enviadas

### Diagnóstico obrigatório

Antes de codar, siga o fluxo existente de texto manual:

- `EnviarMensagemUseCase`;
- `ConteudoDeEnvio`;
- outbox, publisher e canal fake;
- alteração de responsável e eventos de timeline;
- testes de integração de `CanalWhatsAppIT` e de outbox.

O agendador não pode chamar a Meta, outro canal ou um cliente HTTP de forma
síncrona. Ele deve reservar a linha vencida e encaminhar o envio pelo fluxo
existente, com outbox/transação e circuit breaker já adotados pelo projeto.

### Implementação esperada

- Criar uma porta de aplicação para buscar/reservar mensagens `AGENDADA` cujo
  `data_envio <= agora` e marcar a reserva de maneira atômica. A solução deve
  ser segura se houver mais de uma instância executando o job, sem duplicar o
  envio por corrida entre workers.
- Manter o bean com `@Scheduled` separado do bean transacional que reserva e
  processa a mensagem. Não introduzir auto-invocação de método anotado; o ponto
  de entrada agendado precisa ser testável como o runtime o chama.
- Não usar `Thread.sleep`, polling em loop infinito ou espera ocupada. O job
  deve ter lote limitado e configuração operacional coerente com os padrões
  existentes de scheduler/outbox.
- Ao reservar, revalidar que o lead e o canal ainda estão aptos ao envio e
  reutilizar as regras de autorização/estado já existentes. Não permitir que o
  job bypass a regra de transferência do lead: o ator de serviço deve ser
  explícito, auditável e compatível com a regra de negócio.
- A política de falha é a da decisão aprovada acima: a programada transita para
  `ENVIADA` apenas na transação que gravou a mensagem real e a outbox; falhas
  posteriores ficam no status da mensagem/outbox, com retry e alerta já
  existentes. Não marcar `ENVIADA` se a outbox não foi gravada.
- Toda publicação em fila deve continuar passando por Transactional Outbox.

### Testes obrigatórios

Adicionar testes de domínio/aplicação e integração suficientes para provar:

- uma programada vencida é reservada uma única vez e chega ao `CanalFake` pelo
  publisher/outbox;
- chamar o ponto de entrada real do job, como o runtime o chama, produz o
  processamento — não chamar apenas um método interno;
- duas execuções concorrentes não produzem duas mensagens para o mesmo ID;
- mensagem futura não é enviada antes da hora;
- mensagem cancelada não é enviada;
- o envio falha de forma observável e não mente que foi `ENVIADA`;
- a privacidade de mensagem programada de outro atendente continua protegida;
- o teste usa `Awaitility` para efeitos assíncronos e não `Thread.sleep`.

Se o desenho exigir alterar o contrato de status ou schema, pare e reporte a
decisão necessária antes de prosseguir.

---

## Bloco 2 — Corrigir a tela/modal de Mensagens programadas sem engolir o chat

Investigue os dois pontos de entrada, pois o sintoma pode estar sendo descrito
com o mesmo nome:

- navegação para `/mensagens-programadas` pelo `Sidebar`;
- botão do relógio no `Composer`, que abre
  `FormularioMensagemProgramada` dentro do atendimento.

Critérios:

- a rota própria deve ocupar apenas a superfície de página prevista pelo shell,
  sem overflow que esconda a sidebar, sem `h-full` inválido, sem branco
  permanente e sem exceção de hidratação;
- ao abrir o formulário pelo composer, o chat continua montado por baixo do
  modal e volta ao estado anterior ao fechar ou salvar;
- nenhum overlay fica no DOM interceptando cliques depois do fechamento;
- abrir o formulário de novo não reaproveita lead, data, conteúdo ou erro de
  uma abertura anterior de forma indevida;
- consulta, mutação, erro, cancelamento e invalidação de cache mantêm o padrão
  existente; não usar dados mockados;
- datas continuam sendo convertidas explicitamente entre horário local do
  navegador e `Instant` UTC, com teste para horário de verão/fuso quando
  aplicável.

Adicionar testes de componente para os dois caminhos. Se a expectativa de
produto for diferente — por exemplo, a rota deveria abrir como painel sobre o
chat em vez de ser uma página própria — pare e devolva essa decisão ao
Marcondes; não deduza pela imagem.

---

## Bloco 3 — Fazer o aviso de transferência/devolução desaparecer

Corrigir o aviso renderizado por
`frontend/src/components/atendimentos/pagina-atendimentos-cliente.tsx`.

- Ele deve aparecer uma vez por evento real.
- Deve desaparecer ao ser dispensado e não pode permanecer indefinidamente na
  tela. Preferir o mecanismo de notificação já existente; se ele não existir,
  criar um descarte explícito (botão acessível) e comportamento temporário
  consistente com o catálogo/configuração da instância, sem texto literal novo
  nem timeout de negócio hardcoded.
- Ao clicar em “Abrir transferência”, deve abrir o lead e limpar o aviso.
- Ao navegar para fora de Atendimentos, desmontar o estado naturalmente; ao
  voltar, um evento antigo não pode reaparecer por cache local.
- Eventos subsequentes devem substituir ou agrupar o aviso conforme o padrão
  atual, sem acumular overlays.

Testar com fake do tempo ou mecanismo equivalente:

- aviso aparece para `ATENDIMENTO_DEVOLVIDO_PARA_IA`;
- aviso aparece para transferência recebida;
- aviso desaparece no caminho temporário/dispensa;
- “Abrir transferência” limpa e seleciona o lead;
- remount não ressuscita evento antigo.

Não resolver simplesmente escondendo o componente ou removendo a assinatura
WebSocket.

---

## Bloco 4 — Mover os três pontos para a esquerda da barra de ações

Em `CabecalhoConversa`, mover o gatilho de `MoreHorizontal` e a opção
`Finalizar todos os atendimentos visíveis` para o lado esquerdo do grupo de
ações do cabeçalho, antes das ações operacionais de entrar/transferir/finalizar,
mantendo:

- o mesmo `aria-label`, catálogo de textos, dropdown e confirmação;
- a quantidade real de atendimentos visíveis;
- a autorização existente e o estado disabled quando não há itens finalizáveis;
- o botão individual Finalizar e as demais ações funcionais;
- layout responsivo sem empurrar o nome/telefone do lead para fora.

Atualizar o teste de `CabecalhoConversa` para verificar a ordem dos controles no
DOM e preservar o teste que confirma a chamada de finalizar todos. Se a frase
“para a esquerda” tiver outro alvo exato dentro da barra, pare antes de escolher
uma posição diferente.

---

## Bloco 5 — Corrigir áudio sem abrir brecha para vídeo

Reproduza o caso com bytes reais ou fixture que represente o resultado do
`MediaRecorder` do navegador afetado. Inspecione o MIME em cada fronteira:

1. `MediaRecorder.mimeType`;
2. `Blob.type`;
3. `File.type` e extensão;
4. multipart recebido pelo controller;
5. MIME detectado pelo Apache Tika;
6. metadados persistidos e payload do adaptador Meta.

A correção deve:

- aceitar a gravação de áudio real suportada pela aplicação e convertê-la para
  uma representação aceita pelo canal, se necessário, em um adaptador
  explícito e seguro;
- continuar rejeitando vídeo MP4/QuickTime real, inclusive se o nome for
  `gravacao.m4a` ou se o `Content-Type` declarado for `audio/*`;
- nunca adicionar `video/quicktime` genericamente à allowlist;
- preservar a detecção por conteúdo, limite de tamanho, storage, outbox,
  legenda e metadados;
- manter a compatibilidade com áudio selecionado por arquivo e áudio recebido
  pelo webhook;
- se conversão/transcodificação for necessária, não fazê-la de modo síncrono
  no caminho crítico de recebimento de mensagem e não introduzir dependência
  operacional sem documentar imagem, binário, limite e fallback.

Testes obrigatórios:

- áudio real do caso do incidente é aceito ponta a ponta e chega ao canal como
  `TipoMensagem.AUDIO`;
- M4A/AAC já aceito continua aceito;
- áudio com MIME declarado forjado não passa se os bytes forem vídeo;
- MP4 vídeo e QuickTime vídeo continuam rejeitados antes do storage;
- extensão mentirosa continua rejeitada;
- frontend cobre a seleção/finalização da gravação e mostra o erro sem deixar
  preview/estado de envio preso;
- o adaptador do canal recebe MIME compatível com áudio.

Se a implementação atual do detector não conseguir diferenciar áudio e vídeo
com segurança, implemente um parser ISO-BMFF limitado e coberto por fixtures;
não faça uma allowlist ampla para calar a mensagem de erro e não adicione
transcodificação externa nesta etapa.

---

## Definição de pronto

- [ ] Uma programada vencida chega uma única vez ao canal fake através de
      outbox; futura e cancelada não chegam.
- [ ] O fluxo de falha do agendamento usa retry/backoff da outbox, e a transição
      para `ENVIADA` só ocorre junto com a mensagem real e seu evento durável.
- [ ] O job real foi testado como o runtime o chama e não bloqueia o caminho
      crítico de atendimento.
- [ ] Abrir Mensagens programadas pela sidebar e pelo composer não engole o
      chat, não deixa overlay e preserva estado/cache corretos.
- [ ] O aviso de devolução/transferência pode desaparecer e não persiste após
      dispensa, expiração ou remount.
- [ ] Os três pontos estão à esquerda do grupo de ações, e Finalizar todos
      continua funcional e autorizado.
- [ ] Áudio-only ISO-BMFF real é aceito sem aceitar vídeo `video/quicktime`
      genericamente; vídeo disfarçado continua rejeitado antes do storage.
- [ ] Testes negativos cobrem concorrência, cancelamento, privacidade, vídeo
      disfarçado e MIME declarado falso.
- [ ] Não há strings de UI novas fora do catálogo, cores literais novas, dados
      mockados ou chamada externa síncrona no caminho crítico.
- [ ] `cd backend && ./mvnw clean verify` foi executado com Java 21; se Docker
      ou Testcontainers impedir a execução, registrar os testes pelo nome.
- [ ] A suíte frontend relevante foi executada e o resultado foi registrado.
- [ ] Nenhum commit ou push sem autorização explícita do Marcondes.

## Relatório obrigatório

Entregue o relatório no formato do `AGENTS.md`, nesta ordem:

1. commit e estado: SHA, branch, status, diff e push;
2. definição de pronto com evidência concreta por checkbox;
3. decisões tomadas, especialmente retry/status do agendamento, posição exata
   do menu, política do aviso e estratégia do áudio;
4. divergências entre docs e código;
5. bugs encontrados, inclusive pré-existentes;
6. o que ficou de fora e por quê;
7. decisões necessárias do Marcondes.

Não escreva “CI verde” sem número de run. Não diga que a imagem foi publicada
com base apenas em build local. Não silencie falha de integração.

Commits locais, se autorizados, devem ser Conventional Commits e separados por
bloco. Push somente após autorização explícita.
