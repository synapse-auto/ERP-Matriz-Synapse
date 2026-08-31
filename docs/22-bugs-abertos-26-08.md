# 22. Bugs abertos — 26/08

> **Registro encerrado em 30/08/2026.** Os nove itens da triagem foram incorporados à
> `origin/main` por meio das etapas posteriores. Este arquivo permanece datado e histórico;
> não é um painel vivo de bugs. Pendências novas devem entrar em uma triagem própria, com
> data e evidência.

Triagem feita lendo o código, não as telas. **Os nove têm causa confirmada na linha.** A dúvida do
#7 foi resolvida pela consulta ao banco: não há lead duplicado.

> **Estado em 26/08, fim do dia.** Naquele momento os nove estavam corrigidos em commits
> locais ainda **não empurrados** (`0d467a2` E53, `c8d3da4` E54, `20c62f2` + `4bf3d76` E55,
> `40e8e2f` E57, `0987732` + `8d59cbe` E56/E58). Depois, esse conjunto entrou na `main`.
> As descrições abaixo continuam descrevendo o **defeito original**, para quem for ler o histórico —
> elas não descrevem mais o código atual. Onde o comportamento mudou, há uma nota "corrigido em".

| # | Bug | Causa | Onde | Gravidade |
|---|---|---|---|---|
| 1 | Mensagem duplicada ao enviar | **corrigido** `c8d3da4` | frontend | alta |
| 2 | Bolhas sobrepostas ("mensagens juntas") | **corrigido** `c8d3da4` | frontend | alta |
| 3 | Transferência entrega o lead para quem não pode recebê-lo | **corrigido** `0d467a2` | backend | **crítica** |
| 4 | Transferência não atualiza a conversa aberta | **corrigido** `c8d3da4` | frontend | alta |
| 5 | Pontos azuis não somem | **corrigido** `4bf3d76` (V41) | backend | média |
| 6 | "+ + Tag" | **corrigido** `0987732` | frontend | baixa |
| 7 | Um chat por atendimento | **corrigido** `40e8e2f` | frontend + backend | alta |
| 8 | Campo de texto estoura o composer | **corrigido** `8d59cbe` (base `0987732`) | frontend | média |
| 9 | Mensagem demora ~30s para sair | **corrigido** `20c62f2` | backend | alta |

---

## 1 e 2 têm a mesma origem: a corrida entre o otimismo e o WebSocket

`useEnviarMensagem` insere a mensagem no cache com um id temporário (`temp-…`). O backend responde e
o `onSuccess` **troca** esse id pelo id real. Só que o evento do WebSocket com a mesma mensagem
chega **antes** da resposta HTTP — o relay publica no commit, a resposta ainda está voltando pela
rede. A sequência real é:

1. `onMutate` → entra `temp-abc`
2. WebSocket → `mesclarMensagens` insere `real-123` (não conhece `temp-abc`)
3. `onSuccess` → troca `temp-abc` por `real-123`

No fim há **dois itens com o id `real-123`** no array. `mesclarMensagens` deduplica por id, mas o
`onSuccess` não passa por ela — ele faz um `map` que substitui em posição, sem deduplicar.

Isso explica exatamente o que você viu: duas bolhas iguais, uma **com** o nome "Lucas Rezende" e
outra **sem**. A que veio do WebSocket tem `remetenteId`, então `nomeDaAutoria` resolve o nome; a que
veio do `onSuccess` tem `remetenteId: null` e `remetenteNome: null` — o código monta o objeto à mão e
não copia esses campos.

E as duas bolhas viram **a mesma `key` do React**. `ListaMensagens` usa `key={mensagem.id}` dentro de
uma lista virtualizada com itens em `position:absolute`. Com chave repetida o React reaproveita o nó
errado e os `translateY` se sobrepõem — as "mensagens juntas".

Há um segundo caminho para a sobreposição, independente do primeiro, e ele também é real:
`useVirtualizer` está sem `getItemKey`. Sem isso o cache de alturas medidas é indexado por **posição**,
não por mensagem. Como `onCarregarMais` **insere páginas antigas no topo**, todos os índices andam e
todas as alturas guardadas passam a valer para a mensagem errada. Some a isso `estimateSize: () => 64`
para uma bolha de uma linha (~44px) e a separação de data renderizada dentro do item medido.

