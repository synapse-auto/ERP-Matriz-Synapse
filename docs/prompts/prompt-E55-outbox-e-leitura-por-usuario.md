# Prompt E55 — a mensagem que demora 30s e o ponto azul que não some

> Leia `AGENTS.md`, `CLAUDE.md` e `docs/22-bugs-abertos-26-08.md` (bugs 9 e 5).
> **Pode commitar localmente a qualquer momento.** Não execute `git push` sem autorização explícita.

Dois blocos independentes, os dois de backend, em arquivos que não se cruzam. **Faça um commit por
bloco** — o Bloco 1 mexe no caminho de entrega de mensagem e precisa poder voltar sozinho.

---

# Bloco 1 — Tirar a chamada de rede de dentro da transação

## O defeito

`PublicadorDaOutboxOperacoes.rodada()` é **uma transação só**. Dentro dela, reserva até
`lote = 50` linhas com `FOR UPDATE SKIP LOCKED` e percorre **em sequência**, chamando o provedor uma a
uma, com `WHATSAPP_TIMEOUT: 10s` por chamada. E o `@Scheduled` é `fixedDelay`: a próxima rodada só
começa 1s depois que a anterior **termina**.

Três consequências, todas observadas:

1. Uma mensagem para um número problemático segura **todas as que estão atrás dela na mesma rodada**,
   por até dez segundos cada. É a origem dos "30 segundos".
2. Enquanto a rodada está pendurada num timeout, **nenhuma outra sai** — o `fixedDelay` não recomeça.
3. **O pior, e que não é o sintoma relatado:** a transação segura uma conexão do pool do chat durante
   a rodada inteira, potencialmente dezenas de segundos. O `SYNAPSE_DB_POOL_CHAT_TIMEOUT_MS` está em
   **3000**. Com o pool apertado, requisições normais de usuário passam a estourar por falta de
   conexão — uma mensagem lenta consegue degradar a tela inteira.

## O que fazer

**Separe em três passos, cada um com sua transação curta, e a rede fora de todas elas:**

1. **Reservar e commitar.** Uma transação curta marca as linhas como em processamento e devolve os
   dados necessários para o envio. Ela termina antes de qualquer chamada externa.
2. **Enviar, fora de transação.** Nenhuma conexão de banco fica presa enquanto se fala com o
   provedor.
3. **Gravar o resultado**, em transação curta por resultado (ou em lote pequeno) — publicado,
   reagendado com backoff, ou esgotado.

Regras que não podem se perder no caminho:

- **A reserva continua sendo o que impede duas instâncias de enviarem a mesma mensagem.** Hoje quem
  garante isso é o `FOR UPDATE SKIP LOCKED` mantido pela transação longa. Com a transação curta, a
  reserva precisa ficar **persistida** — uma marca na linha, com instante — e o publicador só pega o
  que não está reservado ou cuja reserva expirou. Descreva no relatório como você garantiu isso.
- **Reserva órfã tem que voltar.** Se o processo morrer entre reservar e gravar o resultado, a linha
  não pode ficar reservada para sempre. Defina um tempo de expiração da reserva, configurável, e
  documente o número no `application.yml` como o resto do projeto faz — nenhum número no código.
- **Nenhuma mensagem pode ser enviada duas vezes.** Reenvio duplicado é pior que atraso: o cliente
  recebe a mesma coisa duas vezes no WhatsApp. Se houver dúvida entre reenviar e não reenviar,
  **não reenvie** e deixe o alarme gritar.
- O comportamento de `esgotar` continua idêntico: a linha **não** é apagada, `mensagem.status_entrega`
  vai para `FALHOU`, o evento de mudança de status é publicado e o `[ALERTA_OUTBOX_ESGOTADA]`
  continua saindo.
- `ContextoDeServico` continua envolvendo tudo; sem ele o RLS nega e a outbox parece vazia (foi o bug
  da E07b — não o reintroduza de outra forma).

**Uma linha lenta não pode ser a fila inteira.** Depois da separação, ou envie o lote em paralelo com
um limite de concorrência configurável, ou reduza o lote. Diga qual escolheu e por quê. Se paralelizar,
o limite entra no `application.yml`.

## O que não fazer

- Não aumente o `WHATSAPP_TIMEOUT` para "resolver". O timeout está certo; o que está errado é o que
  ele bloqueia.
