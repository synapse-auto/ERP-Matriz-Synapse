# Abertura de atendimento pela Agenda

## Causa

A Agenda envia `POST /api/v1/atendimentos/leads/{leadId}/novo` e recebe tanto o
`leadId` quanto o `atendimentoId` canônico. Até esta correção, o frontend descartava o
`atendimentoId`, navegava para `ATIVOS` e tentava selecionar o cartão apenas por
`leadId` dentro da lista já filtrada.

A migration `V60__rls_agenda_colaborativa.sql` permite que um colaborador da Agenda abra
um atendimento de lead já existente: ele vira participante ativo do atendimento, embora
o responsável oficial não mude. A RLS permite que esse participante leia o atendimento,
mas a visão `ATIVOS` do painel é uma fila operacional e exige que `a.atendente_id` seja o
usuário autenticado. Assim, a resposta do comando era autorizada e correta, enquanto o
cartão continuava ausente da lista usada como única fonte de seleção.

O sintoma apareceu com maior frequência na Clínica Fêmina pela combinação de seus dados
de Agenda colaborativa e atendimentos ativos de responsáveis diferentes. Não existe, nem
foi incluída, regra por cliente, domínio, credencial ou instância.

## Correção

O parâmetro `atendimentoId` agora é preservado na navegação. A página resolve o cartão
diretamente em `GET /api/v1/atendimentos/{atendimentoId}/cartao`, cuja consulta parte de
`atendimento` e permanece protegida pela mesma sessão, RLS e transação de chat. A busca
pontual não reaplica a visão operacional ou os filtros da lista. Depois dela, Agenda e
lista usam a mesma seleção centralizada; uma atualização tardia de lista só atualiza o
cartão selecionado quando continua sendo o mesmo atendimento.

Falhas 403, 404 e 409 recebem feedback do catálogo de textos. O diagnóstico de abertura
registra apenas ids técnicos, papel, visão, quantidade de filtros, status HTTP e tipo de
evento WebSocket — nunca conteúdo de conversa, telefone ou token.

## Limites

Esta correção não altera RN-CRM-01, RN-CRM-06, as políticas RLS, o pipeline de mensagens
ou a regra de participação colaborativa da Agenda. Um atendimento fora do alcance da
sessão continua retornando 404 no endpoint pontual.