**Correção:** deduplicar por id no `onSuccess` (ou fazê-lo passar por `mesclarMensagens`), preservar
`remetenteId`/`remetenteNome` na reconciliação, e dar `getItemKey: (i) => filtradas[i].id` ao
virtualizador.

## 3. A transferência humana não valida o destino — essa é a mais grave

`TransferirAtendimentoUseCase.executar` grava `atendente_id = paraAtendenteId` e chama
`leads.transferirPara(...)` **sem verificar nada** sobre o destino. Não checa se o usuário existe, se
está ativo, nem se o papel é `ATENDENTE`.

O caminho da Automação valida tudo isso — existe `AtendenteParaTransferenciaRepositorio` e
`AtendenteDestinoInvalidoException` justamente para isso, e o `executarPelaAutomacao` os usa. O
caminho humano não injeta nenhum dos dois.

O efeito prático é sério por causa da RN-CRM-01. A visibilidade do atendente é "meus leads + os sem
dono". Se o lead vai para um gestor, um administrador ou um usuário desativado, ele deixa de ser
"sem dono" e não pertence a nenhum atendente: **some da lista de todo mundo**. Não dá erro, não
avisa; o lead simplesmente evapora do fluxo comercial. E a comissão passa a contar para quem não
vende.

O `DialogoTransferir` piora isso: ele oferece "Assumir para mim" para **qualquer** usuário que se
encontre pelo e-mail, inclusive gestor e administrador. Você, como ADMINISTRADOR, consegue clicar e o
backend aceita.

**Correção:** validar no caso de uso, exatamente como a Automação já valida — destino precisa existir,
estar ativo e ter papel `ATENDENTE`; caso contrário `422`. E o diálogo só oferece "Assumir para mim"
quando quem está logado é `ATENDENTE`.

## 4. A conversa aberta não acompanha a transferência

`PaginaAtendimentosCliente` guarda a conversa aberta num `useState` com o `CartaoAtendimento` que foi
clicado. `useTransferirAtendimento.onSuccess` invalida a lista (`["atendimentos"]`) e **não toca nesse
estado**. Resultado: a lista à esquerda atualiza, a conversa aberta continua com o dono antigo — e ela
alimenta o cabeçalho, o painel da direita (`responsavelNome`), o composer e a autoria das mensagens
(`atendenteId`/`atendenteNome` vão para `nomeDaAutoria`).

Junto disso, quando quem transfere era o dono anterior, o backend publica uma revogação para ele — a
conversa fecha sozinha e aparece "conversa encerrada". Isso é intencional, mas do lado de quem clicou
parece que a transferência quebrou a tela.

E o aviso "Transferência recebida" tem um defeito próprio: clicar em "abrir" faz
`setLeadParaAbrir(leadId)`. Se for o mesmo lead de antes, o valor não muda, o `useEffect` não dispara
e **nada acontece**.

**Correção:** derivar a conversa aberta da lista pelo `atendimentoId` em vez de guardar uma cópia, e
usar um gatilho que mude sempre (não o próprio id do lead) para o "abrir".

## 5. Os pontos azuis não somem porque você é administrador

O SQL é este:

```sql
UPDATE atendimento
   SET lido_ate = GREATEST(COALESCE(lido_ate, 'epoch'::timestamptz), ?)
 WHERE id = ? AND atendente_id = ?
```

O `AND atendente_id = ?` é deliberado — está escrito na V25: "leitura por gestor nao altera", para
que um gestor espiando a conversa não limpe a fila do dono. Só que você está logado como
**ADMINISTRADOR** e a maioria dessas conversas ou é de outra pessoa ou está com a IA
(`atendente_id` nulo). O `UPDATE` afeta zero linhas e o contador nunca zera.

Ou seja: a regra está certa, o que está errado é **mostrar o badge para quem não tem como zerá-lo**.

Existe um segundo furo no mesmo lugar: a leitura só é marcada **ao abrir**. Mensagem que chega
enquanto a conversa já está aberta na tela reacende o contador, porque nada avança o `lido_ate` depois
da abertura.

