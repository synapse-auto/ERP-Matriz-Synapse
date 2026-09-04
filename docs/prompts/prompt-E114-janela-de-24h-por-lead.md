# E114 — a janela de 24h é do cliente, não do atendimento

## O sintoma

"A janela do template está a cada encerramento de conversa (não válida por 24h)." Depois que um
atendimento é finalizado e um novo é aberto para o mesmo cliente, o composer passa a exigir template
— mesmo que o cliente tenha escrito há dez minutos.

## A causa, com os dois lados do sistema

**O backend está certo.** `EnviarMensagemUseCase` decide a janela por
`contato.ultimaInteracao()`, que vem de `leads.contatoParaEnvio(leadId)` e lê
`lead.ultima_interacao_em` — um campo **do lead**, alimentado por `RegistrarMensagemRecebidaUseCase`
quando o cliente escreve. Finalizar atendimento não mexe nele. Se o atendente digitasse texto livre,
o envio seria aceito.

**O frontend está errado.** `composer.tsx` chama
`estadoDaJanelaTextoLivre(conversa.ultimaMensagemDoLeadEm)`, e esse campo vem do cartão do painel,
montado em `PainelDeAtendimentosRepositorioJdbc`:

```sql
LEFT JOIN LATERAL (
    SELECT enviado_em FROM mensagem m2
     WHERE m2.atendimento_id = a.id AND m2.remetente_tipo = 'LEAD'
     ORDER BY m2.enviado_em DESC LIMIT 1
) ultima_lead ON true
```

`m2.atendimento_id = a.id` — **recorta por atendimento**. Atendimento novo não tem mensagem do
cliente ainda, então `ultimaMensagemDoLeadEm` volta nulo, `estadoDaJanelaTextoLivre` devolve
`inexistente` e a tela manda o atendente usar template.

Repare que o cartão já sabe fazer isso certo em outro campo: `nao_lidas`, logo acima, percorre
`atendimento_do_lead.lead_id = a.lead_id`. O recorte por lead já existe no mesmo SELECT; a janela
simplesmente não o usa.

**Consequência real:** o atendente manda template quando podia mandar texto livre. Template custa
dinheiro (é conversa paga na Meta), soa robótico, e o cliente que acabou de escrever recebe uma
mensagem modelo. Não é bug de tela — é dinheiro e experiência.

## O que fazer

Faça o cartão devolver a mesma verdade que o backend usa para decidir. **Prefira
`lead.ultima_interacao_em`** — é literalmente a fonte que `EnviarMensagemUseCase` consulta, então a
estimativa da tela e a autoridade do envio param de poder divergir por construção. O `lead` já está
no `FROM` do painel (`JOIN lead l ON l.id = a.lead_id`), então é trocar a LATERAL por `l.ultima_interacao_em`.

Antes de trocar, **confirme no repositório** que `ultima_interacao_em` só é tocado por mensagem
recebida do cliente. `IniciarNovoContatoUseCase` tem um comentário afirmando isso ("Sem mensagem do
cliente e sem envio: nao toca ultima_interacao_em") e a V50 o atualizou com `GREATEST` na fusão. Se
achar qualquer caminho que grave esse campo em envio de saída, **pare e relate** — nesse caso a
fonte certa é a LATERAL corrigida para `a.lead_id`, e não o campo.

Não invente um endpoint novo, não mude o contrato do cartão, não mexa no `janela-24h.ts`: a função
está certa, o dado que chega nela é que está errado.

## Testes obrigatórios

1. IT do painel: lead com mensagem do cliente há 1 hora, atendimento **finalizado**, novo atendimento
   aberto sem nenhuma mensagem → o cartão devolve `ultimaMensagemDoLeadEm` preenchido, e o front
   calcula `aberta`.
2. IT: lead cuja última mensagem do cliente tem 30 horas → `fechada`, mesmo com atendimento novo.
3. IT: lead que nunca escreveu (contato criado pela tela ou importado pelo CSV) → `inexistente`.
   **Este caso importa agora**: entraram 7.144 contatos importados que nunca escreveram, e nenhum
   deles pode aparecer com janela aberta.
4. Teste que trava a concordância: para o mesmo lead, o estado exibido pela tela e o resultado de
   `canal.aceitaTextoLivre(...)` no backend nunca se contradizem.
5. Os testes existentes de `janela-24h.test.ts` e `composer-janela.test.tsx` continuam verdes sem
   alteração — se algum precisar mudar, explique por quê no relatório.

## Escopo

Sem migration. Sem mudança de contrato público. Não encoste em `EnviarMensagemUseCase` — ele já
está correto e outro agente pode estar mexendo no envio de template neste momento.

## Entrega

Branch própria, Conventional Commit, push, PR contra `main`. Relatório nos sete itens do `AGENTS.md`.
