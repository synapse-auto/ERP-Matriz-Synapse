# E121 — a janela de 24h só conta mensagem do cliente

## O defeito

`lead.ultima_interacao_em` é avançado **em toda mensagem que sai**, não só nas que chegam:

```sql
UPDATE lead
   SET num_atendimentos    = num_atendimentos + ?,
       num_mensagens       = num_mensagens + ?,
       ultima_interacao_em = GREATEST(COALESCE(ultima_interacao_em, ?), ?)
 WHERE id = ?
```

`registrarInteracao` é chamado por `EnviarMensagemUseCase`,
`ResponderAtendimentoDaAutomacaoUseCase` e `RegistrarMensagemEnviadaDaAutomacaoUseCase`. Confirme os
três antes de mexer.

E é esse mesmo campo que decide a janela de 24h no envio:

```java
if (conteudo instanceof ConteudoDeEnvio.MensagemLivre
        && !canal.aceitaTextoLivre(contato.ultimaInteracao(), agora)) {
    throw new ForaDaJanelaException(leadId);
}
```

**Resultado: a janela nunca fecha enquanto a equipe continuar falando.** A regra da Meta é a oposta —
só mensagem **recebida do cliente** abre e renova os 24h; mensagem de saída não estende nada.

Em produção isso significa: o atendente manda texto livre dias depois da última mensagem do cliente,
o CRM aceita, grava `ENVIADO`, a Meta recusa por estar fora da janela, e **o cliente não recebe**.
Como o `statuses[]` ainda não é processado (E118), a bolha diz "enviado" para sempre. É a mesma
família do bug do áudio, com o agravante de **falhar abrindo**: deixa passar o que devia barrar.

## Cuidado que decide o desenho

**Não mude o significado de `ultima_interacao_em`.** Ele tem um segundo consumidor com semântica
diferente: o filtro `semRetornoDias` da Agenda, e o comentário em `RegistrarMensagemRecebidaUseCase`
registra por que o campo passou a ser preenchido como está. Para "há quanto tempo esse lead não tem
movimento", contar interação de qualquer lado é legítimo. Reaproveitar o campo quebra esse filtro.

O que falta é uma **fonte nova**, com uma pergunta só: *quando foi a última mensagem do cliente?*

**Recomendação:** coluna nova em `lead` (algo como `ultima_mensagem_do_lead_em`), escrita apenas por
`RegistrarMensagemRecebidaUseCase`, com backfill a partir do histórico. Custa O(1) no caminho
crítico, que é onde a decisão acontece, e é o mesmo padrão desnormalizado que o resto do lead já usa.

A alternativa — consultar `mensagem` no envio — é aceitável e não precisa de migration, mas põe uma
consulta a mais no caminho crítico. Se escolher essa, justifique.

## O ponto inegociável: uma definição só

A **E114** acabou de corrigir o cartão do painel para computar a última mensagem do cliente por
LATERAL sobre `a.lead_id`. Depois desta etapa, **a tela e o envio têm de ler a mesma fonte**. Se você
criar a coluna, o cartão passa a lê-la e a LATERAL sai.

Duas definições de "janela aberta" no mesmo sistema é como a gente chegou aos 28 clientes duplicados
do nono dígito: a mesma regra escrita em dois lugares diverge. Não repita.

Comece confirmando o que a E114 deixou no `PainelDeAtendimentosRepositorioJdbc` — ela é pré-requisito
e já deve estar na `main`.

## Os três lugares que decidem a janela

Todos passam a usar a fonte nova:

1. `EnviarMensagemUseCase` — envio manual do atendente.
2. `IniciarNovoContatoUseCase` — primeiro contato pela tela.
3. `ResponderAtendimentoDaAutomacaoUseCase` — resposta da IA.

Enumere-os a partir do repositório, não desta lista: pode ter nascido um quarto.

## Migration

A próxima livre — a **V51 já está tomada** (`backfill_disponibilidade_ia_subgestor`, da E113).
A V50 **já foi aplicada em produção**: não encoste nela.

O backfill lê `mensagem` com `remetente_tipo = 'LEAD'` por lead. Duas coisas obrigatórias:

- **Contexto de serviço.** `lead`, `atendimento` e `mensagem` têm `FORCE ROW LEVEL SECURITY` desde a
  V12, e FORCE alcança o dono da tabela — que é quem roda as migrations. Sem contexto, a política nega
  tudo e o backfill vira **no-op silencioso**. Use o mesmo `set_config('app.papel','SERVICO', TRUE)`
  com o guarda de aborto que a V50 documentou, e copie a explicação de lá.
- **Lead que nunca escreveu fica NULL**, e NULL significa janela fechada. São 7.144 contatos
  importados nessa situação: nenhum deles pode aparecer com janela aberta.

## Testes obrigatórios

1. Cliente escreveu há 1 hora, atendente respondeu 10 vezes desde então: janela **aberta**.
2. Cliente escreveu há 30 horas, atendente mandou mensagem há 1 hora: janela **fechada** — hoje passa,
   e é o bug.
3. Lead que nunca escreveu (contato criado pela tela ou importado): janela **inexistente**, template
   obrigatório.
4. Mensagem do cliente reabre a janela; mensagem da IA e do atendente não.
5. Os três caminhos de envio concordam entre si.
6. **Teste de concordância tela × envio**: para o mesmo lead, o estado que o cartão exibe e o
   resultado de `canal.aceitaTextoLivre(...)` nunca se contradizem.
7. `semRetornoDias` continua se comportando como hoje — prova de que `ultima_interacao_em` não mudou
   de significado.
8. Backfill: lead com histórico recebe o instante da última mensagem do cliente; rodar de novo não
   altera nada.

## Escopo

Não processe `statuses[]` aqui (é a E118). Não mexa em RLS, no adaptador da Meta nem no composer.

## Entrega

Branch própria, Conventional Commit, push, PR contra `main`. Relatório nos sete itens do `AGENTS.md`.

No relatório, **estime quantas mensagens em produção foram enviadas fora da janela real** — dá para
medir comparando `mensagem.enviado_em` de saída com a última mensagem `LEAD` do mesmo lead. É esse
número que diz ao Lucas quantos clientes ficaram sem resposta sem ninguém saber.
