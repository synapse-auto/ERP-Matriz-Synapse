# Prompt E14b — Verificação e provisionamento pós-deploy

> Pré-requisito: E14a concluída — a stack está no ar no Dokploy e o CRM responde em HTTPS.
> Leia `AGENTS.md` e `docs/prompts/prompt-E14a-deploy-homologacao.md` antes de começar.
> Etapa curta: meio dia. **Nenhuma linha de código de aplicação** — só scripts operacionais versionados.
> Ao terminar cada entregável, commite e faça push.

---

**Etapa E14b — Provar que o ambiente está protegido, e tornar o provisionamento repetível.**

O deploy subiu. Isso não é a mesma coisa que o deploy estar correto. Esta etapa produz os três scripts que separam "está no ar" de "está pronto para o cliente", e todos os três serão reusados em cada filho — é por isso que viram arquivo versionado em vez de comando digitado uma vez no terminal.

## Contexto do ambiente real

Duas coisas descobertas no deploy que os scripts precisam tratar:

- **O Dokploy roda um Postgres próprio.** `docker ps -qf name=postgres` casa com dois containers e quebra qualquer `docker exec`. Todo script que fale com o banco deve resolver o container pela task do Swarm (`name=_postgres.1`) e **falhar com mensagem clara se encontrar zero ou mais de um**, em vez de expandir para lixo.
- **Não existe secret manager na instância.** Os segredos vivem no ambiente da stack no Dokploy. Nenhum script pode receber segredo por argumento de linha de comando — argumento vaza no histórico do shell e em `ps`.

---

## 1. `docker/verificacao/smoke-rls.sql`

O mais importante da etapa. **Nenhum teste automatizado cobre isto**, porque no CI o usuário das migrations e o da aplicação são o mesmo — exatamente a configuração que esconde o defeito.

O risco concreto: se o `GRANT` de `synapse_app` foi para o usuário errado, ou se o usuário da aplicação é dono das tabelas, as políticas de `V12__rls_isolamento_lead.sql` **não se aplicam** e todo atendente enxerga lead de todo mundo. A tela fica idêntica. Só a consulta certa expõe.

Escreva um script que:

- Crie dois atendentes e dois leads de teste, cada lead atribuído a um atendente
- Assuma a role da aplicação com `SET LOCAL ROLE` e defina o contexto de sessão do usuário, do mesmo jeito que a aplicação faz em runtime — não como superusuário
- Consulte sob o contexto de cada atendente
- **`RAISE EXCEPTION` com mensagem explícita** se a contagem visível for maior que a esperada, nomeando qual atendente viu o que não devia
- Verifique também o caminho inverso: o gestor **deve** ver os dois
- Limpe tudo o que criou, inclusive em caso de falha

Referências: `V12__rls_isolamento_lead.sql`, `V13__role_da_aplicacao.sql`, e o `EspecificacaoVisibilidadeLead` do domínio — a política SQL e a regra de domínio precisam concordar.

Entregue junto `docker/verificacao/README.md` com o comando de execução já resolvendo o container correto.

**Antes de dar por pronto: rode o script contra um banco onde o RLS esteja propositalmente desligado e confirme que ele falha.** Um verificador que passa em tudo não verifica nada — é a nona vez que este projeto encontra esse padrão.

## 2. `docker/provisionamento/`

O seed de desenvolvimento está corretamente travado no perfil `dev` (`application.yml`, bloco final) e não roda aqui. Uma instância nova sobe vazia, e alguém preenche na mão — é onde se esquece um item na terceira repetição.

Crie `provisionar-instancia.sql`, idempotente, parametrizado por `\set`:

- Usuário administrador — **hash BCrypt recebido por variável, nunca senha em texto no arquivo, nunca hash fixo commitado**
- Etapas do funil, com nome, ordem e cor
- Tags iniciais
- `configuracao_automacao` com faixas válidas
- Limites de mídia, com os valores atuais como default explícito e comentário de que precisam ser conferidos contra a documentação da Meta

**Nada da Estrutural Vidros hardcoded.** Este arquivo é do PAI; os valores do filho entram por parâmetro. Se algum valor não puder ser parametrizado sem virar um monstro, prefira deixar um arquivo de exemplo `instancias/estrutural-vidros.exemplo.env` do que embutir.

Entregue `docker/provisionamento/README.md` com: como gerar o hash BCrypt sem deixar a senha no histórico, como executar, e como verificar que funcionou.

## 3. `docker/backup/`

- `pg_dump` comprimido para S3-compatível, **de hora em hora** — não de 4 em 4. Duas horas de conversa perdida é dano real ao cliente
- Retenção mínima de 7 dias, com expurgo do que passar disso
- Credenciais exclusivamente por variável de ambiente
- O script deve **falhar ruidosamente** se o upload não confirmar. Backup que falha em silêncio é pior que não ter backup, porque cria confiança falsa
- Registre em log o tamanho do dump: queda abrupta de tamanho é o sintoma de dump truncado

`docker/backup/RESTAURAR.md` com o procedimento completo. Com 20 migrations e a role `synapse_app`, `pg_restore` puro não basta — documente a ordem exata, incluindo o que fazer com a role e com as partições de `mensagem`.

Documente também como agendar no Dokploy (Schedules).

## Definição de pronto

- [ ] `smoke-rls.sql` rodando no ambiente real de homologação **e comprovadamente falhando quando o RLS está desligado**
- [ ] `provisionar-instancia.sql` idempotente, sem nada do cliente hardcoded, sem segredo no arquivo
- [ ] Script de backup rodando, com falha ruidosa no upload
- [ ] `RESTAURAR.md` escrito **e a restauração testada de verdade**, num banco novo, com a aplicação subindo em cima
- [ ] Todo script resolve o container do Postgres sem colidir com o do Dokploy
- [ ] Os três `README.md` escritos
- [ ] Commit e push

Commit: `chore: verificação de RLS, provisionamento e backup da instância`.

Ao terminar, me diga: o `smoke-rls.sql` passou ou falhou no ambiente real, e quanto tempo levou a restauração de ponta a ponta. Se ele falhou, **pare tudo e me avise antes de seguir** — vazamento de lead entre atendentes é incidente comercial, não bug técnico.
