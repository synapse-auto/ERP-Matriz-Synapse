# Prompt E99 — Finalizados separados na lista e "Reativar atendimento"

> Leia `AGENTS.md` e `CLAUDE.md`. Branch própria (`feat/finalizados-e-reativar`) e PR.
> **Sem merge, sem deploy.** Toca mais de um módulo do backend e o frontend: degrau mais alto —
> `./mvnw verify` no reator **e** a suíte do frontend.
> Não deve precisar de migration. Se você achar que precisa, **pare e explique** antes de escrever uma.

---

## O pedido

Duas coisas na lista de Atendimentos:

1. Os atendimentos finalizados saem do meio da lista e vão para baixo de uma divisória escrita
   **Finalizados**, com o nome em cinza mais claro.
2. Um botão **"Reativar atendimento"** — entre aspas de propósito: **nada é reativado**. Um
   atendimento `FINALIZADO` é estado terminal e continua sendo. O botão cria um **atendimento novo**
   para o mesmo lead, pelo fluxo normal de criação.

## Bloco 0 — Antes de tudo: o cartão da lista é o LEAD, não o atendimento

Isso muda o desenho inteiro, então leia antes de planejar.

Desde a E57, `PainelDeAtendimentosRepositorioJdbc` faz
`ROW_NUMBER() OVER (PARTITION BY a.lead_id ...)` e o `agrupar()` filtra `linha_do_lead = 1`. Ou seja:
**um cartão por lead**, mostrando o atendimento mais recente daquele lead. O cartão já carrega
`atendimento_ativo_id` — o atendimento aberto do lead, ou `NULL` quando não existe nenhum.

Portanto, "cartão finalizado" **não** é `status = 'FINALIZADO'`. É **`atendimento_ativo_id IS NULL`**:
o lead não tem nenhum atendimento em aberto. Um lead pode ter o atendimento mais recente finalizado e
mesmo assim ter outro aberto — esse cartão continua em cima, não vai para os Finalizados. Existe
teste cobrindo exatamente esse caso (`pagina-atendimentos-cliente.test.tsx`,
`finalizadoComNovoAtivo`): status `FINALIZADO` com `atendimentoAtivoId` preenchido.

Confirme isso lendo o repositório antes de escrever qualquer condição. Se você usar `status` como
critério, a lista vai mentir.

## Bloco 1 — A divisória

### Só na aba "Todos"

Leia os `WHERE_*` do repositório: `ATIVOS` e `PENDENTES` exigem
`EXISTS (... status = 'EM_ATENDIMENTO' ...)` e `POTENCIAIS` exige `EM_IA`. Um lead sem atendimento
aberto não passa em nenhum dos três. **Finalizado só aparece em "Todos"** — a divisória só existe
lá, e nas outras abas não deve haver seção vazia nem cabeçalho solto. Confirme lendo.

### A ordenação é do banco, não da tela

Não agrupe no cliente. A lista é paginada por cursor, e existe um teste que fixa o contrato:
`use-atendimentos.test.tsx` — *"consome o cursor e concatena páginas sem reordenar nem duplicar"*.
Reordenar no cliente quebra esse teste, e com razão: se a tela reordena, cada página nova embaralha o
que já estava desenhado e o usuário vê cartões pulando de lugar enquanto rola.

O certo é a ordem já sair pronta do banco: **primeiro os leads com atendimento aberto, depois os sem**,
e dentro de cada grupo a ordem atual (`ultima_mensagem_em DESC`, desempate por id).

Consequência que você **precisa** tratar, não descobrir no meio: o cursor de `listarPaginado` hoje é
`(ultima_mensagem_em, atendimento_id)`. Com uma chave de ordenação nova na frente, o cursor tem que
carregar as três, senão a segunda página volta para o começo do outro grupo. O cursor é **opaco** e
codificado/decodificado no controller (mesmo padrão do histórico de mensagens, que faz Base64 de
`enviadoEm|id`) — então dá para estender sem quebrar contrato externo, desde que codificar e
decodificar mudem juntos. Cursor antigo chegando de uma aba já aberta não pode derrubar a requisição.

Faça o mesmo em `listar` e em `contar`: a ordem e o filtro nunca podem ser escritos em dois lugares
com decisões diferentes — é o motivo pelo qual os `WHERE_*` já estão isolados em constantes.

### A divisória em si

Cabeçalho discreto dentro da lista, com o texto **Finalizados**, aparecendo **só quando existe pelo
menos um cartão abaixo dele**. Sem contador — o total só seria conhecido depois de carregar tudo, e
número errado é pior que número nenhum. O texto entra nos textos da instância como o resto da tela,
não cravado no componente.

## Bloco 2 — O tom cinza

O nome do lead nos cartões finalizados fica em cinza mais claro. Use o token de texto suave que a
tela já tem (`text-muted-foreground` e afins) — **não** invente cor nova nem hex cravado, e confira
que o resultado continua legível nos dois temas. O resto do cartão (foto, prévia da última mensagem,
horário) mantém o tratamento de hoje: o pedido é distinguir, não apagar.

## Bloco 3 — "Reativar atendimento"

### Já existe o caso de uso certo. Não escreva um segundo.

`IniciarNovoContatoUseCase` **já faz exatamente isto**, e já trata o caso do lead existir:

- `leads.visivelPorTelefone(telefone)` — se o lead já existe, reaproveita em vez de criar;
- `leads.transferirPara(leadId, quemPediu)` — transfere para quem pediu;
- sem primeira mensagem, abre o atendimento com `Atendimento.abrirComIa(...).transferirPara(quemPediu)`
  e devolve, deixando o composer oferecer os templates;
