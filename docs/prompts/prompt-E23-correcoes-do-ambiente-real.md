# Prompt E23 — Correções encontradas no ambiente real

> Leia `AGENTS.md`. Todos os achados abaixo vieram da primeira sessão de uso real do CRM em homologação, em 15/08 — nenhum apareceu em teste automatizado.
> Blocos em ordem de gravidade. Commite e faça push por bloco.
> Ao encerrar: `cd backend && ./mvnw clean verify` e o **número da run** do CI.

---

## Bloco 1 — O atendente não consegue enviar mensagem

**Bloqueante. Sem isto o produto não atende ninguém.**

`useMensagens` (`lib/atendimento/use-mensagens.ts`) usa **`useInfiniteQuery`**. O cache da chave `["mensagens", atendimentoId]` tem a forma:

```js
{ pages: [ { mensagens: [...], proximoCursor } ], pageParams: [...] }
```

Mas `useEnviarMensagem.onMutate` (`lib/atendimento/use-enviar-mensagem.ts:43`) trata o mesmo cache como array plano:

```js
queryClient.setQueryData<MensagemResposta[]>(queryKey, (atual) => [...(atual ?? []), otimista]);
```

`[...objeto]` sobre um objeto não-iterável lança `TypeError`. O `onMutate` quebra antes de gravar, o React Query aborta a mutation, e **o `mutationFn` nunca é chamado** — nenhuma requisição HTTP sai do navegador.

Sintomas observados, todos explicados por isso: o texto some do campo (`setTexto("")` roda antes do `mutate`), nenhuma mensagem aparece, **nada no Network**, e nenhum erro na tela.

**O mesmo defeito está em `use-enviar-midia.ts:44`.** Anexo está quebrado do mesmo jeito.

**Correção:** os dois hooks passam a operar sobre `InfiniteData<PaginaMensagens>`, alterando `pages[0].mensagens`. O padrão correto já existe no `atualizarPaginaRecente` do próprio `use-mensagens.ts` — reaproveite em vez de escrever um terceiro jeito de mexer nesse cache.

Vale para `onMutate`, `onError` (transição para `FALHOU`) e `onSuccess` (troca do id temporário pelo real).

### Por que o teste não pegou

`use-enviar-mensagem.test.tsx` e `use-enviar-midia.test.tsx` leem o cache como `MensagemResposta[]` — **a mesma forma errada do código**. Teste e implementação concordam entre si e discordam da realidade.

**Reescreva os testes montando o cache no formato de `useInfiniteQuery`**, e acrescente um que só passa se a mensagem otimista aparecer no resultado de `useMensagens` — não no `getQueryData` cru. Teste que valida a própria ficção é pior que teste ausente: dá confiança falsa.

## Bloco 2 — Falha de envio é invisível

`composer.tsx:128` monta `mensagemDeErro` lendo **apenas** `enviarMidia.error`. O `enviar.error` não é lido em lugar nenhum: anexo que falha mostra aviso, texto que falha não mostra nada.

Some a isso que, com o `onMutate` quebrado, a linha otimista nunca chega à tela — então o `status_entrega` `FALHOU` e o botão de reenviar, que a E11 construiu, não têm como aparecer neste caminho.

Num CRM de atendimento este é o pior tipo de defeito: o atendente digita, o texto some, ele acredita que enviou, e o cliente nunca recebe.

**Correção:** erro de envio de texto aparece na tela, no mesmo padrão do erro de mídia. E um teste que prova que uma falha de envio fica visível — não só que o estado interno mudou.

## Bloco 3 — WebSocket caindo com 504

```
wss://crm.187.77.47.30.sslip.io/ws?access_token=... 
→ Error during WebSocket handshake: Unexpected response code: 504
```

Sem WebSocket não há tempo real: mensagem nova não aparece sozinha, status de entrega não atualiza, e a tela só muda com recarga. Tempo real é requisito central do produto, não enfeite.

O 504 vem do Traefik — ele não completou o handshake com o backend. Investigue nesta ordem: as labels do router do backend em `docker/dokploy-stack.yml` cobrem `/ws`, mas confira se o upgrade está sendo encaminhado; depois `WS_ORIGENS_PERMITIDAS` e a configuração do endpoint STOMP no backend.

**Entregue um teste que exercite o handshake de verdade contra o ambiente empacotado**, não um mock — este é exatamente o tipo de coisa que passa em teste de unidade e falha atrás de um proxy.

## Bloco 4 — ADMINISTRADOR bloqueado na Automação e na telemetria

```
/api/v1/automacao/config     → 403
/api/v1/automacao/telemetria → 403
```

Os dois endpoints nasceram com `hasAnyRole('GESTOR','SUBGESTOR')` — nas etapas E15b e E20, por instrução minha — e **ADMINISTRADOR ficou de fora**. Um administrador logado não consegue abrir a tela de Automação nem ver os cards de telemetria do Dashboard.

