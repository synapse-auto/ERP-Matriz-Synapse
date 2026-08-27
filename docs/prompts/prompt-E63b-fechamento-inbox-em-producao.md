# Prompt E63b — Fechamento da inbox unificada e validação em produção

> Leia `AGENTS.md`. Entrega em 25/08.
> Esta é uma continuação de revisão da E63. Não aceite o relatório anterior como evidência.
> Não faça commit ou push sem autorização explícita do Marcondes.

---

## Contexto

Leia, nesta ordem, antes de alterar qualquer arquivo:

1. `AGENTS.md`
2. `docs/13-estado-do-projeto.md`
3. `docs/prompts/COMO-ESCREVER-PROMPTS.md`
4. `docs/prompts/prompt-E62-inbox-unificada-atendimentos-chat-interno.md`
5. `docs/prompts/prompt-E63-fechamento-inbox-unificada.md`

O objetivo é fechar a E63 sem ampliar o escopo. A inbox unificada deve continuar
misturando clientes e equipe interna em `Atendimentos`, respeitando a ordem global
da última mensagem, a privacidade do chat interno e o recorte de visibilidade de
leads.

Estado confirmado antes desta etapa:

- branch `main`;
- `HEAD` e `origin/main` em `7399b72635b13f79ea79e377e45bc3f7373462ab`;
- há alterações não commitadas da E62/E63 e prompts não rastreados; preserve tudo;
- `useAtendimentos` usa `useInfiniteQuery` para `TODOS` e retorna as páginas
  achatadas;
- `ListaConversas` repassa o resultado para o componente pai por
  `onAtendimentosAtualizados`;
- a validação visual real ainda poderá ser feita pelo Marcondes em produção.

Durante a conferência foi identificado este risco concreto:

```tsx
// frontend/src/lib/atendimento/use-atendimentos.ts
data: inbox.data?.pages.flatMap((pagina) => pagina.itens),
```

```tsx
// frontend/src/components/atendimentos/lista-conversas.tsx
useEffect(() => {
  onAtendimentosAtualizados?.(data ?? []);
}, [data, onAtendimentosAtualizados]);
```

O `flatMap` cria um novo array a cada render. Como o pai passa `setAtendimentos`,
o efeito pode gravar uma nova referência no estado do pai a cada render e provocar
ciclo de renderização ou `Maximum update depth exceeded` quando a inbox contém
dados. O teste atual do componente usa um array mockado estável e não prova esse
caminho real.

Confirme o diagnóstico no código antes de corrigir. Se ele já tiver sido corrigido
por outra alteração, demonstre isso com teste e diff; não faça uma segunda solução
sem necessidade.

## Bloco 1 — estabilizar a atualização da lista

- Elimine o ciclo de renderização sem esconder atualizações reais da inbox.
- Escolha a solução mais simples e local: identidade memoizada do array achatado,
  atualização condicional por conteúdo, ou remoção do espelhamento de estado se ele
  não for necessário.
- Preserve a atualização quando chegar mensagem interna, mensagem de cliente,
  reconexão, abertura de conversa ou mudança de página.
- Não use debounce, timeout ou comparação baseada em tempo para mascarar o defeito.
- Não desabilite o `refetchInterval`, o `IntersectionObserver` ou a invalidação para
  evitar o ciclo.

Adicione um teste de regressão que monte o caminho real o suficiente para exercer:

- dados de uma inbox `TODOS` com cliente e equipe interna;
- repasse da lista ao pai;
- pelo menos uma atualização posterior de dados;
- ausência de `Maximum update depth exceeded` e ausência de chamadas infinitas ao
  setter/atualizador;
- preservação da ordem e da seleção dos dois tipos.

O teste não deve depender de coordenadas, classes visuais ou de um mock que esconda
a identidade instável do retorno do hook.

## Bloco 2 — conferir a invalidação e a paginação

- Confirme que a chave usada pelo hook é `['atendimentos', visao]` e que todas as
  invalidações destinadas à inbox atingem também as páginas de `TODOS`.
- Se a invalidação ampla `['atendimentos']` for mantida, prove que ela é prefixo
  válido do TanStack Query usado no projeto; não crie uma segunda chave como
  `['inbox-unificada']`.
- Confirme que a próxima página usa o `proximoCursor` real, concatena na ordem
  recebida e não duplica itens.
- Confira que mensagens internas só entram em `TODOS`, que a flag desligada não
  as inclui e que filtros de cliente continuam usando os endpoints/recortes
  existentes.
- Confira que a seleção interna continua identificada por `tipo + conversaId` e
  nunca chama histórico, leitura, transferência ou finalização de cliente.

