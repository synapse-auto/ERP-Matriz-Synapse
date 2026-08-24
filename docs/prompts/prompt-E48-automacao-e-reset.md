# Prompt E48 — aba Automação (papel, fidelidade) e comando `#reset`

> Leia `AGENTS.md`, `CLAUDE.md`, `frontend/AGENTS.md` e `design/TOKENS.md`.
> **Pode commitar localmente a qualquer momento.** Não execute `git push` sem autorização explícita.
> Referência visual: `design/componentes/Automacao.html`.

---

## Bloco 1 — Atendente vê "Automação" e a tela quebra

**Causa já localizada.** Em `sidebar.tsx`, `itemVisivel()` filtra por papel apenas dois itens:

```ts
if (item.chave === "equipe" && papel !== "GESTOR" && papel !== "ADMINISTRADOR") return false;
if (item.chave === "dashboard" && papel !== "GESTOR" && papel !== "SUBGESTOR"
    && papel !== "ADMINISTRADOR") return false;
```

**`automacao` não tem recorte nenhum.** Já o caso de uso
`AtualizarConfiguracaoAutomacaoUseCase` exige `hasAnyRole('GESTOR','SUBGESTOR','ADMINISTRADOR')`.
Resultado: o atendente vê o item, clica, a API recusa e a tela mostra erro.

Corrija nas **duas** camadas, e não só na primeira:

1. **O item some do menu** para quem não tem papel de gestão — mesmo tratamento de `equipe` e
   `dashboard`.
2. **A página se defende sozinha.** Quem digitar `/automacao` na barra de endereço, ou chegar por um
   link antigo, precisa ver uma mensagem clara de permissão insuficiente — **não** uma tela de erro
   genérica nem um estado quebrado. Esconder no menu não é autorização; é arrumação.

O servidor continua sendo a autoridade. Nada aqui afrouxa a checagem do caso de uso.

Enquanto estiver nisso: confira se **algum outro** item do menu está visível para papel que a API
recusa. O mesmo defeito pode existir em silêncio em outra tela.

## Bloco 2 — A aba Automação não segue o protótipo

Compare com `design/componentes/Automacao.html` e relate as diferenças **antes** de mexer em JSX.

Pontos observados na tela em homologação, que precisam ser confrontados com o protótipo:

- Os quatro cartões do topo (Mensagens Enviadas, Clientes Transferidos, Conexão Automação, Status do
  CRM) e o bloco "Recursos de IA" com o interruptor de Resumo por IA.
- A frase **"Nenhum parâmetro cadastrado."** aparece solta abaixo do bloco, sem enquadramento nenhum.
  Se o protótipo tem um estado vazio desenhado, use o desenho dele.
- As abas **Geral / Follow-up / Fidelização** aparecem com estilo diferente entre si nas capturas: em
  uma delas a aba ativa ganha um retângulo com borda. **Isso é o mesmo defeito que a E41 corrigiu na
  lista de Atendimentos** — `data-[state=active]` (convenção Radix) não funciona neste projeto, que
  usa Base UI com `data-active`. Procure por `data-[state=active]` em todo o `frontend/src`: o
  esperado é **zero ocorrência**. Se houver, é isto.
- O cartão de regra de follow-up tem os botões Editar / Desativado / lixeira; confira rótulo, ordem e
  peso visual contra o protótipo. "Desativado" como rótulo de botão é ambíguo — não diz se descreve o
  estado atual ou a ação. Use o texto do protótipo; se ele também for ambíguo, **relate** em vez de
  inventar.

Cores de estado (Ativa, Conectado, Online) saem de token, nunca de hexadecimal no JSX. Todo texto no
catálogo.

## Bloco 3 — Comando `#reset` na conversa

Quando o cliente escrever `#reset` no WhatsApp: a IA reinicia o contexto e, se o atendimento estiver
com um humano, ele **volta para a IA**.

### A divisão de responsabilidade, que é o ponto desta etapa

São duas metades, e **o CRM só pode fazer uma delas**:

- **Reiniciar o contexto da conversa é da Automação.** O contexto vive no n8n; o CRM não o guarda e
  não tem como limpá-lo. Isso é trabalho do Dylan, não seu.
- **Devolver o atendimento para a IA é do CRM.** E a operação **já existe**:
  `TransferirAtendimentoUseCase.devolverParaIaPeloSistema(...)`. Reuse — não escreva outra.

**Não crie endpoint novo em `/internal/v1` para isso.** As duas metades reagem à mesma mensagem, cada
uma no seu lado: o CRM detecta `#reset` na entrada e devolve o atendimento; o n8n detecta o mesmo
literal e limpa o contexto dele. Sem contrato novo, sem ordem de chamada para dar errado.

### Regras do reconhecimento

- **Casamento exato da mensagem inteira**, ignorando espaços nas pontas e maiúsculas/minúsculas.
  "quero #reset do orçamento" **não** dispara. Cliente não pode reiniciar atendimento sem querer.
- O literal **não é chumbado no código**: vive em `configuracao_automacao`, como as outras chaves. Cada
  filho pode ter o seu, e ninguém recompila para mudar.
- A mensagem `#reset` **continua sendo gravada** na conversa e o evento vai para a timeline e a
  auditoria — quem devolveu, quando, e de quem era o atendimento. Sem isso, um atendente perde a
  conversa sem explicação na tela.
- Se o atendimento **já estava com a IA**, o CRM não faz nada além de registrar. Não é erro.
- **O dono anterior é avisado** pelo canal pessoal da E42. Ele estava atendendo; não pode descobrir
  pelo silêncio.

### Uma consequência de negócio que precisa de decisão do Marcondes

Com a regra "a conversa é de quem atendeu por último", **um cliente digitando `#reset` tira o
atendimento do atendente que o estava servindo** — e o próximo humano que responder fica com ele, e
com a comissão. É o cliente mexendo em comissão sem saber.

**Não decida isso sozinho.** Implemente o comportamento pedido e **levante a pergunta no relatório**:
o `#reset` deve mesmo devolver para a IA quando há um humano no atendimento, ou nesse caso deve
apenas reiniciar o contexto e manter o dono? Se não houver resposta, entregue como pedido.

---

## Verificação

- `npm test -- --run`, `npm run lint`, `npm run build`.
- Backend: `./mvnw clean verify` **com testes**, reator inteiro.
- Teste de que o item Automação não aparece para `ATENDENTE` e que `/automacao` acessada direto
  responde com mensagem de permissão, não erro.
- Teste de que `#reset` exato devolve o atendimento para a IA, registra na timeline e avisa o dono
  anterior; e de que `"quero #reset do orçamento"` **não** dispara nada.
- Teste de que `#reset` num atendimento que já está com a IA não quebra.
- Zero ocorrências de `data-[state=active]` no `frontend/src`.
- **Verificação visual obrigatória**, com o protótipo aberto, nos dois papéis: administrador e
  atendente. Se não conseguir, **diga**.
