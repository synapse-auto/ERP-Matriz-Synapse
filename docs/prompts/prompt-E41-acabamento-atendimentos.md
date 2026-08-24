# Prompt E41 — acabamento da aba Atendimentos

> Leia `AGENTS.md`, `frontend/AGENTS.md` e `design/TOKENS.md`.
> **Não faça commit nem push sem autorização explícita do Marcondes.**
> Ao encerrar, informe os testes executados, o diff e o que ficou sem verificação.

---

## Contexto

Dois defeitos visuais foram apontados na lista de Atendimentos em homologação, e a causa de cada um
já está localizada. **Os Blocos 1 e 2 são a prioridade desta etapa** — o resto é resíduo da E40.

---

## Bloco 1 — A aba selecionada vira um retângulo azul

**Sintoma:** ao selecionar "Ativos", a aba ganha um retângulo azul em volta. O protótipo tem
sublinhado na cor primária, não caixa.

**Causa, já localizada.** Em `lista-conversas.tsx` o `TabsTrigger` recebe:

```tsx
className="... border-b-2 border-transparent ... data-[state=active]:border-primary
           data-[state=active]:bg-transparent data-[state=active]:shadow-none"
```

`data-[state=active]` é a convenção do **Radix**. Este projeto usa **Base UI**, cuja convenção é
`data-active` — é o que o próprio `components/ui/tabs.tsx` usa em todas as suas regras. Confirmado:
essa linha é **a única ocorrência de `data-[state=active]` no repositório inteiro**.

Ou seja, os três modificadores são **CSS morto**. Nada do que o autor quis aplicar é aplicado. O que
se vê no lugar vem do componente base:

- `data-active:bg-background` — pinta o fundo da aba ativa, criando a caixa;
- `focus-visible:border-ring focus-visible:ring-[3px]` — pinta a borda azul, porque a seleção move o
  foco para a aba.

E o sublinhado pretendido nunca aparece.

**Correção.** O sublinhado já existe pronto no componente: a variante `line` do `TabsList` desenha a
linha por pseudo-elemento (`group-data-[variant=line]/tabs-list:data-active:after:opacity-100`).
Use `variant="line"` e **remova as classes feitas à mão** — `border-b-2`, `border-transparent` e os
três `data-[state=active]:`. Não troque `data-[state=active]` por `data-active` mantendo a borda:
isso conserta a cor e deixa o retângulo de pé, porque a borda continua nos quatro lados.

**Não apague o indicador de foco.** Quem navega por teclado precisa enxergar onde está. O foco pode
deixar de ser um retângulo azul cheio, mas tem que continuar visível e distinguível da seleção.

**Guarda contra reincidência:** ao terminar, garanta que `data-[state=active]` não aparece em lugar
nenhum do `frontend/src`. Se o projeto tiver como transformar isso em regra de lint, proponha —
essa classe silenciosamente não faz nada, que é o pior tipo de defeito de estilo.

## Bloco 2 — Todos os avatares na mesma cor

**Sintoma:** na lista de Atendimentos, todas as fotos de perfil saem no mesmo cinza. Cada pessoa
precisa de um tom próprio, além das iniciais, para se distinguir de relance.

**Causa, já localizada.** `cartao-conversa.tsx` usa o `Avatar` cru, cujo `AvatarFallback` é
`bg-muted text-muted-foreground` — cinza fixo para todo mundo.

**E a solução já existe no projeto.** `components/ui/avatar-iniciais.tsx` exporta `AvatarIniciais` e
`tomDoAvatar(id)`: hash determinístico do id sobre uma paleta de tokens, sem cor literal e sem o
backend guardar cor por pessoa. Já é usado em **Agenda, Equipe, Lembretes, Mensagens Programadas e
Mensagens Rápidas**. Atendimentos é a única superfície que ficou de fora.

**Reaproveite `tomDoAvatar`. Não escreva um segundo hash, não crie uma segunda paleta, não adicione
coluna de cor no banco.**

Quatro exigências:

- **Chave estável:** o tom sai do **id** do lead, não do nome. Renomear um lead não pode trocar a
  cor dele, e dois leads com o mesmo nome têm que se distinguir.
- **A foto continua vencendo.** O card já renderiza `leadFotoUrl` quando existe; o tom é o
  *fallback*. Não substitua a foto por iniciais coloridas.
- **A mesma pessoa, o mesmo tom em toda tela.** Aplique também no cabeçalho da conversa e no painel
  de detalhes do lead. Um lead azul na lista e verde no painel é pior que todos cinzas.
- **Contraste.** As iniciais sobre o tom precisam ser legíveis. Se algum token da paleta for claro
  demais para texto branco, isso é problema do token — relate e proponha o ajuste em
  `design/TOKENS.md`, não resolva com cor literal no JSX.

---

## Bloco 3 — Confirmar o resíduo da E40 antes de escrever JSX

**Faça isto antes de escrever qualquer componente novo — os Blocos 1 e 2 podem ser feitos antes.**

Suba o front contra uma base **com o seed aplicado** e confirme, item a item, que estes elementos
aparecem sozinhos, sem código novo:

- segunda linha do card com empresa/segmento
- selo de etapa colorido no rodapé do card
- ícone do canal no card
- trilha "ETAPA DO ATENDIMENTO" com a barra segmentada e "{n} de {total}" no painel do lead
- etiquetas do lead
- separador de data ("Hoje"/"Ontem") e a linha "Atendimento recebido · WhatsApp · {responsável}"
  no topo da conversa

> Qualquer um desses que **já apareça** vai para o relatório como confirmado e **não vira código**.
> Este projeto já pagou caro por agente que reimplementou o que existia.

Se algum deles continuar invisível **com o seed aplicado**, aí sim investigue — e diga se a causa é
o componente, a resposta da API ou a linha no banco.