- e **recusa texto livre fora da janela de 24h** (`canal.aceitaTextoLivre`), com
  `ForaDaJanelaException`.

Sua tarefa é dar a esse caso de uso uma **entrada por lead existente** (o botão tem o `leadId`, não
tem telefone digitado), reaproveitando o mesmo caminho. **Não duplique** a lógica de janela, de
transferência nem de abertura do atendimento em uma classe nova — se as duas entradas divergirem um
dia, a divergência vai aparecer como mensagem que não sai para o cliente.

**O nome no código não é "reativar".** O botão na tela diz "Reativar atendimento" porque é o que o
usuário entende; o código tem que dizer o que realmente acontece — abrir um atendimento novo para um
lead existente. Escolha um nome nessa linha e explique no relatório.

### Decisões já tomadas — não reabra

| Questão | Decisão |
| --- | --- |
| Quem fica dono do lead | **Quem clicou.** É o que `transferirPara(leadId, quemPediu)` já faz, e é a RN-CRM-06: ação manual transfere o lead para quem agiu. Nenhuma regra nova. |
| Modo do atendimento novo | **Humano, com quem clicou.** Nada de nascer em `EM_IA`: ninguém mandou mensagem, a IA não teria a que responder, e o atendimento ficaria parado em Potenciais. |
| O `FINALIZADO` antigo | **Intocado.** Estado terminal, como diz o próprio enum: *"dele nao se sai"*. O histórico dele continua legível. |

### A janela de 24h é o coração disso, não um detalhe

Um atendimento finalizado quase sempre é conversa velha. Se a última mensagem **do cliente** passou
de 24h, a Meta não aceita texto livre — só template pré-aprovado. O caminho sem primeira mensagem já
resolve isso: abre o atendimento e deixa o composer oferecer os templates, que é o comportamento que
já existe hoje para contato novo.

Então **o botão não manda mensagem nenhuma.** Ele abre o atendimento novo e leva o atendente para a
conversa; quem escreve é o atendente, com as regras de janela que o composer já aplica. Não invente
mensagem automática de reabertura.

### Onde o botão fica

Em `cabecalho-conversa.tsx` existe `const finalizado = conversa.status === "FINALIZADO"` e um bloco
que hoje só mostra o nome do atendente quando finalizado. É ali. O `composer.tsx` já bloqueia a
escrita em conversa finalizada — mantenha esse bloqueio; ele é o que torna o botão necessário.

Depois de criar, a tela precisa ir para o atendimento novo. O aplicativo **já sabe** seguir
`atendimentoAtivoId` de um cartão finalizado para o atendimento ativo — veja o teste
`finalizadoComNovoAtivo`. Reaproveite esse caminho em vez de inventar navegação.

### Quem pode

`isAuthenticated()`, como o `IniciarNovoContatoUseCase` de hoje. Não invente papel novo: a RLS já
decide quem enxerga o lead finalizado, e quem não enxerga não recebe o cartão. Confirme que um
atendente que não enxerga o lead recebe o mesmo 404 de "não existe", nunca 403 — vazar a existência
do lead de outro é o que a RN-CRM-01 evita.

## Bloco 4 — O que NÃO pode mudar

- `StatusAtendimento.FINALIZADO` continua terminal. Nenhuma transição nova saindo dele.
- `FinalizarAtendimentoUseCase` continua igual, inclusive `avaliacao.preparar` (CSAT) e
  `leads.marcarStatus(..., FINALIZADO)`.
- Nada de `/internal/v1`: `ContratoInternalV1IT` e `internal-v1-snapshot.json` não podem mudar. Se
  mudarem, algo saiu do lugar.
- O bloqueio do composer em conversa finalizada.

## Bloco 5 — Testes

- Repositório: lead **sem** atendimento aberto vem depois de lead **com** atendimento aberto, em
  `listar` e em `listarPaginado`; cartão com `status = 'FINALIZADO'` mas `atendimento_ativo_id`
  preenchido fica **no grupo de cima**; `contar` bate com `listar`.
- Paginação: duas páginas seguidas não repetem nem pulam cartão na fronteira entre os dois grupos.
  É o caso que quebra se o cursor não carregar a chave nova.
- Reabertura: lead finalizado → atendimento novo aberto, em modo humano, com quem pediu como
  responsável, o `FINALIZADO` antigo intacto, e **nenhuma mensagem enviada**.
- Reabertura fora da janela de 24h **funciona** (é abertura, não envio) e o composer oferece
  template.
- Lead invisível ao atendente → 404, não 403.
- Frontend: divisória aparece só em "Todos" e só com cartão abaixo; nome cinza só no grupo de baixo;
  o teste de cursor (*"sem reordenar nem duplicar"*) continua verde **sem ser editado** — se você
  precisou editá-lo, a ordenação foi para o lugar errado.

## Verificação

```
./mvnw verify        # no reator, na raiz de backend/
npm run lint && npm run typecheck && npm run test && npm run build   # em frontend/
```

## Relatório

1. Como ficou a ordenação no SQL e como o cursor passou a carregar a chave nova, com o trecho.
2. O nome que você deu ao caso de uso / entrada nova, e por que não é "reativar".
3. Onde você reaproveitou `IniciarNovoContatoUseCase` e o que precisou extrair — se duplicou
   alguma coisa, diga o quê e por quê.
4. O que acontece hoje com um cursor antigo chegando depois do deploy.
5. Qualquer teste existente que você editou, com a justificativa.
