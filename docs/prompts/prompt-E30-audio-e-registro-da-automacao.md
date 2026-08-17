# Prompt E30 — Áudio gravado recusado e registro de mensagem da Automação

> Leia `AGENTS.md`. Entrega em 25/08.
> Blocos em ordem de gravidade. Commite e faça push por bloco.
> Ao encerrar: `cd backend && ./mvnw clean verify` e o **número da run** do CI.

**Substitui o `prompt-E28b`**, cujo conteúdo virou o Bloco 1 aqui.

O Bloco 2 destrava a Automação e pode ser publicado sem o Bloco 3.

---

## Bloco 1 — O áudio gravado é recusado no upload

A gravação da E28 funciona até o envio. Ao subir, o backend responde **422**:

```
POST /api/v1/atendimentos/{id}/mensagens/midia  →  422
tipo de arquivo nao permitido: video/quicktime
```

Reproduzido em homologação, com microfone real.

A E28 negociou `audio/mp4` — aceito pela Meta — e a gravação produziu `audio/mp4;codecs=opus`.
**Opus dentro de contêiner MP4 é uma combinação incomum**: o `ftyp` resultante não é o de um M4A
convencional, e a detecção por conteúdo do backend classifica o arquivo como `video/quicktime`,
fora da allowlist.

O formato foi validado contra a lista da Meta, mas não contra o validador do próprio CRM. Os
testes da E28 exercitaram gravar, pré-visualizar e descartar — **nunca o upload**.

### Corrigir na origem, não no validador

Grave **AAC dentro de MP4** — o M4A convencional, reconhecido como `audio/mp4` pelos detectores
e aceito nativamente pela Meta. Ordem de preferência:

```js
'audio/mp4;codecs=mp4a.40.2'   // M4A/AAC — preferido
'audio/mp4'                     // deixa o navegador escolher
```

> **Proibido: adicionar `video/quicktime` à allowlist de upload.** Seria trocar um defeito de
> formato por um buraco — o CRM não trata vídeo, e a lista existe para impedir que ele receba
> um. Se não houver saída pelo lado da gravação, **pare e me avise**.

Confirme os dois lados, empiricamente: qual `mimeType` a gravação produz, e qual tipo o backend
**detecta** para um M4A/AAC real. Não presuma que são iguais — o defeito atual nasceu disso.

### E dizer a verdade quando não há microfone

Sem microfone, `getUserMedia` rejeita com `NotFoundError` e a tela diz "Não foi possível gravar
o áudio. Tente novamente" — falso, tentar de novo nunca funciona. Distinga, com textos do
catálogo:

| Erro | Mensagem |
|---|---|
| `NotFoundError` | nenhum microfone disponível no computador |
| `NotAllowedError` | permissão negada — e diga que se libera no cadeado da barra de endereço |
| `NotReadableError` | microfone em uso por outro programa |

### Testes do Bloco 1

- **Contrato do endpoint**: um M4A/AAC real — bytes de verdade, não stub com `content-type`
  forjado — sobe e recebe `200`. É o teste que o defeito atual reprovaria.
- **Negativo**: arquivo de vídeo real continua recebendo `422`.
- **Navegador**: gravar, confirmar, enviar, com o upload chegando ao backend. Descartar não basta.
- Os três estados de erro acima.

---

## Bloco 2 — A Automação registra o que já enviou

### O desenho, e por que é assim

Hoje existe um caminho de saída só: o atendente escreve, o CRM persiste e o CRM chama a Meta.
Para a IA o fluxo é outro — o n8n envia **direto** à Meta e depois avisa o CRM:

```
IA → n8n → Meta → WhatsApp do cliente
             └──→ CRM (registrar, sem reenviar)
```

**Decisão do arquiteto, já tomada:** mantemos o n8n como quem chama a Meta. A alternativa —
CRM como saída única, com a IA pedindo o envio — é mais robusta, porque deixa um dono só do
envio e faz o status de entrega casar naturalmente. Ficou de fora por prazo: os workflows já
enviam, e a parte cara (o CRM entender interativo) é a mesma nos dois desenhos.

O preço dessa escolha, que o Bloco exige mitigar: **se o n8n envia e o registro falha, a
mensagem existe no celular do cliente e não existe no CRM**, e o atendente vê um buraco na
conversa. Por isso o `wamid` e a idempotência não são melhoria — são requisito.

### O endpoint

