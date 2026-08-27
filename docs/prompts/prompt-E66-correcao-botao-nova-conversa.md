# Prompt E66 — corrigir o botão Nova conversa que só funciona pressionado

> Leia `AGENTS.md`. Entrega em 25/08.
> Commite por bloco. Não faça `git push` sem autorização explícita do Marcondes.
> Ao encerrar, rode `npm run lint`, `npm run typecheck`, `npm test -- --run` e `npm run build`.
> Se tocar o backend, rode também `cd backend && ./mvnw clean verify` e informe o número da run do CI;
> sem push, escreva `CI não verificado`.

---

## Contexto — o botão desaparece quando o usuário solta o mouse

Na lista de Atendimentos, o primeiro botão `+` da imagem abre “Nova conversa” somente enquanto o
usuário mantém o clique pressionado. Ao soltar, o formulário/seleção desaparece. Isso é uma falha de
interação básica: um clique normal precisa produzir uma ação persistente até o usuário cancelar,
selecionar a pessoa ou criar a conversa.

A causa está em `frontend/src/components/atendimentos/lista-conversas.tsx`:

```tsx
const [novaInternaAberta, setNovaInternaAberta] = useState(false);

<DropdownMenu open={novaInternaAberta} onOpenChange={setNovaInternaAberta}>
  <DropdownMenuTrigger
    render={
      <Button
        type="button"
        variant="outline"
        size="icon-sm"
        aria-label={catalogo.chatInterno.novaConversa}
      />
    }
  >
    <Plus className="size-4" aria-hidden />
  </DropdownMenuTrigger>
  <DropdownMenuContent align="end">
    <DropdownMenuItem onClick={() => setNovaInternaAberta(true)}>
      {catalogo.chatInterno.novaConversa}
    </DropdownMenuItem>
  </DropdownMenuContent>
</DropdownMenu>
{novaInternaAberta && <>
  <SelectContato ... />
  <Button ... onClick={onCriarConversaInterna}>
    <Plus className="size-4" aria-hidden />
  </Button>
</>}
```

O mesmo estado representa duas coisas diferentes: o menu aberto e o formulário de nova conversa
aberto. O `onOpenChange(false)` do menu ocorre no ciclo normal de pointer/mouse e fecha também o
formulário. O `DropdownMenu` ainda contém somente uma opção real, “Nova conversa”.

A opção de criar novo contato WhatsApp continua fora desta etapa: não existe contrato nem política de
opt-in para esse fluxo. Não invente uma opção no menu para preencher a tela.

## Bloco 1 — um clique normal abre e mantém o formulário

- Corrija a composição em `lista-conversas.tsx` para que o primeiro `+` seja um botão acionável por
  clique completo (`pointerdown` + `pointerup`/`click`) e o estado permaneça aberto depois que o
  usuário soltar o botão.
- Como existe apenas uma ação real, prefira remover o `DropdownMenu` intermediário e fazer o botão
  chamar `setNovaInternaAberta(true)` diretamente. O seletor de contatos e o segundo botão `+` devem
  aparecer depois do clique.
- Se houver uma razão concreta para manter o menu, separe obrigatoriamente `menuAberto` de
  `novaInternaAberta`; o fechamento do menu nunca pode fechar o formulário já escolhido.
- Preserve a condição `chatInternoHabilitado`. Quando a flag estiver desligada, não renderize o
  botão, seletor ou qualquer controle fantasma de conversa interna.
- Preserve o contrato já existente: a lista de contatos vem de `contatosInternos`, a seleção passa por
  `onContatoInternoChange` e a criação chama somente `onCriarConversaInterna`.
- O botão de confirmar criação deve permanecer desabilitado sem contato selecionado e não pode gerar
  múltiplas conversas por um único clique.
- Se o seletor for fechado/cancelado, limpe a seleção transitória conforme a semântica já existente e
  permita abrir novamente sem recarregar a página.

> **Não corrija com `onMouseDown`, `onMouseUp`, `preventDefault` ou timeout.** A ação deve depender do
> clique semântico do botão e sobreviver ao ciclo normal de pressionar/soltar.

> **Não adicione uma opção WhatsApp fictícia.** O fluxo de novo contato WhatsApp permanece bloqueado
> até existir contrato de lead, opt-in/template e canal ativo.

## Testes — a proteção nasce com um teste que a viola

Atualize ou acrescente testes em
`frontend/src/components/atendimentos/lista-conversas.test.tsx` cobrindo o ponto de entrada da UI:

- com `chatInternoHabilitado`, o botão “Nova conversa” existe e é um único botão acessível;
- `userEvent.click` completo no primeiro `+` abre o seletor e o segundo botão de criação;
- simular explicitamente `pointerDown` seguido de `pointerUp` também mantém o seletor aberto depois
  que o ponteiro é solto;
- selecionar um contato habilita o segundo botão e um clique chama `onCriarConversaInterna` uma vez;
- sem contato selecionado, o segundo botão permanece desabilitado;
- abrir, fechar/cancelar e abrir novamente não deixa seleção ou menu preso;
- com a flag desligada, nenhum dos controles de nova conversa aparece;
- uma atualização/re-render da lista não fecha o formulário já aberto;
- não existe `DropdownMenu` controlado pelo mesmo estado do formulário, caso o menu seja removido ou
  mantido.

Use `userEvent` quando disponível para reproduzir o ciclo real de interação. Não teste apenas chamando
`setNovaInternaAberta` ou um callback interno.

Faça também uma conferência manual ou E2E na tela `/atendimentos` com a flag `chat_interno` ligada:

1. clicar uma vez no `+`;
2. soltar imediatamente;
3. confirmar que o seletor continua visível;
4. escolher um usuário;
5. criar a conversa e confirmar que ela aparece/reutiliza na inbox sem duplicação.

## Definição de pronto

- [ ] O primeiro `+` funciona com um clique comum, sem precisar manter o botão pressionado.
- [ ] O formulário/seletor continua visível após `pointerup`.
- [ ] A seleção, confirmação e criação da conversa interna continuam funcionais.
- [ ] A flag desligada não deixa controles de chat interno na tela.
- [ ] Não há estado de menu e formulário compartilhado de forma que o menu feche o formulário.
- [ ] Não foi criado fluxo fictício de novo contato WhatsApp.
- [ ] Testes cobrem clique completo, `pointerup`, re-render, flag e ausência de seleção.
- [ ] `npm run lint`, `npm run typecheck`, `npm test -- --run` e `npm run build` passam.
- [ ] O relatório informa SHA, variáveis novas no Dokploy (expectativa: nenhuma), decisões,
      divergências, bugs, fora de escopo e resultado da validação manual/E2E.
- [ ] CI só é chamado de verde com o número da run; sem push, registrar `CI não verificado`.

---

## Fora desta etapa

- Não alterar backend, banco, WebSocket, contrato da inbox ou feature flag.
- Não implementar novo contato WhatsApp.
- Não redesenhar a sidebar, o painel do lead ou o fluxo de seleção de contatos.
- Não commitar nem enviar os prompts não rastreados existentes automaticamente.

