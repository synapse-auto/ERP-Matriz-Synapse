# Prompt E69 — isolar a E65 na `fixtwo` e validar a entrega

> Leia `AGENTS.md`, `docs/13-estado-do-projeto.md` e `docs/prompts/COMO-ESCREVER-PROMPTS.md` antes de alterar qualquer arquivo.
>
> Esta é uma etapa de correção e aceite da E65. Trabalhe exclusivamente no worktree
> `C:\Users\marcondes\Desktop\projeto_matriz-fixtwo`, na branch `fixtwo`. A branch foi criada limpa a
> partir de `main`/`origin/main` em `43bf65e`.
>
> Não trabalhe em `main` ou `hotfix`. Não use os commits posteriores da E67 como parte desta etapa:
> `06aae4d`, `f1f5c45` e `4a180ab`. A E65 é somente o conjunto `e3bcbdd`, `f69066c`, `91d3b81` e
> `45659db`, mas o relatório desses commits não é evidência: confira cada alteração no diff e nos
> testes antes de reaplicá-la na `fixtwo`.
>
> Não faça `git reset --hard`, não descarte alterações, não remova prompts não rastreados e não faça
> commit ou push sem autorização explícita do Marcondes. Ao terminar, informe o estado real da branch,
> os testes executados e o que ainda depende de autorização.

---

## Contexto — a E65 não pode ser aceita misturada na `hotfix`

O relatório anterior informou quatro commits locais da E65:

- `e3bcbdd` — aviso de transferência dispensável;
- `f69066c` — retração da sidebar principal;
- `91d3b81` — retração do painel de detalhes do lead;
- `45659db` — destaque das mensagens programadas.

A revisão confirmou que esses commits existem na `hotfix`, mas a branch também contém alterações
posteriores. A `main`/`origin/main` continua em `43bf65e`. Portanto, esta etapa precisa produzir na
`fixtwo` somente a base da E65, sem incorporar E67/E68 ou qualquer correção não solicitada.

O código relevante da E65 está nestes pontos; abra todos antes de decidir o que reaplicar:

```tsx
// frontend/src/components/atendimentos/pagina-atendimentos-cliente.tsx
const [notificacao, setNotificacao] = useState<NotificacaoTempoReal | null>(null);
const notificacoesProcessadas = useRef(new Set<string>());
const [painelDetalhesAberto, setPainelDetalhesAberto] = useState(true);
```

```tsx
// frontend/src/components/atendimentos/cabecalho-conversa.tsx
{!painelDetalhesAberto && (
  <Button
    type="button"
    aria-expanded="false"
    aria-controls="painel-detalhes-lead"
    onClick={onAlternarPainelDetalhes}
  >
    <PanelRightOpen aria-hidden />
  </Button>
)}
```

```tsx
// frontend/src/components/atendimentos/painel-da-conversa.tsx
<Button
  type="button"
  aria-expanded="true"
  aria-controls="painel-detalhes-lead"
  onClick={onRetrair}
>
  <PanelRightClose aria-hidden />
</Button>
```

O cartão da programada deve continuar sendo o cartão da entidade real, e não um mock:

```tsx
<div className="... bg-primary/5 ...">
  {/* mensagem, data, editar e remover da programada real */}
</div>
```

## Bloco 1 — preparar a `fixtwo` sem contaminar a entrega

- Confirme branch, worktree, `HEAD`, `origin/main`, status e diff antes de alterar arquivos.
- Confirme que a `fixtwo` está em `43bf65e` ou registre qualquer mudança encontrada e pare antes de
  incorporá-la.
- Compare os quatro commits da E65 com a base `43bf65e`.
- Reaplique somente o patch necessário da E65 na `fixtwo`, preservando a autoria/histórico existente
  e sem copiar os commits posteriores da `hotfix`.
- Não altere `backend`, migrations, contratos, autorização ou banco, exceto os textos/tipos que a E65
  realmente exigir. Como a E65 usa catálogo de textos, confirme se o arquivo de textos e o schema
  precisam acompanhar o patch.
