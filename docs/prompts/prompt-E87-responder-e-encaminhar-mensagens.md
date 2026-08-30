# E87 — Prioridade: responder e encaminhar mensagens de verdade

## Objetivo de produto

Implementar as ações **Responder** e **Encaminhar** no chat de Atendimentos, com o comportamento esperado de WhatsApp:

- Ao responder, o composer mostra uma citação da mensagem escolhida (autor + trecho/tipo de mídia), permite cancelar e a mensagem enviada exibe essa referência.
- Para WhatsApp, quando o provedor suportar, a mensagem de saída deve levar o contexto da mensagem original para aparecer como resposta também no aplicativo do cliente.
- Ao encaminhar, o usuário escolhe uma conversa de destino que ele já pode acessar, confirma a ação e o CRM envia uma nova mensagem real ao destino, preservando uma referência de que foi encaminhada.

Não implemente um marcador `@` de usuário nesta etapa. A “menção ao lead” solicitada é a citação de resposta da mensagem/autor, como na imagem. Se o produto quiser menções `@` em mensagens depois, isso será uma tarefa distinta.

## Base e branch

1. Atualize referências remotas sem alterar `main`.
2. Crie worktree a partir de `origin/main` e branch `codex/e87-responder-encaminhar`.
3. Confirme que a E84 (ações/reactions) está integrada na base antes de começar. Se não estiver, pare e informe; não copie nem cherry-pick arquivos por conta própria.
4. Leia integralmente `AGENTS.md`, a implementação atual de mensagens, o adaptador Meta e os contratos de chat antes de editar. Use skills relevantes de arquitetura, API, clean code e migrations.

## Escopo obrigatório

### 1. Responder no Atendimento/WhatsApp

- A ação “Responder” do menu de mensagem deve ser real. Ao acioná-la, o composer mostra contexto da mensagem original: remetente seguro, prévia curta e tipo de conteúdo; deve ter botão de cancelar.
- Enviar com contexto cria uma nova mensagem normal, com vínculo persistente à mensagem original. A bolha exibida no CRM mostra a citação acima do conteúdo, com fallback seguro se a original não estiver disponível.
- Para mensagens de WhatsApp, use o identificador externo correto da mensagem original e o campo/contexto oficialmente suportado pelo adaptador Meta para resposta. Não simule resposta apenas no frontend.
- Se a mensagem não puder ser respondida no canal (por exemplo, não há identificador externo compatível, mensagem não pertence ao atendimento, canal fora da janela ou política do provedor), devolva Problem Details claro, preserve o rascunho e não grave vínculo falso.
- Responder a texto, imagem, áudio e documento deve citar corretamente sem copiar conteúdo sensível em logs. O envio continua respeitando as regras atuais de mídia, janela de 24h, template e entrega.

### 2. Encaminhar no Atendimento/WhatsApp

- A ação “Encaminhar” abre diálogo acessível com busca e **uma única conversa de destino por envio**. O destino é escolhido apenas entre atendimentos/conversas que o usuário autenticado já pode visualizar pela regra de negócio existente.
- Mostre nome do lead, foto/avatar, última atividade/canal somente quando já autorizados; nunca liste leads de outro atendente para um atendente comum.
- Exija confirmação explícita indicando origem e destino. Feche somente após sucesso; em erro, mantenha diálogo e explique pelo catálogo.
- O encaminhamento cria e envia uma mensagem nova de verdade no destino, preservando vínculo de origem/auditoria sem mover, editar ou apagar a mensagem original.
- Texto, imagem, áudio e documento devem seguir a mesma rota validada de envio de mídia atual. Não reimplemente upload, não exponha URL privada e não invente suporte: se um tipo não puder ser reenviado pelo provedor, bloqueie com erro claro e teste-o.
- Encaminhar obedece à janela/template do destino e às regras atuais de envio manual. Em particular, preserve RN-CRM-06: uma mensagem manual enviada ao destino transfere o lead para quem enviou somente quando a regra existente já determina isso; nunca burle recorte de visibilidade.
- Não suporte múltiplos destinos, encaminhamento em lote, encaminhamento para chat interno ou criação de novo contato nesta etapa.

### 3. Persistência e contratos

- Modele referências de resposta e encaminhamento de modo genérico no domínio. Crie migration Flyway nova; jamais altere migration aplicada.
- Armazene vínculo com a mensagem de origem e os dados mínimos de exibição/auditoria necessários, com FKs/índices e comportamento explícito caso a origem não possa mais ser recuperada.
- Não grave payload do provedor, telefone completo, token ou cópia redundante de mídia em campos de referência.
- Estenda os DTOs REST de histórico/envio de forma compatível: a UI recebe somente resumo autorizado da citação (id, autor seguro, tipo, prévia/metadado sanitizado), nunca mensagem/lead fora de seu escopo.
- O endpoint de envio deve aceitar referência de resposta apenas se origem e atendimento atual forem consistentes. O endpoint/uso de encaminhar valida separadamente origem e destino com a Specification/autorizações existentes.
- Não faça chamadas externas síncronas sem o mecanismo resiliente já usado pelo envio. Preservar a prioridade absoluta: Atendimentos não pode ficar indisponível por integração lenta.