- Não troque `fixedDelay` por `fixedRate` sem separar a transação: isso só empilha rodadas
  concorrentes sobre o mesmo problema.
- Não mexa no `PublicadorDeRepasseWebhook` (o repasse ao n8n). Mesmo padrão, outro dono, outra etapa.

---

# Bloco 2 — Leitura por usuário

## O defeito

```sql
UPDATE atendimento
   SET lido_ate = GREATEST(COALESCE(lido_ate, 'epoch'::timestamptz), ?)
 WHERE id = ? AND atendente_id = ?
```

O `AND atendente_id = ?` é deliberado — a V25 diz "leitura por gestor nao altera", para que um gestor
espiando não limpe a fila do dono. Mas isso significa que **quem não é o responsável vê um contador
que não tem como zerar**: gestor, subgestor, administrador, e qualquer conversa que esteja com a IA
(`atendente_id` nulo). O `UPDATE` afeta zero linhas e o ponto azul fica lá para sempre.

Existe um segundo furo no mesmo lugar: a leitura só é marcada **ao abrir**. Mensagem que chega
enquanto a conversa já está aberta na tela reacende o contador.

## O que fazer

**A leitura passa a ser por usuário.** Nova migration (`V41`), tabela estreita:

- chave primária composta por atendimento e usuário, `lido_ate` timestamptz;
- RLS coerente com o resto do schema — cada um enxerga e escreve **a própria linha**;
- backfill da coluna `atendimento.lido_ate` existente para a linha do responsável atual, para ninguém
  acordar com a fila cheia;
- `atendimento.lido_ate` **fica onde está** nesta etapa. Remover coluna é outra conversa; deixe de
  escrever nela e diga no relatório que ficou órfã.

Com isso, a regra da V25 é preservada **por construção**: a leitura do gestor grava na linha do
gestor e não encosta na do dono. `MarcarAtendimentoComoLidoUseCase` passa a gravar sempre, para
qualquer papel, sem a condição de propriedade.

A contagem de não lidas do cartão passa a ser relativa a **quem está pedindo a lista** —
`PainelDeAtendimentosRepositorioJdbc`. Cuidado com o `LEFT JOIN`: quem nunca abriu a conversa não tem
linha, e isso significa "tudo não lido", não "zero não lidas".

**E a leitura precisa avançar com a conversa aberta.** Quando chega mensagem nova e a conversa está
aberta na tela daquele usuário, o `lido_ate` dele avança. Resolva isso no frontend, no mesmo ponto que
já trata o evento de mensagem nova — sem inventar rota: a que existe já serve.

## O que não fazer

- Não remova `atendimento.lido_ate` nesta etapa.
- Não mude quem **vê** o quê. Visibilidade é RN-CRM-01 e não é assunto aqui; muda só quem consegue
  **marcar como lido**.

---

## Verificação

**Bloco 1**

- `./mvnw clean verify` no reator inteiro, verde.
- Teste de que uma mensagem cujo envio demora não atrasa a mensagem seguinte da mesma rodada.
- Teste de que nenhuma conexão de banco fica aberta durante a chamada ao provedor — prove pelo
  desenho do código no relatório se não der para testar diretamente.
- Teste de que reserva expirada volta para a fila e é enviada **uma** vez.
- Teste de que recusa temporária ainda reagenda com backoff, e permanente ainda esgota, publicando
  `FALHOU` e o alarme.

**Bloco 2**

- Teste de que **administrador** abrindo uma conversa de outro zera o contador **dele** e **não** zera
  o do responsável.
- Teste de que o responsável abrindo zera o dele.
- Teste de que conversa com a IA (`atendente_id` nulo) pode ser marcada como lida.
- Teste de que quem nunca abriu vê todas como não lidas, e não zero.
- Teste de que mensagem nova com a conversa aberta não reacende o contador daquele usuário.
- Migration roda em base com dados: prove com o Testcontainers que o backfill preserva o estado atual
  do responsável.

## Relatório

1. Como a reserva ficou persistida e como reserva órfã expira.
2. Se você paralelizou o lote ou reduziu, e o número escolhido.
3. Que `atendimento.lido_ate` ficou órfã e em que etapa você sugere removê-la.
