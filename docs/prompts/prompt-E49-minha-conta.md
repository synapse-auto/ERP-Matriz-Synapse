# Prompt E49 — aba "Minha conta" (Configurações) e a engrenagem na barra lateral

> Leia `AGENTS.md`, `CLAUDE.md`, `frontend/AGENTS.md` e `design/TOKENS.md`.
> **Pode commitar localmente a qualquer momento.** Não execute `git push` sem autorização explícita.
> Referência visual: `design/componentes/Configuracoes.html`.

---

## Bloco 0 — Inventário antes de escrever JSX

**Faça isto primeiro e relate. Grande parte do que o protótipo mostra não tem backend hoje, e a
etapa depende de saber exatamente o quê.**

Responda, com o arquivo e a linha:

1. Quais colunas a tabela `usuario` tem hoje? Existe `telefone`? Existe `cargo`? (Se não existirem,
   são migration nova — e a decisão de criá-las é do Bloco 2.)
2. Existe endpoint para o próprio usuário editar os dados dele, ou só o `UsuarioController` de
   gestão? O que o `GET /me` devolve hoje (`useMeuUsuario`)?
3. `/trocar-senha` está **fora** do grupo `(shell)`. Confirme e diga o que ele usa.
4. Existe alguma rota de upload de imagem para usuário? (Há MinIO no projeto, usado para mídia de
   mensagem — não confunda: mídia de conversa não é foto de perfil.)

**Não construa nada que dependa de contrato inexistente sem dizer antes que ele não existe.**

## Bloco 1 — A engrenagem e a moldura da tela

- No rodapé da barra lateral, ao lado do bloco de perfil, entra o **ícone de engrenagem** que leva à
  tela de Configurações. O bloco de perfil continua abrindo o popup de presença que já existe — são
  dois alvos distintos, e precisam ser distinguíveis por teclado e por leitor de tela, não só por
  posição.
- A tela vive **dentro do `(shell)`**, em rota própria. Ela é do CRM do cliente, com a cor da
  instância — não é a identidade Synapse do login.
- Layout do protótipo: título "Configurações", subtítulo, e uma **sub-navegação à esquerda** com as
  seções, o conteúdo à direita.
- **Só entregue as seções que têm conteúdo real.** O protótipo mostra quatro (Perfil do usuário,
  Preferências gerais, Aparência, Ajuda e suporte). Ver o Bloco 4 antes de criar as quatro.
- Todo texto no catálogo. Nenhum literal no JSX.

## Bloco 2 — Perfil do usuário

Esta é a seção que de fato entra agora.

- **Nome** — editável pelo próprio usuário.
- **E-mail** — **não** editável nesta etapa. É a identidade de login: trocar exige confirmação no
  endereço novo, senão vira caminho de tomada de conta. Mostre em campo desabilitado, com uma linha
  explicando que a troca é pedida ao administrador. Se você achar que dá para fazer direito aqui,
  **escreva o desenho no relatório** — não implemente.
- **Telefone e Cargo** — só se as colunas existirem. Se não existirem, crie-as em **migration nova**
  (número novo, nunca editar migration aplicada), com comentário dizendo por que existem. São campos
  de exibição da equipe, não têm regra de negócio.
- **Papel** aparece como selo, **somente leitura**. Ninguém muda o próprio papel — isso é da tela de
  Equipe e do `ADMINISTRADOR`.
- Autorização **no caso de uso**: o usuário edita o **próprio** registro. Um `id` de outra pessoa no
  corpo ou na URL é recusado no servidor, não escondido na tela.
- Botão "Salvar perfil" com estado de envio, erro por campo e confirmação de sucesso.

## Bloco 3 — Alterar senha, e uma regressão para consertar de brinde

O bloco "Senha" do protótipo tem o botão "Alterar senha". **Reaproveite o `/trocar-senha` que já
existe** — não escreva um segundo formulário de troca de senha.

E aproveite para consertar isto, que é defeito conhecido: `/trocar-senha` está **fora** do grupo
`(shell)`, e desde a E45 as variáveis de tema da instância só são injetadas no layout do shell.
Resultado: `--primary` cai no padrão do `globals.css` e o botão da tela sai fora da marca do cliente.
Na Estrutural pode passar despercebido; **no próximo filho, com outra cor, a primeira tela que todo
atendente novo vê renderiza errada.**

Traga o `/trocar-senha` para dentro do `(shell)`, ou dê a ele o tema por outro caminho — sua
escolha, desde que a tela passe a carregar a cor da instância. Mantenha o fluxo de senha provisória
funcionando: quem entra com senha provisória é obrigado a trocar antes de usar o CRM, e isso não
pode depender de um menu que ele talvez não veja.

"Última alteração há 3 meses" no protótipo pressupõe guardar a data da última troca. Se o dado não
existir, **não invente**: ou omita a linha, ou crie a coluna na mesma migration do Bloco 2. Diga o
que fez.

## Bloco 4 — O que NÃO entra, e por quê

Não construa, e registre cada um em `docs/14-pendencias-de-funcionalidade.md`:

- **Alterar foto.** Não existe upload de foto de usuário. O MinIO do projeto serve mídia de
  conversa; foto de perfil é outra coisa (limites, recorte, remoção, quem pode trocar a de quem).
  Some o botão — botão que não faz nada é pior que ausência.
- **Preferências gerais (idioma, notificações).** O projeto tem **um** catálogo de textos por
  instância, não i18n por usuário. E não há preferência de notificação para guardar.
- **Aparência (tema e cores).** Aqui há um conflito de desenho que precisa de decisão, não de
  código: o tema é **da instância** (`tema.json`), e é isso que faz "trocar de cliente sem editar
  código" funcionar. Tema por usuário exige um segundo nível de sobrescrita. **Levante a pergunta no
  relatório** e não implemente.
- **Ajuda e suporte.** Precisa de conteúdo e de um canal real de suporte. Sem isso é uma página
  vazia com cara de abandono.
- **"Novidades & Em Breve" e "Administração"** aparecem na barra lateral do protótipo. **Não são
  desta etapa.** Não crie os itens.
- **A linha de versão** ("Versão 2.4.1 · Brasília/DF") só entra se vier do build. Versão chumbada no
  código mente na segunda semana, e é justamente quando alguém precisa dela para diagnosticar.

Se ao fim sobrar só uma seção com conteúdo, **entregue com uma seção**. Sub-navegação com três itens
mortos é pior que uma tela direta.

---

## Verificação

- `npm test -- --run`, `npm run lint`, `npm run build`.
- Backend: `./mvnw clean verify` **com testes**, reator inteiro.
- Teste de que o usuário não consegue editar o registro de outro, recusado **no servidor**.
- Teste de que o papel não pode ser alterado por esta tela.
- Teste de que `/trocar-senha` passa a receber as variáveis de tema da instância.
- Teste do fluxo de senha provisória continuar obrigatório.
- **Verificação visual obrigatória**, com o protótipo aberto, em desktop e celular, e nos papéis
  `ATENDENTE` e `GESTOR` — a tela é de todo mundo, não só de gestão. Se não conseguir, **diga**.
