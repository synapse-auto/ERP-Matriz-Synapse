# Prompt E25 — Repasse para a Automação, bug do clique e fidelidade da tela de Atendimentos

> Leia `AGENTS.md`. Entrega em 25/08.
> Blocos em ordem de prioridade. Commite e faça push por bloco.
> Ao encerrar: `cd backend && ./mvnw clean verify` e o **número da run** do CI.

---

## Bloco 1 — Repasse do webhook da Meta para a Automação

**Bloqueia o trabalho do Dylan.** Os workflows dele já foram construídos em cima do formato que a Meta envia.

Ao receber um webhook válido da Meta, o CRM repassa o **payload cru, sem nenhuma alteração**, para a URL da Automação. Nada de reformatar, enriquecer ou traduzir: o que a Meta mandou é o que o n8n recebe.

Destino desta instância: `https://n8n.187.77.47.30.sslip.io/webhook/estrutural-vidros/eventos`

**Vem por variável de ambiente**, não hardcoded — cada filho tem a sua. Acrescente à `dokploy-stack.yml` e ao `.env.example` no padrão das demais, e documente no `README.md`.

Três regras que não podem ser violadas:

**Responda à Meta primeiro.** Ela reenvia o webhook se não receber 200 rapidamente, e reenvio duplica mensagem na tela do atendente. O repasse é **assíncrono**, depois da resposta — reaproveite o padrão de outbox que já existe, não invente um segundo mecanismo.

**Automação fora do ar não pode perder mensagem nem quebrar a entrada.** Se o n8n não responder, o CRM já processou e gravou normalmente; o repasse é retentado com recuo, e desiste com log depois de um limite. O caminho de mensagem do atendente **nunca** depende do n8n estar de pé.

**Repasse o cabeçalho `X-Hub-Signature-256`** junto com o corpo. Sem ele o Dylan não consegue validar a origem do lado dele, e vai acabar aceitando qualquer POST.

Testes: payload chega ao destino byte a byte igual ao recebido; Automação fora do ar não impede a mensagem de aparecer no CRM; sem a variável configurada, o repasse simplesmente não acontece e nada quebra.

## Bloco 2 — Clicar no lead abre a ficha em vez do chat

Na aba Atendimentos, um clique no cartão da conversa abre o painel **Ficha do lead** por cima da tela, e o chat não abre. É preciso fechar a ficha e clicar de novo para chegar na conversa.

O comportamento esperado, e que o protótipo assume: **um clique abre a conversa.** A ficha do lead é o painel da direita, que já existe e acompanha a conversa aberta — não um overlay que intercepta o clique.

Se existir a intenção de "espiar a ficha sem abrir a conversa" (a E17b menciona `PainelLateralLead` com esse propósito), ela precisa de gatilho próprio — um ícone no cartão, ou duplo clique, como o próprio protótipo sugere em `design/componentes/Agenda.html`: *"Clique uma vez para consultar a ficha; clique duas vezes para abrir o atendimento."* Na Agenda esse comportamento faz sentido; **em Atendimentos, não.**

Teste que prova: clique no cartão abre a conversa correspondente, e o overlay da ficha não aparece.

## Bloco 3 — Fidelidade da tela de Atendimentos

`design/componentes/Atendimentos.html` é a referência. Esta é a tela que fica aberta oito horas por dia e é a que o cliente mais olha.

Levantado comparando o protótipo com a homologação. Trabalhe uma coluna por vez, commitando cada uma.

### 3.1 — Coluna da lista

| Elemento do protótipo | Estado hoje |
|---|---|
| Cabeçalho "Atendimentos" com ações à direita | ausente |
| Busca "Buscar cliente ou protocolo..." | ausente |
| **Quatro abas** — Todos, Ativos, Pendentes, Potenciais — com contagem | **só duas** |
| Selo do canal (WhatsApp) no avatar | ausente |
| Empresa como subtítulo do nome | ausente |
| Chip da etapa, colorido, no cartão | ausente |
| Badge de não lidas | ausente |
| Iniciais do responsável no canto do cartão | ausente |
| Horário da última mensagem | ausente |

As quatro visões **já existem no backend** — a E17b entregou `GET /api/v1/atendimentos/contagem` devolvendo `Map<VisaoAtendimento, Long>` com todas. A tela mostra duas. Se faltar dado para algum elemento (não lidas, por exemplo), **diga no relatório em vez de inventar**.

### 3.2 — Cabeçalho da conversa

Selo verde do canal ao lado do nome; linha de contexto com telefone, empresa e responsável; **Finalizar em verde**, não neutro; ícones de busca, etiqueta, telefone e menu à direita.

### 3.3 — Corpo da conversa

Separador de data ("Hoje"); **linha de sistema** marcando o início do atendimento ("Atendimento recebido · WhatsApp · Jardel"); **nome do atendente dentro da bolha enviada**; anexo de imagem com legenda; anexo de documento como cartão com nome, tamanho e descrição.

### 3.4 — Painel da direita

O maior desvio. O protótipo tem, e não existe hoje:

- bloco **Informações gerais**: telefone, e-mail, localização, responsável
- **Etapa do atendimento** com indicador de progresso ("3 de 6") — os dados de etapa e ordem existem em `etapa_atendimento`
- **Etiquetas** como chips coloridos com botão "+ Tag", no lugar do ícone atual
- **Resumo por IA** exibindo o texto quando existir, não só o estado vazio

## Bloco 4 — Três correções pequenas

- **Dashboard no menu para `ADMINISTRADOR`.** A E23 liberou no backend e o `sidebar.tsx` continua restringindo a `GESTOR`/`SUBGESTOR`.
- **A raiz `/` não tem página** e cai no catch-all "Esta área ainda não foi construída". Redirecione para `/atendimentos`.
- **Hospede a JetBrains Mono localmente.** O build depende do CDN do Google e a CI já falhou com seis 404 transitórios.

## Definição de pronto

- [ ] Repasse assíncrono do payload cru, com assinatura, por variável de ambiente
- [ ] Automação fora do ar não afeta a entrada de mensagem — com teste
- [ ] Clique no cartão abre a conversa, com teste
- [ ] Quatro abas com contagem; cartões com canal, etapa, responsável e não lidas
- [ ] Cabeçalho, corpo e painel conforme 3.2 a 3.4
- [ ] As três correções do Bloco 4
- [ ] Lista escrita do que ficou de fora por falta de dado
- [ ] CI verde com **número da run**

Se o tempo apertar, pare no fim de um bloco. O Bloco 1 destrava outra pessoa e vai primeiro; o Bloco 2 custa minutos e incomoda a cada clique.