**Correção:** revise a autorização de **todos** os endpoints do sistema quanto ao papel `ADMINISTRADOR`, não só estes dois. Se o padrão do projeto é que administrador tem ao menos o alcance de gestor, então este é o mesmo defeito repetido em quantos lugares ninguém contou ainda.

Diga no relatório **quantos endpoints** você teve que corrigir. O número diz se foi descuido pontual ou regra ausente.

## Bloco 5 — `POST /api/auth/refresh` devolvendo 401

Observado no console logo após login, com sessão nova. O access token dura **15 minutos** (`exp - iat = 900`), então um refresh quebrado significa que a sessão morre em quinze minutos de uso.

O `apiFetch` trata isso corretamente — renova, e se a renovação falhar, manda para `/login`. Mas o usuário **não** estava sendo deslogado, o que sugere que esse 401 vem de outro caller, provavelmente uma renovação na montagem da página, antes de existir sessão.

**Investigue se é ruído ou defeito.** Se for chamada na montagem sem sessão, não deveria acontecer — e polui o console, escondendo erro de verdade, que foi exatamente o que atrasou o diagnóstico do Bloco 1 por mais de uma hora.

## Bloco 6 — O canal precisa nascer com a instância

`canal` e `canal_credencial` estavam **vazias** na homologação. O CRM recebe mensagem sem elas (o webhook resolve tudo pelo payload), mas não tem de onde enviar. Foram preenchidas na mão, por SQL.

Não existe tela nem API para cadastrar canal. Ou seja: **todo filho novo precisa de alguém com acesso ao banco para conseguir enviar** — o que contradiz a promessa do Base PAI de que filho novo é deploy e configuração.

**Correção:** o script de provisionamento cria `canal` e `canal_credencial` a partir das variáveis que já existem no ambiente — `WHATSAPP_NUMERO` é o Phone Number ID e `WHATSAPP_PROVEDOR` o tipo. Idempotente, como o resto do script.

E acrescente ao `/health/critical`: **canal ativo cadastrado**. Hoje o check valida a credencial contra o provedor, mas uma instância sem canal nenhum passa — e foi assim que isso chegou até a primeira mensagem real sem ninguém notar.

## Bloco 7 — A instância só processa o número que é dela

A inscrição de app na Meta é **por conta (WABA), não por número**. Descoberto em 15/08: a conta `Estrutural Vidros` tem dois números — o `+55 61 3199-1947` que usamos em homologação e o `+55 61 3213-6200`, que é o número comercial real, em uso, com qualidade Alta e atendido por outra plataforma (`f-bot`).

Ao inscrever o nosso app na conta, a instância de homologação passou a receber webhook **dos dois números**. Nenhuma mensagem de cliente real chegou até agora, mas isso é sorte de horário, não proteção.

No modelo Silo cada filho tem o seu número. Receber webhook de um número que não está no `canal_credencial` da instância e criar lead a partir dele é errado por desenho — mistura cliente de outra operação no banco de um filho, e em produção isso é incidente de dados pessoais, não bug.

**Verifique primeiro se a proteção já existe.** O processador do webhook pode já estar comparando o `phone_number_id` recebido com o `identificador_externo` do canal ativo. Se estiver, diga isso no relatório e **não invente um segundo caminho** — foi assim que nasceram outros defeitos deste projeto.

Se **não** estiver:

- webhook cujo `phone_number_id` não corresponda a nenhum `canal_credencial` ativo é **descartado**, com log em nível de aviso — não é erro do remetente, é mensagem que não pertence a esta instância
- **teste negativo obrigatório:** payload com `phone_number_id` desconhecido não cria lead, não cria atendimento e não grava mensagem
- o descarte precisa ser visível no log, senão vira o oposto do problema — mensagem legítima sumindo em silêncio quando alguém errar o cadastro do canal

## Definição de pronto

- [ ] Envio de texto e de mídia funcionando, com mensagem otimista aparecendo em `PENDENTE`
- [ ] Testes reescritos sobre o formato real do `useInfiniteQuery`, com um que falha se a otimista não chegar à tela
- [ ] Falha de envio de texto visível, com teste
- [ ] WebSocket conectando no ambiente empacotado, com teste de handshake real
- [ ] `ADMINISTRADOR` revisado em todos os endpoints; **número de correções no relatório**
- [ ] `/api/auth/refresh` sem 401 espúrio no console
- [ ] Provisionamento criando o canal; `/health/critical` acusando ausência de canal
- [ ] CI verde com **número da run**

Commit por bloco. O Bloco 1 vai sozinho e primeiro — é o que impede o produto de atender.

No relatório, item 5: procure **outros lugares** que escrevam no cache de `["mensagens", ...]` assumindo array plano. Se houver um terceiro, o problema é o formato não estar encapsulado, e a correção é um helper único — não três hooks concordando por coincidência.