**Corrigido em `4bf3d76`.** A V41 criou `atendimento_leitura` (chave composta atendimento+usuário,
RLS `usuario_id = app_usuario_id()`), com backfill do responsável atual. A regra da V25 passou a ser
preservada por construção: cada um grava a própria linha. `atendimento.lido_ate` ficou como legado,
não é mais escrita, e deve sair numa migration futura.

## 6. "+ + Tag"

`AtalhoTags`, modo painel, renderiza o ícone `<Plus/>` **e** o texto do catálogo, que já é "+ Tag".
Tira um dos dois.

## 7. Um chat por atendimento — é o desenho, mas os dados **não** estavam sãos

> **Correção de 31/08 (E111).** A conclusão abaixo estava errada, e o motivo é instrutivo: a
> verificação foi feita **por nome**, e a consulta por telefone foi dispensada com o argumento de
> que "só mudaria a resposta se dois leads tivessem nomes diferentes". Era exatamente esse o caso.
> Rodada de fato, `SELECT right(telefone, 8), count(*) FROM lead GROUP BY 1 HAVING count(*) > 1`
> devolveu **23 pares** — o mesmo número com e sem o nono dígito, sob nomes diferentes
> (`{5561981536371, 556181536371}` = "Jair real 1814" / "Adjair"). A hipótese do nono dígito estava
> **confirmada**, não descartada, e é a causa do "não consigo puxar o cliente" da E105 Parte 1.
> Corrigida pela V50 (fusão dos pares) mais a regra do nono dígito em `TelefoneCanonico`.
>
> A lição do falso negativo: verificação por atributo escolhido pelo humano (`nome`) não substitui
> verificação pela chave que o sistema usa (`telefone`).

A consulta de leads com nome repetido voltou **zero linhas**. Como a lista mostra "Lucas Rezende"
duas vezes e nenhum nome existe em dois leads, as duas linhas são **o mesmo lead com dois
atendimentos** — o que continua valendo para o caso do "Lucas Rezende" na tela.

Então o que resta é o desenho: a lista é `key={cartao.atendimentoId}`, uma linha por atendimento,
como está desde a E11. Você quer o comportamento do WhatsApp — uma conversa por número, com os
atendimentos virando seções separadas por data e por encerramento. Concordo, e agora dá para fazer
com segurança, porque é só apresentação: o `atendimento` continua sendo a unidade de comissão, de
transferência e de assinatura do WebSocket. O que muda é que a lista agrupa por `lead_id` e o
histórico da conversa aberta passa a unir os atendimentos daquele lead, com um separador em cada
troca.

Um detalhe que precisa de decisão sua: a assinatura em tempo real é por atendimento
(`/user/queue/atendimento.{id}`). Com a conversa unificada, o front assina **só o atendimento em
aberto** — os encerrados são histórico e não recebem mensagem nova. É o comportamento certo, mas
significa que o painel da direita e o composer passam a olhar para "o atendimento aberto deste lead",
não para "o atendimento que eu cliquei".

## 8. O campo de texto estoura o composer — e o mesmo defeito está em todo o CRM

Confirmado, e são três coisas somadas:

1. **`field-sizing-content` no `Textarea` base** (`components/ui/textarea.tsx`). Esse recurso
   dimensiona o campo pelo conteúdo — e faz isso **nos dois eixos**, não só na altura. Uma palavra
   sem espaço ("kkkk…") tem largura intrínseca ilimitada, então o campo cresce para o lado.
2. **`flex-1` sem `min-w-0`** no `<div className="relative flex-1">` que envolve o campo no composer.
   Item de flex nasce com `min-width: auto`, ou seja, **se recusa a encolher abaixo da largura mínima
   do conteúdo**. Com a palavra gigante, essa mínima é enorme e o div empurra a linha inteira.
3. O composer sobrescreve **altura** (`min-h-11 max-h-32`) e nunca largura — não há `w-full`,
   `min-w-0` nem quebra forçada.

Por isso o campo escapa da borda arredondada e a linha do composer transborda.

