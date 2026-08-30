# Prompt E76 — Subir a `hmlgc` localmente para validação visual

## Objetivo

Iniciar a aplicação da branch `hmlgc` localmente e abrir o navegador para que o Marcondes faça a validação visual da E75 antes de qualquer commit, push ou promoção.

Esta é uma etapa operacional de validação. Não implemente novas funcionalidades, não altere o código visual e não faça commit, push, merge ou deploy.

## Árvore obrigatória

Use exclusivamente:

- branch: `hmlgc`;
- worktree: `C:\Users\marcondes\Desktop\projeto_matriz-hmlgc`.

Antes de iniciar serviços:

1. confirme branch, `HEAD` e `git status --short --branch`;
2. confirme que o merge da `fixtwo` já está concluído;
3. confirme que as alterações não commitadas da E75 estão presentes;
4. preserve `frontend/.playwright-cli/` e `frontend/output/`;
5. não execute comandos destrutivos e não altere `main`, `hotfix` ou `fixtwo`.

Se houver merge em andamento ou conflito, pare e informe. Não faça limpeza ou reset para conseguir iniciar a aplicação.

## Leitura obrigatória

Leia integralmente, nesta ordem:

1. `AGENTS.md`;
2. `docs/13-estado-do-projeto.md`;
3. `docs/prompts/COMO-ESCREVER-PROMPTS.md`;
4. `docs/prompts/prompt-E75-correcao-visual-e-validacao-E74.md`.

## Preparação local

Reutilize os serviços locais já existentes quando estiverem disponíveis. Antes de criar qualquer container ou processo, verifique:

- Docker Desktop;
- Postgres;
- Redis;
- MinIO;
- RabbitMQ;
- porta HTTP `8080`;
- porta frontend `3000`;
- Java 21;
- Node/npm compatíveis com o projeto.

Não derrube containers de outros projetos nem sobrescreva banco existente. Se for necessário usar dados locais, prefira o banco temporário da homologação já utilizado na `hmlgc`. Não exponha senhas no relatório final.

## Backend local

Se o backend da `hmlgc` não estiver rodando, construa e inicie a aplicação com Java 21 usando o procedimento já validado no worktree. O backend deve ficar acessível em:

```text
http://localhost:8080
```

Confirme antes de prosseguir:

```text
GET http://localhost:8080/health/liveness
```

Se o backend exigir banco, Redis, MinIO ou RabbitMQ, confirme os serviços e suas portas reais a partir da configuração local. Não assuma a porta 5432 para Postgres.

## Frontend local

Instale dependências somente se necessário, sem modificar deliberadamente versões ou lockfile. Inicie o frontend da `hmlgc` em:

```text
http://localhost:3000
```

Para a validação local do tempo real, use a configuração já documentada:

```text
NEXT_PUBLIC_API_URL=http://localhost:8080
NEXT_PUBLIC_WS_URL=ws://localhost:8080/ws
```

Não grave segredo no repositório. Não altere o comportamento de produção para resolver uma limitação local.

Se a aplicação já estiver rodando, não inicie uma segunda instância na mesma porta sem antes identificar o processo existente. Se precisar reiniciar um processo criado por esta etapa, encerre somente o processo identificado como pertencente à `hmlgc`.

## Navegador

Abra o navegador real na aplicação local e deixe a sessão disponível para o usuário. Se houver credenciais de desenvolvimento já previstas no ambiente, utilize-as sem registrar senha no relatório.

Abra inicialmente:

```text
http://localhost:3000/administracao
```

Deixe também acessíveis as seguintes rotas para a conferência:

- `/administracao`;
- `/administracao/acessos`;
- `/administracao/feedbacks`;
- `/atendimentos`;
- rota real da Agenda;
- `/feedbacks`;
- modal de Novidades & Em Breve.

Não preencha dados fictícios e não altere registros reais só para produzir uma tela mais cheia. Use apenas dados de desenvolvimento já existentes.

## Roteiro para o usuário validar

Deixe o usuário verificar:

1. fonte Hanken Grotesk em títulos, menus, labels, cards, tabelas e botões;
2. proporção do cabeçalho e da sidebar;
3. canvas azul-acinzentado e superfícies brancas;
4. espaçamento, bordas, raios e sombras dos cards;
5. navegação interna selecionada;
6. tabela de acessos, avatares, badges e ações;
7. estado vazio de Feedbacks;
8. estados hover, selected, focus-visible e destructive;
9. modal de Novidades & Em Breve;
10. comportamento em desktop e mobile;
11. ausência de scrollbar horizontal e clipping;
12. ausência de reconexão WebSocket repetitiva em Atendimento.

Valide ao menos nos viewports `1440x900`, `1024x768` e `390x844`. Não encerre os processos antes que o usuário confirme que terminou a inspeção.

## Segurança operacional

- Não faça commit, push, merge ou deploy.
- Não altere `.env`, secrets, configurações do Dokploy ou infraestrutura compartilhada sem autorização.
- Não apague containers, volumes, bancos, imagens ou artefatos de outras etapas.
- Não altere arquivos da E75 durante a validação.
- Não mascare erro do console: registre se ainda houver falha de WebSocket, HTTP, autenticação ou carregamento.
- Não declare validação visual concluída sem o usuário confirmar a inspeção.

## Relatório intermediário obrigatório

Depois de iniciar a aplicação, informe:

1. branch, worktree e `HEAD`;
2. status da árvore, incluindo os arquivos não rastreados preservados;
3. serviços iniciados ou reutilizados e suas portas;
4. URL do frontend e URL do backend;
5. rota aberta no navegador;
6. usuário de desenvolvimento usado, sem informar senha;
7. resultado do health check;
8. resultado da conexão WebSocket;
9. erros de console ou rede encontrados;
10. confirmação de que não houve alteração de código, commit, push, merge ou deploy.

Mantenha a aplicação aberta para a validação do usuário. Após a confirmação visual dele, aguarde nova instrução antes de encerrar os serviços ou preparar o commit.
