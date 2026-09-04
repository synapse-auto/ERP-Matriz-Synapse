# E117 — buscar lead por telefone tem que usar a mesma regra que grava o telefone

## O que aconteceu

A E111 subiu em 31/08 21:57 e normalizou 201 telefones em produção: celular brasileiro passou a ser
gravado no formato de discagem, com o nono dígito. A regra vive em
`TelefoneCanonico` (crm-core, domínio) e está aplicada em três lugares — caminho de mensagem
(`LeadNoCaminhoDeMensagemJdbc`), edição do lead (`AtualizarLeadUseCase`) e importação
(`PrepararImportacaoLeadsCsv`).

**Não está aplicada na busca.** E é por busca que a Automação encontra o cliente.

O integrador do n8n consulta `POST /api/v1/leads/filtrar` com o `wa_id` que a Meta entrega — que
para boa parte dos números brasileiros vem **sem** o nono dígito, com 12 dígitos. O banco agora
guarda 13. `556181536371` não é nem igual nem substring de `5561981536371`, porque o `9` entra no
meio. A consulta deixou de casar na noite do deploy, e a Automação parou de achar o atendimento.

Isto não é regressão da E111: é uma porta de entrada que ficou sem a regra e que só apareceu quando
os dois formatos deixaram de coexistir no banco.

## O que fazer

**Normalize o valor do critério de telefone no filtro de leads, com `TelefoneCanonico` — a mesma
instância injetada que o resto do sistema usa.** Não reimplemente a regra, não copie o `if`, não
crie um helper novo em outra camada. Uma regra, um lugar.

Cuidado que decide o desenho: o filtro aceita busca parcial. Normalizar um pedaço de número
("8153") corromperia a busca. Então:

- valor que `TelefoneCanonico.normalizar` aceita → busque pelo **canônico**;
- valor que ele recusa (`TelefoneInvalidoException`, curto demais para ser telefone) → mantenha
  exatamente o comportamento de hoje, sem normalizar.

O usuário que digita meio número continua achando o que achava; a Automação que manda o número
inteiro passa a achar.

**Investigue e trate a mesma classe de defeito em `app_buscar_lead_para_entrada`** (criada na V36).
Ela faz `regexp_replace(telefone,'[^0-9]','','g') LIKE '%' || termo || '%'` — pelo mesmo motivo,
um termo de 12 dígitos não casa mais com o cadastro de 13. É a busca que o atendente usa para pedir
entrada num atendimento de colega. Se confirmar, corrija junto; se a sua leitura do código disser
outra coisa, **relate em vez de mexer**.

**Varra o resto.** Encontre toda porta de entrada que aceita telefone de fora — controller, endpoint
interno, consumidor de fila, job — e diga no relatório, uma por uma, se ela normaliza ou não. As
que não normalizam e deveriam, corrija. As que você decidir não mexer, justifique. Essa lista é
metade da entrega: o bug de hoje existe porque ninguém tinha essa lista.

## Contexto do integrador

O Dylan está aplicando a regra do lado dele **como contorno temporário**, com o texto exato que
mandamos:

> assinante de 8 dígitos começando em 6–9 ganha o 9 (celular); começando em 2–5 fica como está
> (fixo); outro tamanho ou outro DDI, não mexe.

Isso destrava a operação hoje à noite e **precisa sair depois que esta etapa subir**. Duas
implementações da mesma regra, em repositórios diferentes, é exatamente o que produziu os 28
clientes duplicados que a V50 acabou de fundir. No relatório, deixe registrado que o contorno do n8n
tem de ser removido, para alguém cobrar isso.

## Testes obrigatórios

1. Filtrar por telefone com 12 dígitos (formato que a Meta entrega) acha o lead gravado com 13.
2. Filtrar com 13 dígitos acha o mesmo lead — o comportamento de hoje não pode quebrar.
3. Filtrar com máscara (`+55 (61) 98153-6371`) acha o mesmo lead.
4. Filtrar por fixo de 12 dígitos (`556132241234`) acha o fixo e **não** ganha nenhum dígito.
5. Busca parcial (`8153`) continua se comportando como hoje.
6. Número de outro DDI não é tocado.
7. Se você mexer em `app_buscar_lead_para_entrada`: termo de 12 dígitos acha o lead de 13, e a
   RN-CRM-01 continua valendo — a busca não pode passar a revelar lead que o usuário não enxerga.
8. Nenhuma política RLS muda. Um teste que prove isso, se ainda não existir.

## Escopo

Sem migration se a correção for em Java. Se `app_buscar_lead_para_entrada` precisar mudar, é
`CREATE OR REPLACE FUNCTION` numa migration nova — a próxima livre, a V50 está tomada e **já foi
aplicada em produção**, então não encoste nela.

Não mexa em `EnviarMensagemUseCase`, no composer, na janela de 24h nem em
`cabecalho-conversa.tsx` — há outras etapas nesses arquivos agora.

## Entrega

Branch própria, Conventional Commit, push, PR contra `main`. Relatório nos sete itens do
`AGENTS.md`, com a varredura das portas de entrada e a nota sobre remover o contorno do n8n.
