# Prompt E94 — escala dos ícones da interface: +15%

> Leia `AGENTS.md`, `CLAUDE.md` e `docs/13-estado-do-projeto.md`.
> Branch própria (`feat/escala-de-icones`) e PR. **Não faça merge nem deploy.**
> Só frontend. Nenhuma rota, contrato, migration ou regra de negócio muda.

---

## O pedido

**Ícones da interface: 15% maiores.**

A barra lateral **fica de fora desta etapa** — os ícones dela diminuem, e isso vai junto com a
mudança do rodapé, na **E95**, porque é o mesmo arquivo. Não toque em `components/shell/`
aqui: as duas etapas rodam em branches separadas e encostar no mesmo arquivo garante conflito.

Parece trivial. Não é, por dois motivos — e é por causa deles que esta etapa existe em vez de ser
um "aumenta aí".

## Bloco 1 — 15% não é um passo do Tailwind, e isso é o problema central

Os ícones hoje usam utilitários de tamanho do Tailwind (`size-4`, `size-5`, `size-3.5`). A escala
anda de 4 em 4 pixels: `size-4` são 16px, `size-5` são 20px. **Subir um passo é +25%, não +15%.**
Descer um passo é −20%. Nenhum dos dois é o que foi pedido.

Então **não** resolva subindo ou descendo passo, e **não** espalhe valor arbitrário (`size-[18px]`,
`size-[1.15rem]`) pelos componentes: o `CLAUDE.md` proíbe número solto no código, e este produto é
multi-instância — tamanho cravado em 40 arquivos é impossível de reverter ou ajustar depois.

**Faça com dois tokens**, declarados em um lugar só (junto dos demais tokens em `globals.css`):

- um para o ícone padrão da interface;
- um para o ícone da barra lateral.

Os componentes passam a referenciar o token, não o número. Confirme no `package.json` a versão do
Tailwind e use a sintaxe de valor por variável CSS que essa versão suporta — **confirme, não presuma**;
se a sintaxe que você tentar não compilar, diga no relatório qual foi e o que usou no lugar.

Assim, "mais 15%" vira uma linha, e o próximo pedido de ajuste também.

## Bloco 2 — "ícones em geral" é escopo perigoso; delimite antes de mexer

Antes de editar qualquer coisa, **levante e liste no relatório** onde há ícone: composer, cabeçalho da
conversa, cartões da lista, botões de ação, estados vazios, selos, painel do lead, telas de gestão.

Depois aplique com cuidado nestes pontos, que são onde 15% quebra layout:

- **Linhas de altura fixa.** O composer e o cabeçalho da conversa têm altura fixa; ícone maior dentro
  deles pode desalinhar verticalmente ou estourar. Confira, não confie.
- **Ícone dentro de botão** (`buttonVariants` com `size="icon"` e `size="icon-sm"`): a caixa do botão
  não cresce junto. Ícone maior em caixa pequena encosta na borda ou corta.
- **Ícone junto de texto**: o alinhamento com a linha de base muda. É o lugar onde fica feio sem
  ninguém saber dizer por quê.
- **Não mexa em avatar.** Avatar não é ícone; `AvatarIniciais` e as fotos ficam como estão.
- **Não mexa em ícone dentro de conteúdo de mensagem** nem na prévia do WhatsApp, que imita um app
  externo e não segue a escala do produto.

## Bloco 3 — Verificação visual é obrigatória, e é o teste desta etapa

Mudança puramente visual não é pega por teste unitário. Um ícone cortado dentro de um botão passa em
todos os testes do projeto.

Suba a aplicação de verdade — backend e frontend — e capture, em viewport de desktop:

1. Atendimentos com uma conversa aberta (composer, cabeçalho, cartões da lista);
2. uma tela de gestão qualquer (Automação ou Equipe);
3. um botão de ícone pequeno em close, mostrando que o glifo não encosta na borda.

Se não conseguir subir o ambiente, **pare e relate** — não entregue esta etapa sem prova visual.
Numa etapa anterior a validação visual falhou porque a API local não estava no ar, e o resultado foi
uma regressão que só apareceu depois.

Além disso: `npm run lint`, `npm run typecheck`, `npm test` e `npm run build` verdes.

## O que não fazer

- Nada de valor arbitrário espalhado; a escala vive nos dois tokens.
- Nada de trocar biblioteca de ícone nem substituir ícone por outro.
- Nada fora do `frontend/`, e **nada dentro de `components/shell/`** — a sidebar é da E95.
- Não "aproveite para" ajustar espaçamento, cor ou peso de fonte. Se algo parecer errado, relate; não
  corrija de passagem — mudança visual não pedida é impossível de revisar junto com a pedida.

---

## Relatório

1. Os dois tokens: nome, valor final em px e a sintaxe que funcionou na versão do Tailwind do projeto.
2. O levantamento do Bloco 2: onde havia ícone e o que ficou de fora, com o motivo.
3. As capturas do Bloco 3.
