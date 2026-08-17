# Prompt E29 — Primeiro acesso e troca de senha

> Leia `AGENTS.md`. Entrega em 25/08.
> Commite e faça push por bloco.
> Ao encerrar: `cd backend && ./mvnw clean verify` e o **número da run** do CI.

---

## O que não existe

Busca no `backend/` por `AlterarSenha`, `TrocarSenha`, `RedefinirSenha`, `senha_provisoria`,
`deve_trocar`, `primeiro_acesso`: **nenhuma ocorrência**. A tabela `usuario` tem `id`, `nome`,
`email`, `senha_hash`, `papel`, `status_presenca`, `ativo`, `criado_em` — nada de controle de
senha. Os documentos de domínio e de dados também não preveem.

Hoje a senha nasce no provisionamento e **não há caminho para trocá-la** — nem pelo dono, nem
pelo gestor. No go-live o gestor entrega a senha ao atendente pela mão; sem troca obrigatória,
a senha de trabalho de quem disputa comissão fica conhecida por outra pessoa
indefinidamente.

Esta etapa cria as duas coisas: o fluxo de primeiro acesso e a troca em qualquer momento.

---

## Bloco 1 — O estado da senha vive no banco, e o bloqueio é do servidor

Migration nova: `usuario.senha_alterada_em TIMESTAMPTZ NULL`.

`NULL` significa **senha nunca trocada pelo dono** — ou seja, ela é provisória e alguém além do
usuário a conhece. Preferi um instante a um booleano porque ele responde "quando", serve para
auditoria e permite política de expiração depois, sem outra migration.

**Os usuários existentes ficam com `NULL`.** Eles receberam senha de terceiros; tratá-los como
já trocada seria mentir sobre o único fato que a coluna representa. Consequência prática, que
vai no relatório: no próximo login **todo mundo cai na tela de troca**, inclusive o
administrador.

**O bloqueio é no backend, não na tela.** Enquanto `senha_alterada_em IS NULL`, o login funciona
e emite token normalmente, mas qualquer endpoint autenticado que não seja o de troca de senha
(nem o de logout) responde **403** com Problem Details apontando o caminho a seguir. Só o
frontend redirecionar não protege nada: o token é válido e a API responderia a qualquer chamada
direta.

## Bloco 2 — Trocar a própria senha

`POST /api/v1/auth/senha` — senha atual + nova. Vale para os dois casos: primeiro acesso e troca
voluntária. Exigir a atual mesmo no primeiro acesso é o que impede que um token vazado troque a
senha do dono.

- Política de senha vem de configuração, com default — **sem número fixo no código**. Recusa
  com Problem Details que diga a regra, não um "senha inválida" seco.
- Nova senha igual à atual é recusada.
- Ao trocar, `senha_alterada_em = now()` e **as demais sessões do usuário são revogadas** — a
  família de refresh tokens já é revogável por `EncerrarSessaoUseCase`. Trocar senha sem
  derrubar as outras sessões deixa em pé exatamente a sessão de quem você quer excluir.
- A sessão que fez a troca continua válida; obrigar novo login logo depois de trocar é atrito
  sem ganho.
- `@Auditable`. **Nunca** logar ou devolver senha, hash ou fragmento.

## Bloco 3 — O gestor devolve o acesso de quem esqueceu a senha

Não há servidor de e-mail nesta instância, então "recuperar senha por link" não é opção. O
caminho realista é o gestor gerar uma senha provisória e entregá-la.

`POST /api/v1/usuarios/{id}/senha-provisoria`

- Autorizado a **ADMINISTRADOR e GESTOR**, declarado com `@PreAuthorize` como todo caso de uso.
- Gera senha aleatória, grava o hash, e põe `senha_alterada_em = NULL` — o alvo cai no fluxo de
  primeiro acesso e é obrigado a trocar.
- Revoga todas as sessões do alvo. Alguém que esqueceu a senha pode ter sido comprometido.
- A senha em claro é devolvida **uma única vez** na resposta, para o gestor repassar. Não é
  persistida em claro, não vai para log, não aparece em auditoria.
- `@Auditable`, registrando quem resetou a senha de quem. É poder sobre a conta alheia num
  sistema onde conta é carteira de comissão.

> **Ponto de parada.** Se você concluir que a senha provisória deveria ser digitada pelo gestor
> em vez de gerada pelo sistema, **pare e me avise** — é decisão de produto, e senha escolhida
> por outra pessoa tende a ser fraca e reutilizada entre atendentes.

## Bloco 4 — As telas

- **Tela de troca de senha**, alcançada de duas formas: redirecionamento automático quando a
  senha é provisória, e item no menu do usuário para troca voluntária.
- Quando é primeiro acesso, a tela explica **por que** está ali — sem isso o atendente acha que
  o sistema quebrou.
- Enquanto a senha for provisória, o redirecionamento vale para qualquer rota: digitar
  `/atendimentos` na barra não pode escapar.
- **Ação de gerar senha provisória na tela de Equipe**, visível apenas a quem pode. A senha
  gerada aparece uma vez, com botão de copiar e aviso de que não será mostrada de novo.
- Textos do catálogo, cores por token. Nenhuma string literal de UI, nenhuma cor fixa.

---

## Testes — a proteção nasce com um teste que a viola

- **O negativo que sustenta a etapa:** usuário com `senha_alterada_em IS NULL` autentica, recebe
  token válido, e ao chamar `GET /api/v1/atendimentos` recebe **403** — não 200. Feito pelo
  endpoint real, com o token real. É este teste que prova que o bloqueio não mora só na tela.
- O mesmo usuário **consegue** chamar o endpoint de troca de senha e o de logout.
- Depois de trocar, o mesmo token passa a alcançar os endpoints normalmente.
- Senha atual errada é recusada; nova igual à atual é recusada; senha fora da política é
  recusada com a regra na mensagem.
- Trocar a senha revoga as demais sessões: um refresh token emitido antes deixa de renovar.
- Reset por gestor: ATENDENTE recebe 403 ao tentar resetar a senha de outro; GESTOR consegue;
  o alvo passa a ter `senha_alterada_em IS NULL` e suas sessões morrem.
- Nenhuma resposta e nenhum log contêm senha ou hash — inclusive nos caminhos de erro.

## Definição de pronto

- [ ] `usuario.senha_alterada_em` com os existentes em `NULL`
- [ ] 403 no servidor para qualquer endpoint que não seja troca/logout enquanto a senha é provisória
- [ ] `POST /api/v1/auth/senha` com política configurável e revogação das demais sessões
- [ ] `POST /api/v1/usuarios/{id}/senha-provisoria` restrito a ADMINISTRADOR e GESTOR, auditado
- [ ] Tela de troca, redirecionamento à prova de digitar a rota, e ação na tela de Equipe
- [ ] Os testes acima, com o negativo do 403 pelo endpoint real
- [ ] `docs/02`, `docs/03` e `docs/11` atualizados com a coluna nova
- [ ] CI verde com **número da run**

## No relatório

Diga qual política de senha ficou como padrão e onde ela é configurada.

Diga explicitamente que, no primeiro login após o deploy, **todos os usuários existentes serão
levados à troca de senha** — inclusive o administrador. Quem opera precisa saber disso antes de
subir, não durante.

Diga se alguma variável nova precisa entrar no Dokploy, ou afirme que nenhuma precisa.