**Isso não é só do composer.** O `field-sizing-content` está na classe base, então qualquer textarea
do sistema tem o mesmo comportamento: a mensagem de follow-up e de fidelização na Automação, as notas,
as mensagens rápidas. Ninguém tinha digitado uma palavra longa sem espaço até agora.

**Correção:** no `Textarea` base, restringir o `field-sizing` à altura (ou trocar por
`field-sizing-fixed` e manter o crescimento por `rows`), e garantir `w-full` + `min-w-0` no campo e no
wrapper do composer, com `break-words` para o texto digitado. A bolha da mensagem já está certa
(`max-w-[70%] break-words`) — o problema é só o campo de entrada.

## 9. Os 30 segundos não são a sua internet

Isso dá para afirmar por construção, antes de qualquer medição: **a requisição HTTP que envia a
mensagem não fala com a Meta**. Ela grava a mensagem e uma linha na `outbox_evento` e devolve. Quem
conversa com o WhatsApp é um job agendado, depois e fora do caminho do usuário — foi feito assim de
propósito, para o atendente ter resposta de tela em milissegundos mesmo com o provedor lento. Sua
internet influencia esses milissegundos e mais nada. Os 30 segundos são inteiramente do lado de cá.

E o drenador tem duas características que se somam mal:

**A rodada inteira é uma transação só, e a chamada de rede acontece dentro dela.**
`PublicadorDaOutboxOperacoes.rodada()` reserva até `lote = 50` linhas com `FOR UPDATE SKIP LOCKED` e
percorre **em sequência**, chamando a Meta uma a uma. O timeout por chamada é `WHATSAPP_TIMEOUT: 10s`.
Uma mensagem para um número problemático segura **todas as que estão atrás dela na mesma rodada** por
até dez segundos. Três dessas e você tem os seus trinta.

**O `@Scheduled` é `fixedDelay`.** A próxima rodada só começa 1 segundo depois que a anterior
**termina**. Enquanto uma rodada está pendurada num timeout, nada mais sai.

Há ainda o caminho da recusa temporária: `OUTBOX_BACKOFF_INICIAL: 5s`, dobrando. Uma mensagem que
falha duas vezes e passa na terceira sai 5 + 10 + 20 ≈ 35 segundos depois do clique — sem erro
nenhum na tela, porque no fim deu certo.

**A consulta que decide qual dos dois é:**

```sql
SELECT id,
       criado_em,
       publicado_em,
       publicado_em - criado_em AS latencia,
       tentativas,
       ultimo_erro
  FROM outbox_evento
 WHERE criado_em > now() - interval '2 days'
 ORDER BY (publicado_em - criado_em) DESC NULLS FIRST
 LIMIT 20;
```

- `tentativas > 0` com `ultimo_erro` preenchido → é recusa e backoff. O erro diz o que a Meta
  respondeu, e o conserto é lá.
- `tentativas = 0` e latência alta → é a rodada serializada segurando a fila. O conserto é aqui.

**Um risco que apareceu junto e é mais sério que o sintoma.** Essa transação segura uma conexão do
pool do chat durante a rodada inteira — potencialmente dezenas de segundos. O
`SYNAPSE_DB_POOL_CHAT_TIMEOUT_MS` está em **3000**. Se a rodada travar num timeout com o pool
apertado, requisições normais de usuário passam a estourar em 3 segundos por falta de conexão. Ou
seja: uma mensagem lenta pode derrubar a responsividade da tela inteira. Isso precisa ser corrigido
mesmo que o atraso de 30s não incomodasse.

**Corrigido em `20c62f2`.** A reserva virou lease persistido (`proxima_tentativa_em` empurrado para
o futuro na mesma transação curta do `SELECT ... FOR UPDATE SKIP LOCKED`), o envio saiu de qualquer
transação, o resultado entra numa segunda transação curta, e o lote vai em paralelo com concorrência
configurável (`OUTBOX_CONCORRENCIA`, padrão 4; `OUTBOX_RESERVA_EXPIRACAO`, padrão 30s contra timeout
de 10s do provedor).

