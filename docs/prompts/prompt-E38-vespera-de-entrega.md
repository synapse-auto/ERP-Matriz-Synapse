# Prompt E38 — véspera da entrega

> Leia `AGENTS.md`. **Entrega amanhã, 25/08.**
> Commite e faça push por bloco.
> Ao encerrar: `cd backend && ./mvnw clean verify` e o **número da run** do CI.

---

## Contexto — esta etapa não adiciona função

Estado em homologação, verificado hoje: deploy em `90c1cd6`, migrations até a `V34` verdes,
**smoke RLS passou**, backfill de disponibilidade conferido. O que está no ar funciona.

O que falta não é código novo: é **a demonstração estar vazia** e **nada quebrar amanhã**.

> **Regra desta etapa: nenhuma migration nova, exceto se o Bloco 1 provar que o seed exige.**
> Se qualquer coisa aqui pedir mudança de schema, **pare e avise.** Véspera de entrega não é hora
> de alterar tabela — este projeto tem dezoito casos registrados de proteção que existia e não
> protegia, e todos vieram de pressa.

## Bloco 1 — O seed precisa rodar contra o schema atual

`docker/provisionamento/seed-demonstracao.sql` **nunca foi executado**. Foi escrito antes da `V24` e
o schema andou muito desde então: telefone canônico com DDI (`V24`/`V26`), `senha_alterada_em`
(`V28`), mensagens interativas com `opcoes` (`V30`), `disponibilidade_atendente_ia` com a semântica
nova (`V34`).

Hoje o ambiente tem **1 atendente e 6 leads**, todos resíduo de teste manual. A tela que o cliente
vai abrir amanhã está praticamente vazia — e o rodízio da E37 não tem entre quem distribuir.

Requisitos:

- O seed roda de ponta a ponta contra um banco com **todas** as migrations aplicadas, sem erro.
- Popula atendentes suficientes para o rodízio ser observável: **pelo menos quatro** ATENDENTE
  ativos, com nomes que **não** estejam em ordem alfabética conveniente — o rodízio da E37 ordena
  por carga, e um seed alfabético esconderia uma regressão.
- Cada atendente novo nasce com linha em `disponibilidade_atendente_ia`, coerente com o default da
  E36b. Atendente seedado que não aparece em `/internal/v1/atendentes/disponiveis` quando ONLINE é
  defeito.
- Telefones no formato canônico com DDI — o seed antigo é anterior a essa regra e vai violar o
  `CHECK` da `V24`.
- Leads distribuídos entre os atendentes, com carga **desigual**, para a tela de Dashboard e o
  rodízio mostrarem algo diferente de zero.
- Conversas com mensagem de entrada e de saída, para a aba Atendimentos não abrir vazia.
- **Idempotente ou claramente não-idempotente.** Se rodar duas vezes duplicar tudo, escreva isso no
  topo do arquivo em uma linha. O pior caso é alguém rodar de novo amanhã por dúvida.

> **Não apague dado existente.** O ambiente tem conversas reais de teste com a Meta e o lead
> `test user name` (`16315551181`). O seed **acrescenta**; a limpeza é decisão do arquiteto e já
> existe em `docker/provisionamento/limpar-demonstracao.sql`.

**Teste automatizado:** um teste de integração que aplica todas as migrations e executa o seed
inteiro. Se o seed quebrar de novo daqui a três migrations, o CI avisa em vez de alguém descobrir na
véspera. Este é o item que impede o problema de se repetir.

## Bloco 2 — Varredura de véspera

Passe por **todas** as telas que o menu mostra e prove que abrem. O menu real hoje é: Atendimentos,
Dashboard, Agenda de Contatos, Tags, Mensagens Rápidas, Mensagens Programadas, Lembretes, Equipe,
Automação.

- Cada uma abre, com dado do seed, sem erro no console e sem `500` na rede.
- **Nenhum item escondido por flag aparece.** `feature_flag` tem uma linha só (`dashboard`), então
  Campanhas, Horários, Relatórios e Banco de Arquivos precisam continuar fora do menu. Item visível
  levando a tela vazia é o pior defeito possível numa demonstração.
- Automação: as três abas carregam, e a aba selecionada sobrevive a recarregar (E35b).
- **A troca de senha da E29** não pode surpreender ninguém amanhã: confirme que um usuário com senha
  provisória é levado à tela de troca e consegue concluir. Já derrubou uma apresentação uma vez.
- Degradação: com o backend fora, a aba Atendimentos mostra erro tratado, não tela branca. É a regra
  de precedência absoluta do projeto.

Relate **o que você viu**, tela por tela. "Tudo ok" não é relatório.

## Bloco 3 — Paleta de tags do protótipo

Só se os Blocos 1 e 2 fecharem. É o único item de fidelidade que custa quase nada e o cliente vê.

O protótipo aprovado tem **7 tons e 22 ícones** no modal de tag; o construído tem 7 e 14. Não há
backend envolvido — é o conjunto de opções do modal.

- Complete os ícones a partir do protótipo, usando `lucide-react`, que já é a biblioteca do projeto.
- Tag já cadastrada com ícone antigo continua renderizando. Teste isso.
- Sem cor hardcoded: os tons saem dos tokens do tema, como o resto.

---

## Definição de pronto

- [ ] Seed roda inteiro contra o schema atual, sem erro
- [ ] Pelo menos quatro atendentes ativos, com disponibilidade coerente com a E36b
- [ ] Telefones canônicos com DDI
- [ ] Carga desigual entre atendentes
- [ ] Teste de integração que executa o seed depois das migrations
- [ ] Varredura das nove telas, relatada uma a uma
- [ ] Nenhum item de flag desligada visível no menu
- [ ] Fluxo de troca de senha confirmado ponta a ponta
- [ ] Paleta de tags, se os blocos anteriores fecharam
- [ ] **Nenhuma migration nova** além do que o Bloco 1 exigir, com justificativa
- [ ] CI verde com **número da run**

## No relatório

1. **O que o seed quebrava antes**, erro por erro. É o que diz o quanto ele estava defasado.
2. Quantos atendentes, leads, atendimentos e mensagens ele cria.
3. A varredura, tela por tela, com o que apareceu.
4. Se criou migration, **qual e por quê** — a expectativa é nenhuma.
5. **Os nomes dos testes novos, um por linha.** Não informe o total da suíte.
6. O SHA final **e o SHA curto** — `SYNAPSE_IMAGE_TAG` usa a tag curta, nunca `latest`.

---

## Fora desta etapa, por decisão — não é esquecimento

Estes itens estão em `docs/14` e **não entram na véspera**:

- **Horários de trabalho** — módulo inteiro; o item está escondido por flag, o cliente não vê
- **Kanban na Agenda**, **importar/exportar CSV**, **troca de credencial do canal** — médios, sem
  tela quebrada hoje
- **Mensagens rápidas compartilhadas ("Geral")** — muda visibilidade de dado; risco alto para o
  ganho, na véspera
- **Avaliação por atendimento** — travada na escala do CSAT, que é decisão do cliente
- **Rotinas por atendente** e **`mensagem_festiva`** — fase 2, já decidido

Também fora: qualquer execução ou agendamento de regra de automação (RN-CRM-07), e o refino visual
da Agenda e do composer, que tem prompt próprio (`prompt-E34`).
