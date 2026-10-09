# 70. Visibilidade de atendimentos em IA e finalizados para atendentes (achado preexistente)

Nota **separada** do `docs/69`. Registra um comportamento que **já existe** no CRM e que a revisão do card
de informações do chatbot trouxe à tona. Não é parte daquela entrega, **não a autoriza e não a amplia**.

## O que acontece hoje

A política `rls_atendimento` (V59 e, depois, V93) deixa **qualquer atendente** enxergar:

- atendimentos `EM_IA` de qualquer colega (os "Potenciais" da RN-CRM-01, que prevê esse acesso);
- atendimentos **`FINALIZADO` de qualquer colega**, com o histórico completo das conversas.

A V59 registra isso como decisão de produto da E145 ("atendimento encerrado volta ao balcão"), e o
comentário da própria migration chama a consequência de "aceita e grande". O texto literal da RN-CRM-01
("atendente vê apenas seus leads mais os em status IA") **não** menciona finalizados.

A leitura do histórico (`GET /api/v1/atendimentos/{id}/mensagens`) depende só dessa política. Convite
pendente (V87/V93) dá leitura somente para quem foi convidado, enquanto o convite estiver vigente.

## Relação com o card do chatbot

O card **herda exatamente** essa regra e não vai além dela. `InformacoesDoChatbotVisibilidadeIT` compara,
para cada usuário e cada cenário, o status da leitura dos cards com o da leitura das mensagens, e os dois
sempre coincidem. Nenhum acesso novo nasceu com o card. O que muda na prática é só o que há para ler: o
card concentra o que o chatbot coletou (nome, telefone, interesse), então em um atendimento finalizado ou
em IA de um colega ele é mais compacto e mais sensível do que as mensagens espalhadas.

## O que NÃO foi feito

- Nenhuma política foi alterada nem ampliada. Nenhum acesso a atendimentos de colegas foi autorizado.
- Não se tratou o achado como autorização de produto: restringir o card a um subconjunto do que as
  mensagens já mostram criaria uma regra paralela e divergente (o card seria mais restrito que a conversa
  de onde veio), e isso é decisão de produto, não técnica.

## Decisão pendente (dono do produto)

Confirmar se a regra da E145 para `FINALIZADO` continua sendo a intenção, **inclusive para o conteúdo
coletado pelo chatbot**. Se não for, a correção é na política `rls_atendimento` (e vale igualmente para
mensagens, ficha e card), com migration nova e teste que viole a regra de propósito. Até lá, a paridade
entre cards e mensagens é a garantia: se a política mudar, os dois mudam juntos e o IT de visibilidade
acusa qualquer divergência.
