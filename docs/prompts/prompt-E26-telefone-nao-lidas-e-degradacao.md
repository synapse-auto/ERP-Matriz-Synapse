# Prompt E26 — Telefone canônico, não lidas, autoria e degradação de tela

> Leia `AGENTS.md`. Entrega em 25/08.
> Blocos em ordem de gravidade. Commite e faça push por bloco.
> Ao encerrar: `cd backend && ./mvnw clean verify` e o **número da run** do CI.

---

## Bloco 1 — Telefone canônico

**O mais grave.** Encontrado pelo próprio agente na E25: `LeadNoCaminhoDeMensagemJdbc` compara telefone literalmente, então `+5561999999999` e `5561999999999` viram **leads diferentes**.

Isso não é duplicidade cosmética. Dois leads significam **dois atendentes donos do mesmo cliente** — e atendente trabalha por comissão. É incidente comercial esperando dado real para acontecer.

**Formato canônico: só dígitos, com código de país, sem `+`, sem espaço, sem hífen.** É o formato que a Meta envia, que é a fonte de maior volume.

- Normalize **na escrita, num ponto só do domínio**. Nenhum caller deve precisar lembrar de normalizar — se dois lugares normalizarem, eles divergem
- Migration que normaliza as linhas existentes
- **Índice único** sobre o telefone normalizado, depois da migration
- O seed e o provisionamento passam a gravar no formato canônico

**Se a migration acusar colisão, pare e me avise.** São duplicados reais, e reconciliar lead é decisão de negócio — envolve histórico de conversa e quem fica com a comissão. Melhor descobrir agora com poucos leads do que em novembro com milhares.

Teste: lead criado por webhook com `5561…` e lead criado pela tela com `+55 61 …` são **o mesmo lead**.

## Bloco 2 — Não lidas

Coluna `lido_ate` (timestamp) em `atendimento`, atualizada quando **o responsável** abre a conversa.

Não lidas = mensagens com `remetente_tipo = LEAD` e `enviado_em > coalesce(lido_ate, epoch)`.

**Gestor abrindo conversa de outro atendente não marca como lida.** O contador é sinal de trabalho pendente do dono; se a leitura de um gestor zerasse, o atendente perderia a fila dele.

Exponha no `CartaoAtendimento` e ligue o badge na lista, que a E25 deixou pronto esperando o dado.

Teste negativo obrigatório: gestor abre conversa alheia, o `lido_ate` do atendimento **não** muda.

## Bloco 3 — Autoria da mensagem vem do dado

A E25 exibe o nome na bolha apenas quando o `remetenteId` bate com o responsável atual, para não atribuir mensagem histórica à pessoa errada. A cautela foi certa; a solução é resolver o nome de verdade.

`mensagem.remetente_id` já existe. Faça o read model resolver o nome a partir dele, e exiba sempre que houver remetente. Assim uma conversa transferida mostra quem realmente escreveu cada mensagem — que é o comportamento do protótipo e a informação que importa numa auditoria de atendimento.

A linha de "atendimento recebido" continua derivada de `iniciado_em` e do canal. É informação verdadeira e não vale criar evento novo agora.

## Bloco 4 — Tela não some quando uma query falha

**A maior dívida estrutural aberta.** A E24 mapeou **doze superfícies** que substituem todo o conteúdo por uma mensagem de erro quando qualquer query falha: Agenda, Dashboard, Equipe, Tags, Mensagens Rápidas, Mensagens Programadas, Lembretes, Automação, painel do lead, timeline, filtros avançados da Agenda e telemetria da Automação. Mais a sidebar.

Foi assim que o produto pareceu morto por horas em 15/08: **uma** query falhou e o menu inteiro sumiu. O defeito era um; o efeito foi o sistema parecer fora do ar.

Estabeleça uma política e aplique nas treze:

**Dado essencial da tela falhou** → mostre o erro **no lugar daquele conteúdo**, com botão de tentar de novo, preservando o resto da tela: sidebar, cabeçalho, navegação. O usuário precisa poder ir para outro lugar.

**Dado auxiliar falhou** → a tela renderiza sem ele. Um resumo por IA indisponível não pode esconder a conversa; a contagem de uma aba não pode esconder a lista.

**A sidebar é caso especial: o menu sempre renderiza.** Se as feature flags não vierem, mostre os itens que não dependem de flag em vez de nada. Perder o menu é perder o produto inteiro; mostrar um item a mais é um clique que responde "indisponível".

Extraia o padrão para um componente único em `components/ui/` — treze implementações do mesmo comportamento divergem na primeira mudança.

**Teste que prova, e não vale mock parcial:** com o endpoint de features respondendo 500, o menu ainda aparece e é navegável.

## Definição de pronto

- [ ] Telefone canônico, normalizado num ponto só, com migration e índice único
- [ ] Colisão de duplicados reportada, não resolvida por conta própria
- [ ] `lido_ate` e badge de não lidas, com teste negativo do gestor
- [ ] Nome do remetente resolvido de `remetente_id`
- [ ] Política de degradação aplicada nas treze superfícies, com componente comum
- [ ] Teste: features em 500 e menu navegável
- [ ] CI verde com **número da run**

No relatório: diga se a migration de telefone encontrou colisão e **quantas**. Se encontrou, não reconcilie — liste os pares e devolva para decisão.
