# Prompt E31b — Logo da instância e pendências da E29

> Leia `AGENTS.md`. Entrega em 25/08.
> Blocos em ordem de urgência. Commite e faça push por bloco.
> Ao encerrar: `cd backend && ./mvnw clean verify` e o **número da run** do CI.

O Bloco 1 é o que está sendo cobrado agora. Os demais são pendências abertas da E29, todas
pequenas, e a 2 é a mais grave delas.

---

## Bloco 1 — A marca da instância aparece no favicon

A E31 ligou o `logoUrl`: quando preenchido, ele alimenta o favicon e a marca da sidebar; quando
nulo, cai no quadrado com gradiente. O que ficou faltando é **onde o arquivo mora** — a etapa não
registrou essa decisão, e `logoUrl` continua nulo.

### A decisão, tomada

O arquivo vai para **`backend/crm-app/src/main/resources/`**, ao lado do `tema.json` e do
`textos.json`, e é servido por uma rota pública de configuração — algo como
`GET /api/v1/config/logo`, irmã de `/api/v1/config/tema` e `/api/v1/config/textos`, que já são
públicas no `SecurityConfig`. O `logoUrl` do `tema.json` passa a apontar para essa rota.

Considerei servir pelo MinIO, sob o `MIDIA_DOMINIO`, o que permitiria trocar o logo sem rebuild.
Recusei por duas razões: o storage hoje entrega URL assinada com expiração de 5 minutos
(`MIDIA_S3_EXPIRACAO_LEITURA`), e favicon precisa de URL estável — seria preciso criar um bucket
com leitura anônima, separado do que guarda mídia de conversa, e isso não se faz com pressa sem
arriscar tornar público o que não deve ser. E porque a limitação que isso resolveria já existe:
o `ConfiguracaoDeInstanciaResources` lê `tema.json` do classpath, uma vez, na subida — trocar
qualquer coisa do tema já exige rebuild hoje.

Registre no relatório que servir a marca pelo storage, sem rebuild, continua sendo o desenho
melhor para o segundo filho.

### O que fazer

- Rota pública de logo, ao lado das outras de configuração, com o `Content-Type` correto e
  cache de longa duração.
- **A ausência do arquivo não pode derrubar a aplicação.** Diferente de `tema.json` e
  `textos.json`, cuja falta faz a subida falhar de propósito, um filho sem logo é caso normal:
  sem arquivo, a rota responde 404 e o front cai no fallback que já existe.
- `logoUrl` no `tema.json` apontando para a rota.
- Nenhuma referência ao nome do cliente em código, nome de arquivo ou comentário. O arquivo se
  chama `logo.png`, como `tema.json` se chama `tema.json`.

Teste: com o arquivo presente, a rota devolve 200 e o tipo certo; sem ele, devolve 404 e a
aplicação sobe normalmente.

**Ação de operação, no relatório:** o arquivo do logo precisa ser colocado em
`backend/crm-app/src/main/resources/` antes do build que vai para homologação.

---

## Bloco 2 — O provisionamento devolve senha de terceiro sem exigir troca

A E29 criou `usuario.senha_alterada_em`: nulo significa senha nunca trocada pelo dono, e
enquanto for nulo o backend recusa qualquer endpoint que não seja troca de senha ou logout.

O `provisionar-instancia.sql` **não conhece essa coluna** — nenhuma ocorrência no arquivo. E ele
faz `ON CONFLICT (email) DO UPDATE SET ... senha_hash = EXCLUDED.senha_hash`. Reexecutar o
provisionamento, coisa que a operação faz para reconciliar canal, etapas e flags, **reescreve a
senha do administrador a partir da variável de ambiente e deixa `senha_alterada_em` intacta**.

O resultado é uma senha definida pelo operador que o sistema trata como se fosse do dono. O
bloqueio de primeiro acesso não dispara nesse caminho — a proteção existe e não cobre a porta
por onde a senha realmente entra.

- Quando o provisionamento **grava um `senha_hash` diferente do que estava lá**,
  `senha_alterada_em` volta a `NULL`.
- Quando o hash não muda, não mexa: reexecutar o provisionamento por causa de etapas ou flags
  não pode obrigar o administrador a trocar a senha à toa.
- Documente o efeito no `docs/18`: reexecutar o provisionamento com um `SYNAPSE_ADMIN_SENHA_HASH`
  novo redefine a senha do administrador e exige troca no próximo login.

Teste, em banco descartável: com hash novo, a coluna volta a `NULL`; com o mesmo hash, permanece
como estava.

## Bloco 3 — Ninguém devolve o acesso do administrador

A E29 restringiu `POST /api/v1/usuarios/{id}/senha-provisoria` a alvos ATENDENTE e SUBGESTOR,
seguindo o mesmo recorte da gestão de equipe. A decisão está certa, mas cria um beco: **se o
administrador esquecer a senha, não existe caminho pelo produto** — só `UPDATE` no banco.

Escolha e **justifique no relatório**: permitir que um ADMINISTRADOR gere senha provisória para
outro ADMINISTRADOR, ou assumir que o caminho é operacional e documentá-lo no `docs/18` com o
comando exato.

A primeira opção não vale nada se houver um administrador só na instância — que é o caso hoje.
Pense nisso antes de escolher.

## Bloco 4 — A política de senha tem um furo e uma variável não documentada

`UsuarioController.Criacao` continua com `@Size(min = 8, max = 100)` fixo. Configurar
`SYNAPSE_SENHA_TAMANHO_MINIMO` como 12 não impede o gestor de criar usuário com senha de 8: a
política vale na troca e não vale na criação.

- A criação de usuário passa a usar a mesma `PoliticaDeSenha` dos outros dois endpoints.
- `SYNAPSE_SENHA_TAMANHO_MINIMO` entra no `.env.example` e na tabela de variáveis do `README.md`.
  A E29 a deixou de fora alegando precedente de variáveis irmãs — mas o `AGENTS.md` é explícito,
  e o precedente é o erro que se propagou, não a norma. Se as irmãs também faltarem,
  acrescente-as.
- Menor, no mesmo bloco: o construtor de dois argumentos de `UsuarioAutenticado` existe só para
  os testes. Torne-o pacote-privado, para que ninguém o use em produção e perca o campo de senha
  provisória sem perceber.

Teste: criar usuário com senha abaixo do mínimo configurado é recusado, com a regra na mensagem.

---

## Definição de pronto

- [ ] Rota pública de logo, com 404 quando o arquivo não existe e a aplicação subindo assim mesmo
- [ ] `logoUrl` apontando para ela; nenhuma menção ao cliente em código ou nome de arquivo
- [ ] Provisionamento zera `senha_alterada_em` quando troca o hash, e só nesse caso
- [ ] Caminho de recuperação do administrador decidido, implementado ou documentado
- [ ] Criação de usuário usando a política configurável
- [ ] `.env.example` e `README.md` com a variável
- [ ] Construtor de dois argumentos pacote-privado
- [ ] Testes dos blocos 1, 2 e 4
- [ ] `docs/18` atualizado com o efeito do provisionamento sobre a senha
- [ ] CI verde com **número da run**

## No relatório

A ação de operação do Bloco 1: colocar o arquivo do logo em
`backend/crm-app/src/main/resources/` antes do build de homologação.

A escolha do Bloco 3, com o porquê.

Se alguma variável nova precisa entrar no Dokploy, ou a afirmação de que nenhuma precisa.
