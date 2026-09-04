# E123 — o cliente precisa saber com quem está falando

## O pedido

Card: *"Nome apenas dos atendentes em negrito em cima para os clientes"*. Hoje o cliente recebe o
texto solto e não sabe quem respondeu — vários atendentes usam o mesmo número.

O formato desejado é o do CRM antigo:

```
*Daiane:*

Olá! Tudo bem com você? Como posso te ajudar hoje?
```

Nome em negrito do WhatsApp (`*asterisco*`), dois pontos, linha em branco, e então a mensagem.

**A IA não leva nome.** Quando quem responde é a automação, a mensagem sai como sempre saiu.

## Onde isso mora — e onde não pode morar

O `CanalGateway.Envio` carrega `mensagemId`, `telefoneDestino`, `conteudo`, `credencialId` e
`contextoWamid`. **Não carrega o nome de quem enviou.** Então o prefixo tem de ser composto onde o
`Envio` é montado a partir da mensagem persistida — investigue o publicador da outbox e escolha o
ponto certo.

Duas coisas que não pode fazer:

- **Não busque usuário dentro do adaptador da Meta.** O adaptador traduz para o provedor; ele não
  conhece equipe. Se o nome tiver de chegar até lá, ele chega **dentro do `Envio`**, resolvido antes.
- **Não persista o prefixo.** O `conteudo` gravado em `mensagem` continua sendo o texto que o
  atendente escreveu, limpo. A bolha do CRM já mostra quem enviou; gravar "Daiane:" junto poluiria o
  histórico, a busca, o resumo por IA e apareceria duplicado na tela. O prefixo é **formatação de
  saída**, não conteúdo.

## Regras

1. **Só mensagem de humano.** Remetente `ATENDENTE` leva o prefixo; automação e IA, não. Enumere os
   caminhos de saída no repositório e diga no relatório qual leva e qual não leva.
2. **Template nunca leva prefixo.** O corpo é aprovado pela Meta e não pode ser alterado no envio —
   prefixar quebraria a aprovação.
3. **Mídia com legenda leva o prefixo na legenda. Mídia sem legenda continua sem legenda** — não
   invente uma legenda só para carregar o nome.
4. **O formato é configuração da instância**, não string no código. Procure onde os textos da
   instância já vivem e coloque lá, com o nome como parâmetro. Outro cliente pode querer outro
   separador, ou não querer nada.
5. **Sem nome, sem prefixo.** Se por qualquer motivo o nome não for resolvido, a mensagem sai limpa
   em vez de sair com `*:*` ou `*null:*`.

## Decisão registrada

O nome usado é o **nome cadastrado do usuário**, como está na equipe. Se a operação quiser só o
primeiro nome, é ajuste de uma linha depois — não invente o corte agora.

## Testes obrigatórios

1. Atendente manda texto: o que vai para o provedor tem o prefixo; o que fica em `mensagem.conteudo`
   **não** tem.
2. A automação manda texto: sem prefixo, nem no provedor.
3. Template: sem prefixo, em nenhuma hipótese.
4. Mídia com legenda: prefixo na legenda. Mídia sem legenda: nenhuma legenda criada.
5. Nome ausente: mensagem sai limpa, sem `*:*`.
6. A bolha do CRM continua mostrando o texto sem o prefixo — nenhuma regressão de tela.

## Escopo

Sem migration. Sem mudança de contrato público. Não encoste na janela de 24h, no status de entrega
nem na busca por telefone — há outras etapas nesses caminhos.

## Entrega

Branch própria, Conventional Commit, push, PR contra `main`. Relatório nos sete itens do `AGENTS.md`,
dizendo em que ponto exato o prefixo é composto e por que ali.