### 4. Interface e acessibilidade

- Acrescente “Responder” e “Encaminhar” ao menu de ações somente onde tiverem backend funcional. Não deixe controles decorativos.
- Reaproveite os componentes de bolha, composer, diálogo e seletor de contatos; não crie uma segunda implementação divergente.
- A barra de resposta no composer precisa ser visível, responsiva, focável e cancelável por teclado. Enter/Shift+Enter e falhas de envio mantêm os comportamentos atuais.
- Bolhas de resposta/encaminhamento devem ser compactas, distinguíveis e usar tokens semânticos/catálogo de textos, sem cores, rótulos ou dados mockados hardcoded.
- No chat interno, não exponha as ações como funcionais nesta etapa. Se for tecnicamente simples compartilhar apenas a visualização de citação sem envio, não faça: mantenha o escopo no Atendimento/WhatsApp para evitar comportamento parcial/confuso.

## Segurança e casos negativos obrigatórios

- Atendente A não responde, cita nem encaminha mensagem/lead de B; gestor/subgestor seguem regras atuais e não recebem ampliação implícita.
- Não aceitar `mensagemOrigemId` arbitrário, nem origem de outro atendimento, nem destino fora da visibilidade.
- Não permitir alterar o conteúdo/origem do encaminhamento pelo frontend.
- Eventos WebSocket, histórico e auditoria não podem vazar a citação para quem perdeu acesso ao atendimento.
- Falha no canal, template obrigatório ou mídia inválida não cria mensagem “enviada” nem deixa referência parcialmente persistida.

## Fora de escopo

- Menções `@`, respostas/encaminhamentos de chat interno, múltiplos destinos, favoritos, fixar, apagar, denunciar, IA, edição ou exclusão de mensagem.
- Alterar regras comerciais de transferir lead, janela de 24h, template ou RLS.
- Chamadas reais ao Meta/WhatsApp durante testes.

## Testes e validação

### Backend com Java 21 e Testcontainers

- Resposta válida persiste vínculo e o adaptador Meta recebe o contexto/identificador externo esperado.
- Origem sem identificador elegível, origem de outro atendimento e origem não visível falham com status correto e sem gravação parcial.
- Encaminhar texto e pelo menos uma mídia suportada cria nova mensagem no destino; tipo incompatível é rejeitado.
- Destino fora da visibilidade, usuário não autorizado, concorrência e retry não causam duplicidade/vazamento.
- Teste negativo de RLS/Specification: atendente A não alcança origem nem destino de B.
- Fluxo ponta a ponta de envio confirma que indisponibilidade/timeout do provedor não interrompe a aplicação.

### Frontend

- Menu mostra as duas ações funcionais no Atendimento e não as mostra como ativas no chat interno.
- Composer mostra/cancela contexto; erro preserva rascunho/contexto; envio bem-sucedido limpa ambos.
- Bolha renderiza citação de texto, mídia e fallback sem HTML inseguro.
- Seletor de destino busca, não mostra o próprio lead/origens inválidas, exige confirmação e preserva erro.
- Teste acessível de foco, Escape e navegação por teclado.

### Navegador real

- Em ambiente local autenticado com dados de demonstração: responda uma mensagem recebida, confira a citação no composer e na bolha depois do envio; encaminhe para um destino permitido e confirme que origem e destino permanecem corretos.
- Confirme desktop e 390 px, ausência de overflow e que nenhuma conversa inacessível aparece no seletor.
- Capture screenshots sem dados reais de cliente.

### Comandos finais

- `cd backend && ./mvnw clean verify`
- `cd frontend && npm ci`, `npm run lint`, `npm run typecheck`, `npm test -- --run`, `npm run build`
- `git diff --check`

## Commit, push e CI

- Use commits Conventional Commits e envie a branch para `origin/codex/e87-responder-encaminhar`.
- Abra PR contra `main` após validações locais; aguarde CI remota e informe URL/número da run e resultado de cada job.
- Não faça merge, deploy ou chamada real ao Meta sem autorização explícita posterior.

## Relatório final

Siga as sete seções de `AGENTS.md`, com SHA, branch, push, migration/contratos, provas de autorização/RLS, cobertura de mídia, resultado da validação visual, CI e qualquer limitação do provedor Meta identificada.
