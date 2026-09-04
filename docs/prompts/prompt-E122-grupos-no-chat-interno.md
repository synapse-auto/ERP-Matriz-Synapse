# E122 — grupos no chat interno

## O pedido

Card da operação: *"Criar Grupos no chat interno que nem no whatsapp"*. Hoje o chat interno só
tem conversa entre duas pessoas; a equipe quer falar em grupo sem sair para o WhatsApp pessoal.

## O que já existe — leia antes de projetar

Isto **não** é modelagem nova. Confirme cada item no repositório antes de escrever código; se algum
não bater, pare e relate.

- `tipo_conversa_chat` é um enum criado na **V1** com `('DIRETA', 'GRUPO')`. O valor `GRUPO` está lá
  desde o começo e nunca foi usado.
- `chat_interno_participante` é `(conversa_id, usuario_id)` — **já é n:n, sem limite de dois**.
- A **RLS já é por participação** (V37): `app_chat_participa(conversa_id)` protege conversa,
  participante e mensagem. A mesma função vale para grupo sem nenhuma mudança de política.
- Reação (V45), mídia (E80), resposta e encaminhamento (E87) são por mensagem e vêm de graça.
- `ChatInternoRepositorioJdbc` já ramifica em `CASE WHEN c.tipo = 'DIRETA'` para derivar nome e foto
  do outro participante, e a busca de conversa existente entre duas pessoas já filtra
  `WHERE c.tipo = 'DIRETA'` — então grupo não colide com o dedupe da conversa direta.

## O ponto que decide a etapa: o bootstrap

A V37 tem esta função, e o comentário dela explica o problema:

```sql
-- A criação direta é a única operação que insere os dois participantes de uma
-- vez. SECURITY DEFINER mantém o bootstrap fora da política genérica de INSERT:
-- ela não pode ser usada para entrar numa conversa já iniciada.
CREATE OR REPLACE FUNCTION app_criar_conversa_direta(primeiro UUID, segundo UUID)
```

É ovo e galinha: a política de INSERT em `chat_interno_participante` exige **já** participar da
conversa, então ninguém consegue se inserir na conversa que acabou de criar. A conversa direta
resolve com uma função `SECURITY DEFINER` estreita.

**Grupo precisa do equivalente** — uma função de criação que insere a conversa e os participantes
iniciais numa transação, e **nada além disso**. Espelhe o desenho da `app_criar_conversa_direta`,
incluindo o cuidado que o comentário registra: a função não pode virar uma porta para entrar em
conversa alheia. O criador tem de estar entre os participantes iniciais, e a função deve recusar
qualquer chamada em que ele não esteja.

## Migration

A próxima livre — confirme no repositório; **V50, V51, V52 e V53 já estão tomadas e a V50 já rodou
em produção**. Não encoste em migration aplicada.

O que falta no schema:

- **Nome do grupo.** `chat_interno_conversa` não tem coluna de nome — a conversa direta deriva do
  outro participante, o grupo não tem de quem derivar. Coluna anulável, obrigatória por regra apenas
  quando `tipo = 'GRUPO'`. Se usar CHECK, lembre que conversa direta continua com nome nulo.
- **Quem criou**, apenas se a mensagem de sistema de criação não bastar para o histórico. Não é
  usado para permissão — a regra 2 não tem administrador.
- A função de criação de grupo, no mesmo espírito da direta.

Não crie tabela nova de "grupo": grupo é uma `chat_interno_conversa` com `tipo = 'GRUPO'`. Duas
tabelas para o mesmo conceito é o começo de duas regras de visibilidade divergentes.

## Regras — decididas pela gestão em 01/09, não são defaults

O Lucas respondeu as três perguntas em aberto. **Trave cada uma com teste**: elas são decisão de
negócio registrada, e mudar depois tem de ser explícito.

1. **Qualquer usuário ativo cria grupo**, escolhendo nome e participantes iniciais.
2. **Não existe hierarquia dentro do grupo.** Quem está dentro pode adicionar, remover e renomear.
   Não crie papel de administrador, não crie coluna de dono para efeito de permissão.
3. **Qualquer participante sai quando quiser.**
4. **Quem entra depois enxerga o histórico inteiro** — confirmado pela gestão, e é o oposto do
   WhatsApp. É também o que a RLS atual já produz, porque `app_chat_participa` não tem recorte por
   data de entrada. Não acrescente recorte.
5. **Conversa direta não vira grupo.** Adicionar um terceiro participante numa `DIRETA` tem de ser
   recusado — senão nasce um grupo sem nome e fora de toda regra acima.
6. **Grupo sem participantes** não pode ficar órfão. Decida o destino (fechar, apagar ou deixar
   inerte) e justifique; o que não pode é linha invisível ocupando a lista de alguém.

Consequência aceita da regra 2, que precisa estar coberta por teste e não tratada como bug: **um
participante pode remover outro, inclusive quem criou o grupo.** É grupo plano, como o Lucas pediu.

## A RLS que já existe é exatamente esta regra

As políticas de `chat_interno_participante` na V37 permitem INSERT e DELETE a **qualquer
participante** da conversa. Eu tinha levantado isso como possível buraco; com a decisão da gestão,
**é a especificação**.

Portanto: **não estreite essas políticas.** Escreva um teste que prova que um participante comum
consegue adicionar e remover outro, para que ninguém "conserte" isso mais tarde achando que é falha.

O que continua valendo é o limite de fora: quem **não** participa não enxerga nem toca a conversa.

## Mensagens de sistema

Grupo sem rastro de quem entrou e quem saiu vira discussão sobre quem viu o quê. O `tipo_mensagem`
já existe em `chat_interno_mensagem` — verifique se comporta um evento de sistema sem remetente
humano, ou se precisa de valor novo. Mínimo: grupo criado, participante adicionado, participante
removido, participante saiu, nome alterado.

## Frontend

Reaproveite a tela do chat interno que já existe. O que muda:

- criar grupo (nome + seleção de participantes) a partir de onde hoje se inicia conversa direta;
- as ações de participantes ficam visíveis para todo mundo que está dentro, sem distinção de papel;
- a lista precisa mostrar nome do grupo e um avatar próprio, já que não há "o outro participante";
- texto novo **sempre** no `textos.json`, nenhuma string solta em componente.

## Fora de escopo

Foto do grupo, menção a pessoa (`@`), grupo com atendimento vinculado, e qualquer mudança no chat
com o cliente. Não encoste no caminho de mensagem do WhatsApp.

## Testes obrigatórios

1. Criar grupo com três participantes: os três enxergam a conversa; um quarto usuário não.
2. A função de criação recusa chamada em que o criador não está entre os participantes.
3. Participante comum **consegue** adicionar, remover outro e renomear — trava a regra 2. E quem
   não participa não consegue nada disso.
4. Participante adicionado depois enxerga as mensagens anteriores (trava a regra 4).
5. Quem saiu deixa de enxergar mensagens novas.
6. Adicionar terceiro numa conversa `DIRETA` é recusado.
7. Conversa direta continua funcionando exatamente como hoje — nenhuma regressão no dedupe nem na
   derivação de nome e foto.
8. Mensagens de sistema aparecem nos eventos do item correspondente.
9. Front: criar grupo, listar com nome próprio, e as ações de admin ausentes para quem não é.

## Entrega

Branch própria, Conventional Commit, push, PR contra `main`. Relatório nos sete itens do `AGENTS.md`.
No relatório, diga explicitamente como você garantiu que quem **não** participa continua sem
alcance nenhum à conversa.