---

## Bloco 4 — O travessão no lugar do responsável

`cartao-conversa.tsx` sempre renderiza o quadrado de iniciais do responsável e, quando não há
atendente, escreve `"—"` dentro dele:

```tsx
{cartao.atendenteNome ? iniciaisDoNome(cartao.atendenteNome) : "—"}
```

Na lista real isso vira uma coluna de travessões em caixinha cinza — o protótipo não tem nada ali
quando a conversa não tem dono.

**Correção:** não renderizar o elemento quando não há responsável. O `min-h-5` da linha já garante
que o card não encolha e a lista não "pule" entre cards com e sem responsável — confirme isso, não
presuma.

O `title` com "sem atendente" some junto: um elemento invisível não precisa de rótulo acessível.

## Bloco 5 — Autoria das mensagens enviadas pela automação

No protótipo todo balão enviado é atribuído: o nome do atendente aparece em negrito dentro do balão
azul. Em homologação os balões azuis da IA saem **sem autoria nenhuma**, porque `nomeDaAutoria()`
depende de `remetenteNome` ou de `remetenteId === atendenteId`, e a mensagem da automação não tem
nem um nem outro.

A E40 acertou ao não inventar autoria. O que falta é a autoria **verdadeira**: quando a mensagem
saiu pela automação, quem falou foi a IA, e o balão deve dizer isso.

**Regra:** a origem vem de `remetenteTipo`, não de heurística sobre nome nulo. Rótulo no catálogo de
textos (`textos.json`), **nunca literal no JSX**. Um `remetenteTipo` que você não reconheça continua
sem autoria — não invente rótulo genérico.

Se `remetenteTipo` hoje não distingue automação de atendente humano, **pare e relate** em vez de
adivinhar pelo formato do dado: a correção passa a ser de contrato, não de front.

## Bloco 6 — Contador no item "Atendimentos" do menu

O protótipo mostra um badge com a contagem ao lado de "Atendimentos" na barra lateral. Hoje não
existe.

`useContagemDeAtendimentos` já existe e alimenta as abas da lista. Reaproveite — **não crie um
segundo endpoint nem um segundo `useQuery` com outra chave de cache**.

Três cuidados:

- o número é o de **pendentes**, não o total: um badge que mostra 35 o tempo todo não informa nada;
- a barra lateral aparece em toda tela do shell, então a consulta não pode disparar erro visível nem
  bloquear a renderização do menu — falha vira ausência do badge, silenciosa;
- o badge respeita o recorte por papel que o servidor já aplica (RN-CRM-01). O front não soma nada
  por conta própria.

## Bloco 7 — Canal nulo no cartão (investigação, não conserto às cegas)

Em homologação, conversas reais do WhatsApp aparecem **sem o ícone de canal** no card. O card só
mostra o ícone quando `cartao.canalTipo` chega preenchido, e a consulta do painel usa:

```sql
LEFT JOIN canal c ON c.id = a.canal_id
```

Ou seja: `canal_id` nulo no atendimento apaga o ícone.

**Descubra qual dos dois é**, com evidência:

1. o atendimento criado pelo webhook da Meta **não grava** `canal_id` — bug de backend, corrija na
   origem (e diga se atendimentos antigos precisam de correção de dado);
2. grava, e o problema está na consulta ou na serialização.

Não "resolva" isso no front com fallback para WhatsApp. O CRM é multicanal por desenho; chumbar o
canal no componente é esconder o defeito.

## Bloco 8 — O que NÃO entra nesta etapa

Três elementos do protótipo continuam fora, e o motivo é o mesmo da E40: **não existe contrato por
trás deles**.

- **Botão "nova conversa"** no cabeçalho da lista. Não há endpoint de criar atendimento; iniciar
  conversa com um contato exige template aprovado da Meta e a janela de 24h. É uma funcionalidade,
  não um botão.
- **Menu `⋮`** no cabeçalho da conversa. Nenhuma das ações que ele abrigaria existe.
- **Botão de câmera** no avatar do lead. Não há upload de foto de lead; `leadFotoUrl` vem da Meta.

Se você acha que algum deles cabe, **escreva o contrato que faltaria e devolva no relatório** — não
implemente.

Fora do escopo também: ligar as flags `banco_arquivos`, `campanhas`, `horarios` e `relatorios`. Os
itens já existem em `sidebar.tsx` atrás de flag, mas as rotas caem no placeholder "em construção".
Menu cheio levando a tela vazia é pior, na frente do cliente, do que menu curto e honesto.

---

## Verificação

- `npm test` e `npm run lint` no `frontend`.
- Teste para a aba selecionada: a ativa se distingue da inativa e **não** ganha caixa nem borda
  nos quatro lados; o indicador de foco por teclado continua existindo.
- Teste para o tom do avatar: mesmo id sempre no mesmo tom, ids diferentes em tons diferentes, e
  foto presente prevalecendo sobre as iniciais.
- Teste para o card **sem** responsável: nada é renderizado no lugar, e a altura da linha não muda.
- Teste para a autoria por origem: mensagem da automação atribuída à IA, mensagem de atendente
  atribuída ao atendente, origem desconhecida sem autoria.
- Teste para o badge do menu: com contagem, sem contagem e com a consulta em erro.
- **Verificação visual obrigatória, com o seed aplicado.** A E40 foi entregue sem nenhuma, porque o
  agente não alcançou um backend. Se você também não alcançar, **diga isso em letras claras no
  relatório** em vez de descrever como a tela "deve" estar.

## Relatório

Para cada bloco: o que foi confirmado no Bloco 0 como já existente, o que virou código, o que ficou
sem verificar e por quê. No Bloco 7, a causa com evidência — consulta, log ou linha do banco.