Não altere o contrato ou a estratégia keyset apenas para facilitar o teste. Se a
ordenação global ou a segurança ainda não forem verdadeiras no código, pare e
relate o gap em vez de declarar a E63 fechada.

## Bloco 3 — evidência automatizada e roteiro de produção

Execute e registre os comandos completos:

- frontend: suíte completa, `npm run typecheck`, `npm run lint` e `npm run build`;
- backend: `cd backend && ./mvnw clean verify`, com Java 21 e Testcontainers;
- `git diff --check`;
- branch, `HEAD`, `origin/main`, `git status` e diff antes/depois.

O agente não deve acessar ou alterar produção para fabricar evidência. Como a
validação visual pode ser feita pelo Marcondes após o deploy, entregue ao final um
roteiro manual com estes casos:

1. conta participante e conta não participante de uma conversa interna;
2. flag `chat_interno` ligada e desligada;
3. inbox `TODOS` com cliente e equipe interna misturados;
4. mensagem nova de cliente e mensagem nova interna, confirmando a ordem por
   última mensagem;
5. rolagem até a próxima página, confirmando ausência de duplicação;
6. abertura de cliente, verificando que o composer envia ao WhatsApp;
7. abertura de equipe, verificando que o histórico/composer são internos e que não
   aparecem detalhes, tags, transferência ou finalização de cliente;
8. uso do botão `+` para nova conversa interna;
9. console do navegador sem `Maximum update depth exceeded` e sem erro de rede
   causado pela inbox.

O roteiro deve indicar o resultado esperado e o que deve ser fotografado/registrado.
Não escreva no relatório que a produção foi validada se o Marcondes ainda não
executou esses passos.

## Restrições

- Não grave chat interno como `Atendimento`.
- Não relaxe `RN-CRM-01`, participação ou RLS para fazer o item aparecer.
- Não use dados mockados no frontend de produção.
- Não crie endpoint de novo contato WhatsApp nesta etapa.
- Não crie broadcast WebSocket novo.
- Não introduza strings de UI, cores literais ou configuração hardcoded.
- Não altere migration aplicada.
- Java 21 é fixo.
- Não crie variável nova no Dokploy. Se descobrir uma necessária, pare, documente
  o nome/valor de exemplo e atualize `.env.example` e `README.md`.

> **Ponto de parada.** Se o ciclo de renderização estiver sendo provocado por uma
> decisão de contrato mais ampla, ou se corrigi-lo exigir mudar a API, a paginação,
> a segurança ou o modelo de dados, pare e informe antes de escolher uma expansão
> de escopo.

## Definição de pronto

- [ ] O ciclo de renderização do `flatMap` foi eliminado ou comprovadamente já não existe.
- [ ] Há teste de regressão do caminho real de atualização pai/lista.
- [ ] Cliente e equipe interna continuam na ordem global correta.
- [ ] Cursor, concatenação, invalidação e ausência de duplicação estão cobertos.
- [ ] Participação, visibilidade do lead e flag `chat_interno` continuam protegidas.
- [ ] Seleção interna não chama operações de atendimento de cliente.
- [ ] Suíte frontend completa, typecheck, lint e build passaram.
- [ ] `./mvnw clean verify` passou com Java 21/Testcontainers.
- [ ] `git diff --check` passou.
- [ ] O roteiro de validação em produção foi entregue sem ser apresentado como teste executado.
- [ ] Nenhum commit ou push sem autorização explícita.
- [ ] CI remoto fica como `não verificado` enquanto não houver push e número da run.

## Relatório obrigatório

Siga os sete itens de `AGENTS.md`:

1. branch, SHA, quantidade de arquivos e confirmação de commit/push;
2. cada checkbox acima com evidência concreta e números;
3. decisões tomadas sozinho e por quê;
4. divergências entre documentação e realidade;
5. bugs encontrados, inclusive fora do escopo;
6. o que ficou de fora;
7. decisões necessárias do Marcondes.

Inclua também:

- ação necessária no Dokploy antes do próximo deploy: expectativa `nenhuma`;
- resultado do teste de regressão do ciclo e o arquivo alterado;
- roteiro de produção ainda pendente, caso o Marcondes não o tenha executado;
- CI remoto com número da run somente se houver push autorizado.

---

## Fora desta etapa

- criação de lead/contato novo no WhatsApp;
- mudança de política de opt-in, template ou janela do WhatsApp;
- redesign visual amplo;
- novos eventos WebSocket;
- deploy ou alteração de configuração de produção;
- validação visual declarada como concluída pelo agente.
