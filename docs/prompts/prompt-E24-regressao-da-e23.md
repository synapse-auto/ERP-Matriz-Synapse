# Prompt E24 — Regressão da E23: o sistema não abre

> Leia `AGENTS.md`. **Prioridade máxima.** Entrega em 25/08.
> A E23 passou no CI com 244 testes de integração e 79 de frontend, e **quebrou o CRM em homologação**. Nenhum teste pegou.

---

## O sintoma

Depois do deploy do `55dca61`, na instância de homologação:

- **O menu lateral inteiro sumiu** e foi substituído por `estados.erroGenerico` ("Algo deu errado. Tente novamente."). Em `sidebar.tsx:148`, isso acontece quando a query de `GET /api/v1/config/features` entra em `isError`.
- **O rodapé perdeu o nome do usuário** e voltou ao avatar "?" — o `GET /api/v1/me` também está falhando. Funcionava em 15/08.
- O papel "ADMINISTRADOR" **continua aparecendo**, e ele é decodificado do JWT no cliente: **o token existe**.
- Nenhum redirecionamento para `/login`, então o `apiFetch` não caiu no caminho de sessão morta.
- Logs do backend limpos: boot normal, nenhuma exceção.

Ou seja: **há token, e as chamadas autenticadas falham assim mesmo.**

`/api/v1/config/features` não tem `@PreAuthorize` — exige apenas autenticação. Então não é o endpoint que ficou restrito.

## Método — reproduza antes de corrigir

**Não conserte por hipótese.** Suba a stack empacotada localmente, autentique como `ADMINISTRADOR` e chame `GET /api/v1/config/features` e `GET /api/v1/me` com o token real. Descubra o **código HTTP e o corpo** antes de tocar em qualquer arquivo.

Os sete commits da E23 são os suspeitos. Dois merecem olhar primeiro:

- **`e3cbb13` — "evitar refresh sem sessão".** O access token vive em memória; num reload ele desaparece e a sessão só volta pelo refresh na montagem. Se essa renovação deixou de acontecer fora de `/login`, a aplicação sobe sem token válido no store — e o papel ainda apareceria, se vier de um token velho ou decodificado antes.
- **`af810aa` — "incluir administrador nos endpoints de gestão".** Confirme que a mudança ficou restrita aos quatro endpoints e **não tocou em `SecurityConfig`, no mapeamento de authorities nem no prefixo `ROLE_`**. O projeto já teve um defeito assim: `JwtAuthenticationToken` construído de forma a nascer com `authenticated=false`, derrubando todo `@PreAuthorize` sem exceção visível.

Diga no relatório **qual commit causou** e por quê.

## O teste que faltava

A E23 tinha 323 testes verdes e derrubou o produto. Isso não é azar: **não existe teste que prove que a aplicação abre.**

Construa um teste de fumaça de ponta a ponta, no mesmo padrão do teste de handshake do WebSocket que você já escreveu na E23 — stack empacotada, backend real, sem mock:

1. autentica como `ADMINISTRADOR`
2. `GET /api/v1/config/features` responde **200** com lista
3. `GET /api/v1/me` responde **200** com nome, papel e presença
4. repete para `GESTOR`, `SUBGESTOR` e `ATENDENTE`

Ele roda no CI e reprova qualquer mudança que impeça o app de montar o menu. É o teste mais barato que este projeto podia ter e é o que teria evitado a noite de ontem.

## Depois disso — o WebSocket, com a evidência já levantada

A correção da E23 mudou o erro de 504 para 502, mas não resolveu. O que se sabe, medido no ambiente real:

- **O backend aceita o upgrade.** Dentro do container, `curl` com cabeçalhos de upgrade e token válido em `http://127.0.0.1:8080/ws?access_token=...` devolve **`101 Switching Protocols`** com `Sec-WebSocket-Accept`.
- **O Traefik do Dokploy devolve 502** para o mesmo caminho, pelo domínio público.
- O log do backend mostra `WebSocketSession[0 current, 0 total]` e `CONNECT(0)` — nada chega nele.
- HTTP funciona no **mesmo router e mesmo serviço**, então não é seleção de rede.

**O teste que você escreveu passa contra um Traefik montado por você — não contra o do Dokploy.** É a mesma armadilha do CI verde: o teste valida uma configuração que não é a que roda.

Investigue: router duplicado (a aba Domains do Dokploy deve estar **vazia**; as rotas vêm das labels), versão do Traefik do Dokploy versus a sintaxe `traefik.swarm.*` usada nas labels, e middleware global aplicado pelo Dokploy.

E um defeito de cliente visível no console: **a primeira tentativa de conexão vai com `access_token=` vazio** — o cliente STOMP conecta antes de a sessão existir.

## Definição de pronto

- [ ] Causa da regressão identificada por reprodução, com o commit nomeado
- [ ] Menu e rodapé voltando a funcionar para `ADMINISTRADOR`
- [ ] Teste de fumaça de boot cobrindo os quatro papéis, na stack empacotada
- [ ] WebSocket conectando pelo domínio público, ou diagnóstico escrito do que falta com evidência
- [ ] Cliente STOMP não conecta sem token
- [ ] CI verde com **número da run**

Commit: `fix: regressão da E23 e teste de fumaça de boot`.

No relatório, item 5: diga **quantos outros caminhos** do frontend quebram inteiros quando uma query falha. A sidebar come o menu; o painel do lead faz o mesmo (`painel-lateral-lead.tsx:214`). Se o padrão for "query falhou, tela some", uma indisponibilidade momentânea de um endpoint derruba áreas inteiras do produto — e isso é decisão de arquitetura, não detalhe de UI.