- Não inclua `Novidades`, `Administração`, ficha do lead, novo contato WhatsApp, chat interno ou outras
  tarefas posteriores.

> **Não faça cherry-pick cego da `hotfix`.** A branch contém E67 e pode carregar defeitos de outra etapa.
> Extraia e confira somente o intervalo funcional da E65.

> **Ponto de parada.** Se não for possível separar E65 de E67 sem conflito semântico ou se a base
> `43bf65e` não for a base correta, pare e relate os hashes e o conflito. Não resolva escolhendo
> silenciosamente outra base.

## Bloco 2 — confirmar o aviso realmente dispensável

Em `frontend/src/components/atendimentos/pagina-atendimentos-cliente.tsx` e no adaptador de tempo real:

- Confirme o ponto de entrada real da notificação: conexão, assinatura, frame recebido, renderização,
  clique em fechar e invalidação de `atendimentos`.
- O botão de fechar deve remover o aviso do DOM com um clique normal, sem exigir pressionamento longo.
- Abrir a transferência deve limpar o aviso antes da navegação/abertura.
- O mesmo frame repetido por reconexão, re-render ou invalidação não pode ressuscitar o aviso.
- Um evento novo, distinguível pelos campos reais do contrato, deve continuar aparecendo.
- Preserve a expiração configurável. O timer é somente apresentação e não altera estado persistido.
- Não invente `id` no frontend nem descarte eventos reais por uma heurística não documentada.

O teste deve passar pelo fake STOMP/adaptador usado no projeto, não somente por um setter:

- emite `TRANSFERENCIA_RECEBIDA`;
- confirma aviso visível;
- clica no controle de fechar e confirma ausência no DOM;
- invalida a query, renderiza novamente e emite a duplicata;
- confirma que o aviso continua ausente;
- emite ocorrência nova e confirma que ela aparece;
- cobre `ATENDIMENTO_DEVOLVIDO_PARA_IA`, abrir atendimento e expiração configurada.

> **Não mascare o defeito** com `opacity`, `display`, z-index, timeout maior ou remoção do botão.

## Bloco 3 — confirmar as duas retrações sem perder funcionalidade

### Sidebar principal

- O estado deve permanecer no dono do shell, hoje `frontend/src/components/shell/shell-com-sidebar.tsx`.
- A `Sidebar` recebe o estado e o callback; não use mutação de DOM, seletor global ou evento global.
- Aberta: marca, rótulos, links, item ativo e badge permanecem visíveis.
- Retraída: ícones, links, item ativo, badge, configurações, presença e logout continuam acessíveis.
- O botão tem `aria-expanded`, nome do catálogo e `title` coerente para retrair/reabrir.
- O popup de presença não pode ficar cortado ou inacessível no estado retraído.
- Não crie persistência, endpoint, coluna ou feature flag para a preferência.

### Painel de detalhes do lead

- O estado deve permanecer em `pagina-atendimentos-cliente.tsx`.
- Aberto: a grade contém a terceira coluna e o `PainelDaConversa`.
- Retraído: a terceira coluna deixa de participar da grade e o chat ocupa o espaço liberado.
- O mesmo lead, histórico, composer e scroll devem permanecer selecionados ao reabrir.
- Trocar de lead, atualizar a inbox, abrir transferência ou receber evento de tempo real não pode
  reabrir o painel por acidente.
- O controle de reabertura deve aparecer no cabeçalho da conversa quando o painel estiver fechado.
- Conversa `EQUIPE_INTERNA` não pode receber painel, controles, tags, transferência ou finalização de
  cliente.
- Não confunda com `PainelLateralLead` da Agenda, que deve manter seu próprio fechamento.

Teste os negativos, não somente a presença do botão:

- foco e navegação continuam possíveis nos links icon-only;
- não há coluna invisível capturando cliques;
- o painel fechado não é renderizado;
- troca de lead preserva retração;
- conversa interna não renderiza ações de cliente;
- a Agenda não perde o fechamento do seu painel.

