# E112 — a conversa única depois da fusão, e uma só regra de telefone

## Ponto de partida

Branch `fix/telefone-nono-digito`, commit `2a8a215` (E111). Ela está pronta e **não pode subir
como está**. `origin/main` já é `d3ffd4f`, que traz a E105 — e a E111 saiu de `ebe1c74`, sem ela.

Comece por `git merge origin/main`. O único conflito é `docker/provisionamento/README.md`: as
duas etapas apendam uma seção no fim do arquivo. Mantenha as duas, importação (E105) antes da
simulação (E111).

Esta etapa fecha três coisas no mesmo PR. O Bloco 1 é bloqueante; os outros dois são dívida que
não pode atravessar o deploy.

---

## Bloco 1 — a V50 entrega o sobrevivente com duas conversas abertas

### O que acontece hoje

A fusão move o atendimento do perdedor para o sobrevivente:

```sql
UPDATE atendimento SET lead_id = par.sobrevivente WHERE lead_id = par.perdedor;
```

Não existe unique no banco impedindo dois atendimentos não-finalizados no mesmo lead — "conversa
única por lead" é invariante de aplicação, não do schema. Então quando os dois leads do par têm
atendimento aberto, o sobrevivente fica com dois. E a aplicação escolhe assim (V36):

```sql
CREATE OR REPLACE FUNCTION app_atendimento_aberto_do_lead(p_lead UUID) ...
    SELECT id FROM atendimento WHERE lead_id=p_lead AND status <> 'FINALIZADO'
     ORDER BY iniciado_em DESC LIMIT 1;
```

Ganha o **mais recente**. O perdedor é o lead com menos mensagens, e o atendimento dele costuma
ser o mais novo — foi criado agora, quando o atendente tentou puxar o cliente e mandou o template.

Medido em produção em 31/08: **28 pares**, e em **24 deles os dois lados têm atendimento aberto**.
Em 8, o atendimento vazio é o mais recente. O pior: o par `...98430401`, onde ficaria aberto um
atendimento de **0 mensagens** criado às 09:10, escondendo o atendimento de **146 mensagens**.

A migration fundiria o lead certo, com o dono certo e o telefone certo — e o atendente abriria uma
conversa em branco. Trocaríamos um bug que tinha explicação por um que não tem nenhuma.

A lista de 8 é um retrato de agora. `iniciado_em` não muda, mas qualquer atendente que tente puxar
um duplicado antes do deploy cria um atendimento novo e move mais um par para a coluna de perigo.
**Trate estruturalmente, não pela foto.**

### O que fazer

Na V50, depois do `UPDATE atendimento ... SET lead_id = par.sobrevivente` e antes de apagar o
perdedor: entre os atendimentos **não-finalizados** do sobrevivente, manter aberto **um só**, e
finalizar os demais.

Quem fica aberto, na ordem: **mais mensagens**, empate o de `iniciado_em` **mais antigo**, empate o
menor `id`. É a mesma regra que já decide o lead sobrevivente — quem tem a conversa fica —, aplicada
um nível abaixo.

Finalizar significa `status = 'FINALIZADO'` e `finalizado_em = now()`. **Nada é apagado**: a
mensagem de template que o atendente mandou continua no histórico, sob o mesmo cliente, num
atendimento fechado.

E encerre a participação junto: `atendimento_participante.saiu_em = now()` onde `saiu_em IS NULL`
para o atendimento que você finalizou. Sem isso o recorte da aba Todos (E106) continua trazendo o
atendimento fechado, porque ele casa pela participação aberta — e o atendente veria a conversa
vazia reaparecer na lista dele depois da fusão.

Emita `RAISE NOTICE` por atendimento finalizado, com id, contagem de mensagens e dono. Conte-os e
inclua o total na `RAISE NOTICE` final, ao lado de `fundidos` e `normalizados`.

Não aborte por causa disto: 24 dos 28 pares cairiam nesse caminho, e uma migration que sempre
aborta não é um guarda, é um deploy que nunca acontece.

---

## Bloco 2 — a simulação precisa mostrar o que vai fechar

`docker/provisionamento/simular-fusao-nono-digito.sql` é o único registro do que a V50 vai fazer, e
hoje ele não olha status de atendimento nenhum. Acrescente uma seção listando, por par:

- o atendimento que **fica aberto**: id, dono, contagem de mensagens, `iniciado_em`;
- os que serão **finalizados**: os mesmos campos.

Essa seção é a lista que a gestão aprova antes do deploy. Ela precisa ser legível por quem não lê
SQL: nome do lead, nome do atendente, número de mensagens.

---

## Bloco 3 — a E105 tem uma segunda regra de telefone, e ela contradiz a E111

`PrepararImportacaoLeadsCsv` (já na main) recusa todo número nacional de dez dígitos:

```java
/** O dominio nao inventa o nono digito. Para a importacao em massa, um numero nacional
 *  de dez digitos e recusado ... */
private void recusarSemNonoDigitoQuandoAmbiguo(String telefone) { ... }
```

A justificativa morre com a E111: o domínio **passa** a completar o nono dígito, de forma
determinística e segura — assinante de oito dígitos começando em 6–9 é celular e ganha o 9;
começando em 2–5 é fixo e fica como está. Com as duas etapas juntas, esse guard recusa:

- **fixos legítimos** (`61 3224-1234`), que o `TelefoneCanonico` trata corretamente e não toca;
- os celulares de oito dígitos que a E111 acabou de aprender a canonizar — ou seja, exatamente os
  que casam com os leads que já estão em produção.

O arquivo do cliente tem mais de 17 mil linhas. Um guard que recusa silenciosamente parte delas,
com a mensagem "confirme o nono dígito", é uma tarde de trabalho manual para a operação e uma
importação incompleta que ninguém percebe.

**Apague** o método, a chamada dele em `executar`, e os casos correspondentes de
`PrepararImportacaoLeadsCsvTest`. No lugar, testes que travam a regra nova:

- `6132241234` → aceito como `556132241234` (fixo, sem nono dígito, intacto);
- `6181536371` → aceito como `5561981536371` (celular, ganha o 9);
- entrada curta demais continua recusada, com a mensagem que já existe.

Uma regra de telefone só. A do domínio.

---

## Bloco 4 — o teste de paridade tem nome errado em três lugares

`TelefoneCanonicoParidadeIT` é citado no javadoc de `TelefoneCanonico`, num comentário da V50 e no
`COMMENT ON FUNCTION app_telefone_canonico`. **Essa classe não existe.** O teste de paridade real é
a classe aninhada `TelefoneNonoDigitoIT.Paridade`, e ele é bom — roda 18 casos nas duas
implementações. Corrija as três citações para apontar para o nome verdadeiro.

Um nome errado aqui não é cosmético: quem apagar o `TelefoneNonoDigitoIT` perde a garantia de
paridade entre Java e SQL sem que nada reclame, e é essa paridade que impede a migration de fundir
um cliente que o runtime depois normalizaria de outro jeito.

Editar a V50 é legal porque **ela nunca foi aplicada em produção**. Em máquina de desenvolvimento
onde ela já rodou, o checksum muda e o Flyway recusa: recrie o banco local, não use `repair` para
esconder isso.

---

## Bloco 5 — o que NÃO muda

- Nenhuma política RLS. `ux_lead_telefone` fica como está. Ninguém passa a enxergar o que não
  enxergava.
- A Agenda não oculta contato de ninguém — decisão do Lucas, já travada por teste na E106.
- O critério de nome na fusão fica como está: o nome do perdedor é descartado, nunca concatenado,
  e a lista da simulação é o que a operação usa para corrigir pela tela. Não invente "nome melhor".
- **Nada roda em produção nesta etapa.** A entrega é código, migration e simulação.

---

## Bloco 6 — testes obrigatórios

1. IT: par em que os dois lados têm atendimento aberto e o **vazio é o mais recente**. Depois da
   migration, `app_atendimento_aberto_do_lead(sobrevivente)` devolve o atendimento **com as
   mensagens**, e o outro está `FINALIZADO` com `finalizado_em` preenchido.
2. IT: o atendimento finalizado pela fusão mantém suas mensagens, e sua participação está encerrada
   (`saiu_em` preenchido) — o recorte da aba Todos não o traz mais.
3. IT: par em que só um lado tem atendimento aberto — nada é finalizado, o aberto continua aberto.
4. IT: par sem atendimento aberto de nenhum lado — a fusão roda e não finaliza nada.
5. Teste do CSV: fixo de dez dígitos aceito, celular de dez dígitos canonizado para treze.
6. `TelefoneNonoDigitoIT.Paridade` continua verde, com os casos de dez dígitos incluídos na tabela.
7. `./mvnw clean verify` verde, e o front também.

---

## Bloco 7 — entrega

- PR com a main já mesclada, CI verde **incluindo o job `imagens`** — CI verde sem esse job não
  significa que a imagem existe.
- No relatório: a saída da simulação rodada contra um Postgres com V1–V49 e fixtures que reproduzam
  os pares descritos aqui, incluindo o caso de 0 mensagens contra 146.
- Diga explicitamente o que ficou de fora.

## Bloco 8 — ordem de subida (não inverta)

1. Esta etapa sobe (ela carrega a E111 dentro).
2. A simulação roda contra produção e a saída é guardada.
3. A gestão aprova a lista de nomes e a lista de atendimentos que serão finalizados.
4. Deploy. A V50 funde e normaliza no start, antes de a aplicação atender.
5. **Só então** a importação dos 17 mil contatos.

Importar antes disto cria um terceiro cadastro por cliente e faz a própria V50 abortar por trio no
mesmo canônico.