**Risco residual, ainda aberto:** se o processo morrer ou a transação de resultado falhar **depois**
que a Meta aceitou, nada marca que houve despacho — `tentativas` só é incrementado no caminho da
recusa. Passados os 30s do lease, a linha volta elegível e a mensagem **sai de novo**. A janela é de
milissegundos em operação normal, mas todo deploy mata o container, e é aí que ela abre. O conserto é
uma coluna `despachado_em`, gravada antes da chamada; na volta do lease, `despachado_em` preenchido e
`publicado_em` nulo significa **não reenviar** — marcar para inspeção e alarmar. A API da Meta não
aceita chave de idempotência do cliente para mensagens, então a garantia tem de ser nossa.

---

## Ajustes finos (não são bugs, são pedidos)

Anotados aqui para não se perderem, com o que já verifiquei de cada um:

- **Botões de adicionar, editar e remover em Lembretes e Mensagens Programadas no painel da
  conversa.** Hoje `SecaoDeLembretes` e `SecaoDeProgramadas` em `painel-da-conversa.tsx` são listas
  puramente de leitura. Os endpoints de CRUD já existem (são os mesmos das telas de Lembretes e
  Mensagens Programadas), então é trabalho só de tela.
- **Finalizar todos os atendimentos pelo menu de três pontinhos.** Não existe rota de finalização em
  lote — hoje é `POST /atendimentos/{id}/finalizar`, um por vez. Precisa decidir se o botão dispara N
  chamadas do front ou se ganha uma rota própria; com muitos atendimentos abertos, N chamadas é
  ruim. E precisa de confirmação, porque finalizar é irreversível (`FINALIZADO` é estado terminal:
  dele não se sai).
- **Trocar o ícone do Dashboard para a seta de métrica.** Uma linha na sidebar.

---

# Por que tantos bugs

Sem rodeio, quatro razões, e a última é minha.

**1. Todos os bugs de comportamento estão na mesma tela, e não é coincidência.** Atendimentos é a
única tela do CRM com **quatro fontes de verdade simultâneas** sobre o mesmo dado: o cache paginado
por cursor, o WebSocket, a atualização otimista e o backfill de reconexão. Todo o resto do sistema é
requisição-resposta, uma fonte só. Bug de concorrência só nasce onde há concorrência — e ela está
concentrada aqui. Os bugs 1, 2 e 4 são exatamente isso: três componentes que discordam sobre quem é o
dono do estado.

**2. A homologação foi o primeiro lugar com latência de verdade.** A corrida entre o `onSuccess` e o
WebSocket **não pode acontecer** em teste: no Vitest a promessa resolve no mesmo tick, o WebSocket é
um dublê e a ordem é sempre a mesma. Ela precisa de rede real, banco real e duas pessoas. Foi
aparecer o WhatsApp de verdade e ela apareceu junto.

**3. O CI verifica unidades; esses bugs moram nas costuras.** Temos 339 testes de integração no
backend e testes em quase todo componente do front, e todos passam — porque cada peça está certa
sozinha. `mesclarMensagens` deduplica corretamente. `onSuccess` reconcilia corretamente. O defeito é
que uma não conhece a outra. Nenhuma suíte que temos abre duas abas e manda uma mensagem.

**4. O ritmo cobrou.** Da E40 à E51 foram doze etapas em pouco mais de três dias, várias de
madrugada, com data de entrega em cima. Etapa curta com CI verde dá a sensação de fechado, e eu
tratei "CI verde" como prova de que o comportamento estava certo em coisas que o CI nunca exerceu.
Isso é meu, como revisor: eu conferi que o agente fez o que o prompt pediu, e não conferi se o prompt
pedia a coisa certa para dois usuários simultâneos.

Vale registrar o que **não** é a explicação: não é código malfeito nem descuido do agente. O bug 3 —
o mais grave — é a assimetria entre um caminho que valida (Automação) e outro que não valida
(humano), e essa assimetria nasceu porque as duas transferências foram escritas em etapas
diferentes, com semanas de distância, e ninguém voltou para comparar. É o custo de construir por
etapas: cada uma fecha bem, e a costura entre elas é que ninguém revisa.

**O que muda daqui pra frente:** antes de fechar Atendimentos, uma etapa só de reconciliação de
estado — uma fonte de verdade por dado — e um roteiro de fumaça com duas sessões abertas, que hoje
não existe.