## Bloco 4 — confirmar o destaque das mensagens programadas

- Em `SecaoDeProgramadas`, o cartão da mensagem programada real deve usar tokens semânticos já
  existentes, como `primary`, `muted` e `border`.
- Não use cor literal, `#hex`, `rgb(...)`, classe arbitrária de cor ou estilo inline novo.
- O destaque deve atingir somente programadas; lembretes, notas, mensagens enviadas, confirmação,
  editar, remover e estado vazio permanecem neutros.
- Texto, data local, contagem e ações devem continuar legíveis em viewport desktop e estreito.
- Se a tela global de programadas possuir renderer próprio, confira se a mesma semântica visual foi
  aplicada sem criar dados ou controles fictícios.

## Testes e validação obrigatórios

Antes de declarar pronto:

- Execute os testes específicos de aviso, sidebar, painel/cabeçalho e programadas.
- Execute `npm run lint` e informe a quantidade exata de warnings, separando preexistentes de novos.
- Execute `npm run typecheck`.
- Execute `npm test -- --run`.
- Execute `npm run build`.
- Como a E65 altera catálogo de textos e o backend participa da composição, execute:
  `cd backend && ./mvnw clean verify` com Java 21 e Testcontainers.
- Se o `clean` falhar por arquivo bloqueado, informe o caminho e não declare o ciclo completo aprovado.
- Não substitua `clean verify` por `verify -DskipTests`, `mvn test` ou compilação parcial.
- Faça validação visual em navegador, registrando viewport e evidência de:
  1. aviso visível e dispensado;
  2. sidebar aberta e retraída;
  3. painel do lead aberto e retraído;
  4. conversa interna sem painel de lead;
  5. cartão de mensagem programada destacado.

## Definição de pronto

- [ ] A `fixtwo` contém somente a E65 sobre a base `43bf65e`, sem E67/E68.
- [ ] O aviso desaparece com clique normal e não ressuscita por reconexão, duplicata ou re-render.
- [ ] Evento novo continua aparecendo e o timeout configurável permanece funcionando.
- [ ] Sidebar principal retrai/reabre sem perda de links, badges, presença, configurações ou logout.
- [ ] Painel de detalhes retrai/reabre sem perda de conversa, histórico, composer ou seleção.
- [ ] Conversa interna não recebe ações/painel de cliente.
- [ ] Mensagens programadas usam destaque por tokens, sem cores literais nem dados mockados.
- [ ] Testes positivos e negativos cobrem os pontos de entrada reais.
- [ ] `lint`, `typecheck`, suíte frontend, `build` e `clean verify` foram executados e reportados com
  resultado real.
- [ ] O relatório informa branch, SHA-base, SHA-final, arquivos, commits locais, warnings, evidência
  visual, divergências, bugs e o que ficou de fora.
- [ ] Variáveis novas no Dokploy: expectativa `nenhuma`. Se surgir alguma, atualizar `.env.example`,
  README e informar nome/valor de exemplo antes de concluir.
- [ ] Sem commit ou push sem autorização explícita.

## No relatório

Use os sete itens de `AGENTS.md`. Em especial:

1. diferencie a `fixtwo` da `hotfix`, informe o SHA e confirme se houve ou não push;
2. não diga “CI verde” sem número da run;
3. liste quais commits da E65 foram reaplicados e quais foram deliberadamente excluídos;
4. informe a contagem real de warnings e qualquer falha de ambiente;
5. registre screenshots/viewport ou declare que a validação visual não foi possível;
6. informe que os prompts não rastreados foram preservados;
7. peça autorização separada para commit e push.

---

## Fora desta etapa

- Não corrigir E67/E67b, Novidades, Administração, ficha do lead ou ícones destructive.
- Não implementar fluxo de novo contato WhatsApp.
- Não alterar chat interno, backend funcional, WebSocket, outbox, migrations ou autorização.
- Não criar preferência persistente para sidebars.
- Não fazer deploy, commit ou push sem autorização explícita.