Vai em **`/internal/v1`**, que já existe com `X-Synapse-Token`, filtro próprio e teste de
contrato. **Não crie `/api/n8n/**`** — seria um segundo mecanismo de autenticação para o mesmo
fim.

**Sem campo `enviarWhatsapp`.** Um endpoint cujo comportamento inverte por booleano é um convite
a mandar `true` por engano e disparar mensagem duplicada ao cliente. O nome da rota diz o que
ela faz, e ela só faz isso: registrar. Algo como
`POST /internal/v1/atendimentos/{id}/mensagens-enviadas`.

O CRM, ao receber: **não chama a Meta**, grava a mensagem como saída com autoria da IA, guarda o
`wamid`, atualiza a última interação do lead e publica no WebSocket para a conversa acender na
hora.

### Idempotência — e a armadilha da partição

O `wamid` é a chave: o n8n toma timeout e repete, e a mesma mensagem não pode virar duas linhas.

> **Atenção, e provavelmente o ponto mais difícil desta etapa.** A tabela `mensagem` é
> **particionada por `enviado_em`** — e em tabela particionada um índice único precisa **conter a
> coluna de partição**. Um `UNIQUE (wamid)` puro não é possível; `UNIQUE (wamid, enviado_em)`
> deixaria passar o mesmo `wamid` em partições diferentes, que é justamente o caso de uma
> retentativa na virada do mês.
>
> Decida como garantir unicidade real e **justifique no relatório**. Se a saída que você
> encontrar implicar mudar o particionamento, **pare e me avise** — não mexa nele sozinho.

### Testes do Bloco 2

- Registro cria mensagem de saída no atendimento, com autoria da IA, e **o adaptador da Meta
  não é chamado** — verifique isso explicitamente, é a garantia central do endpoint.
- A mesma chamada repetida com o mesmo `wamid` **não** cria segunda mensagem e responde de forma
  idempotente.
- Sem `X-Synapse-Token`, ou com token errado: recusado.
- Atendimento inexistente: erro claro, não 500.
- A mensagem registrada chega pelo WebSocket a quem está com a conversa aberta.

---

## Bloco 3 — Botões e listas no histórico

Sem isto, a IA manda botões para o cliente e o CRM guarda só o texto: o atendente que assume a
conversa não vê o que foi oferecido. Foi esse o defeito que gerou o P1 no outro projeto.

Hoje o CRM **não tem nada disso**: `TipoMensagem` é `TEXTO`, `IMAGEM`, `AUDIO`, `DOCUMENTO`, e
busca por botão, lista ou `interactive` no backend inteiro retorna zero. É tipo novo — domínio,
schema, read model e frontend.

**Normalize; não guarde o JSON da Meta.** O payload do provedor não entra no domínio (regra de
ACL do `AGENTS.md`). Algo como:

```
tipo: BOTOES | LISTA
opcoes: [ { id, titulo, descricao? } ]
```

**Na resposta do cliente, mostre o título, não o id.** `button_reply` traz `id: "agendar"` e
`title: "Agendar consulta"`; o histórico precisa dizer "Agendar consulta". Isso vale para o
webhook de entrada, que hoje já processa a resposta do cliente.

### Testes do Bloco 3

- Registro com interação persiste as opções normalizadas e o histórico as devolve.
- Resposta do cliente a um botão aparece no histórico com o **título**, não com o id.
- Conversa com interativo renderiza no frontend sem quebrar as bolhas existentes.

---

## Definição de pronto

- [ ] Gravação em MP4/AAC, com os dois mimeTypes (produzido e detectado) relatados
- [ ] Nenhum tipo novo na allowlist de upload
- [ ] Três estados de erro do microfone com mensagens distintas
- [ ] `POST /internal/v1/...` registrando sem chamar a Meta, sem campo `enviarWhatsapp`
- [ ] `wamid` persistido, com unicidade real resolvida e justificada
- [ ] Interação normalizada, com título na resposta do cliente
- [ ] Todos os testes acima, incluindo o que prova que a Meta não é chamada
- [ ] `docs/16` atualizado com o endpoint novo, para o Dylan
- [ ] CI verde com **número da run**

## No relatório

Como a unicidade do `wamid` foi garantida apesar do particionamento — é o ponto onde eu mais
espero uma decisão sua.

Se o Bloco 3 não couber junto, publique os Blocos 1 e 2 e diga o que faltou. O Bloco 2 sozinho
já destrava a Automação para mensagens de texto e mídia.
