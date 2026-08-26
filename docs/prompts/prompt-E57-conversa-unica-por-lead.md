# Prompt E57 — uma conversa por cliente, não uma por atendimento

> Leia `AGENTS.md`, `CLAUDE.md`, `docs/22-bugs-abertos-26-08.md` (bug 7) e `docs/13-estado-do-projeto.md`.
> **Pode commitar localmente a qualquer momento.** Não execute `git push` sem autorização explícita.
> **Rode por último.** Esta etapa reescreve a lista e o histórico; as E54 e E56 precisam já estar
> fechadas, senão você resolve conflito em vez de resolver o problema.

---

## Contexto

Hoje a lista de conversas é `key={cartao.atendimentoId}`: uma linha por atendimento. O mesmo cliente
aparece várias vezes. É o desenho desde a E11, não um acidente.

**Já foi confirmado que não há lead duplicado.** A consulta de nomes repetidos voltou vazia: as linhas
repetidas na tela são o **mesmo lead com vários atendimentos**. Não existe dado sujo para reconciliar,
nem falha de canonicalização de telefone. Não invente migration de dados nesta etapa.

O que se quer é o comportamento do WhatsApp: **uma conversa por cliente**, com os atendimentos virando
seções dentro dela, separadas por data e pelo encerramento.

## Bloco 0 — O que continua sendo o atendimento

Isto é o mais importante desta etapa. **A unidade de negócio continua sendo o atendimento.** Ele é a
unidade de comissão (RN-CRM-06), de transferência, de finalização, de leitura e da assinatura em tempo
real. Esta etapa muda **apresentação e leitura de histórico**, não o modelo.

Se em algum momento parecer necessário mudar `mensagem.atendimento_id`, fundir atendimentos ou criar
uma entidade "conversa" no banco, **pare e relate**. Não é isso.

## Bloco 1 — A lista agrupa por cliente

- Cada linha passa a ser **um lead**, não um atendimento.
- O que a linha mostra — última mensagem, horário, não lidas, responsável, etapa — é do **atendimento
  mais recente** daquele lead. Não some contadores de atendimentos diferentes sem dizer no relatório
  que somou e por quê.
- As quatro visões (`TODOS`, `ATIVOS`, `PENDENTES`, `POTENCIAIS`) e as contagens continuam
  significando o que significam hoje. Um lead entra na visão se **algum** atendimento dele entra —
  confirme como as contagens do backend se comportam com isso e ajuste para que o número do selo bata
  com o número de linhas exibidas. Selo que não bate com a lista é pior que selo nenhum.
- A visibilidade continua sendo a que o servidor devolve. **Esta tela nunca amplia o recorte recebido
  da API** — se o agrupamento fizer aparecer um lead que o atendente não veria, você quebrou a
  RN-CRM-01.

Prefira resolver o agrupamento **no servidor**, não no cliente: agrupar no front depois de paginar
produz página com número variável de linhas e contagem que não fecha. Se decidir agrupar no front,
justifique no relatório.

## Bloco 2 — O histórico é contínuo, com marcos

Ao abrir a conversa, o painel mostra as mensagens de **todos os atendimentos daquele lead**, em ordem
cronológica, com:

- o separador de data que já existe;
- **um marco visível a cada troca de atendimento**, dizendo que um atendimento terminou e outro
  começou, com a data. A `LinhaDeInicio` que já existe é o ponto de partida — hoje ela aparece só no
  índice 0.
- A paginação por cursor continua. Ela passa a atravessar atendimentos, o que significa que o cursor
  não pode mais assumir um único `atendimento_id`. Resolva isso **no repositório**, com uma consulta
  que ordene por instante e desempate de forma determinística — não monte a página no cliente
  juntando várias chamadas.

Continua valendo o que a etapa da Automação já decidiu: histórico **não** vira contrato interno novo.
Nada disso encosta em `/internal/v1`.

## Bloco 3 — Tempo real: assina só o que está vivo

A assinatura é `/user/queue/atendimento.{id}`, por atendimento. Com a conversa unificada:

- o front assina **apenas o atendimento em aberto** daquele lead. Atendimento finalizado é histórico e
  não recebe mensagem nova;
- se o lead **não tem** atendimento aberto (todos finalizados), a conversa abre em modo leitura, sem
  assinatura, e o composer se comporta como já se comporta hoje com `status === "FINALIZADO"`;
- quando a IA abre um atendimento novo para aquele lead, a tela precisa passar a assinar o novo sem
  recarregar a página. Diga no relatório como você detectou essa transição.

**Uma consequência que precisa ficar explícita na sua cabeça e no código:** o composer, o painel da
direita, a transferência, a finalização e a leitura passam a operar sobre **"o atendimento aberto
deste lead"**, não sobre "o atendimento que eu cliquei". Deixe isso nomeado no código — uma variável
chamada `atendimentoAtivo`, não `conversa.atendimentoId` reaproveitado com outro sentido.

## Bloco 4 — Leitura e não lidas

A E55 tornou a leitura por usuário. Aqui, o contador da linha é do **lead**, então precisa somar as
não lidas dos atendimentos daquele lead para aquele usuário. Abrir a conversa marca como lido **o
atendimento aberto**; os finalizados já estavam lidos ou nunca mais mudam.

Se a E55 ainda não tiver entrado quando você rodar, **pare e relate** — não reimplemente leitura aqui.

## Bloco 5 — O que não pode regredir

- RN-CRM-01: nenhum atendente passa a ver lead que não via.
- RN-CRM-06: mandar mensagem manual continua mudando o responsável **do atendimento aberto**.
- Transferência, finalização, `#reset` e o contrato `/internal/v1` seguem intactos.
- Os testes existentes de `lista-conversas`, `cartao-conversa`, `painel-da-conversa` e
  `lista-mensagens` vão quebrar. Atualize **no mesmo commit**.

---

## Verificação

- `./mvnw clean verify` no reator inteiro e `npm run lint && npm run typecheck && npm test` no
  `frontend/`, verdes.
- Teste de que um lead com três atendimentos aparece **uma vez** na lista.
- Teste de que o selo de cada visão bate com o número de linhas exibidas.
- Teste de que o histórico traz mensagens dos três atendimentos em ordem, com marco entre eles.
- Teste de que a paginação atravessa a fronteira de atendimento sem repetir nem pular mensagem.
- Teste de que a assinatura em tempo real é feita só para o atendimento aberto.
- Teste de que lead sem atendimento aberto abre em leitura, sem composer ativo.
- Teste de que um atendente **não** passa a enxergar lead de outro por causa do agrupamento.

## Relatório

1. Se agrupou no servidor ou no cliente, e por quê.
2. Como o cursor do histórico atravessa atendimentos e como desempata.
3. Como a tela detecta que a IA abriu um atendimento novo para o lead aberto.
4. Se alguma contagem precisou somar entre atendimentos.
