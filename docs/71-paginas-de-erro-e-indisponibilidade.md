# Páginas de erro e limites de disponibilidade

## Dentro da aplicação

O catch-all antigo `(shell)/[...slug]` retornava “em construção” até para endereços inexistentes, com 200 ou redirecionamento de sessão. Foi substituído por rotas explícitas de Banco de Arquivos, Horários e Relatórios, preservando os placeholders e flags atuais sem aceitar URLs arbitrárias.

O retorno usa histórico apenas quando existe página anterior conhecida da mesma origem. Entrada direta, referrer ausente/bloqueado e histórico externo desconhecido seguem para `/` (ou troca de senha obrigatória), que delega a sessão às proteções existentes. É uma escolha conservadora: com Referrer-Policy restritiva, pode ir ao início mesmo havendo histórico; não abre about:blank nem tenta adivinhar rotas autorizadas.

- Next 16.3.8: `global-not-found.tsx` e `experimental.globalNotFound` atendem URLs sem rota antes do layout dependente de sessão/configuração. Retorno HTTP 404, inclusive sem backend; não executam queries privadas. `not-found.tsx` atende `notFound()` em segmentos (resposta já transmitida por streaming pode manter HTTP 200, limitação nativa).
- `error.tsx`, `(shell)/error.tsx` e `global-error.tsx` usam as boundaries nativas. Erro de renderização é descrito como erro inesperado, nunca como prova de queda do backend. O retry nativo **refaz leitura/renderização**, não reenvia mutations.
- Falha de transporte/502/503/504 ao carregar o catálogo inicial apresenta indisponibilidade; catálogo inválido, 401/403 e configuração de URL ausente não são convertidos em queda. A leitura dos catálogos tem orçamento finito de 3 segundos, alinhado ao timeout de readiness já existente no Traefik. Readiness continua DOWN, não vira UP por existir tela de fallback.
- Renovação de sessão distingue recusa 401/403 de falha transitória. Backend inacessível/5xx não apaga cookies nem credenciais em memória. Na hidratação, sem credencial restaurada, o conteúdo protegido continua bloqueado; com sessão já carregada, aviso compacto preserva a árvore aberta. Recuperação da renovação é explícita e deduplicada, sem timer infinito de tentativas.
- `navigator.onLine=false` informa **offline**, sem afirmar que backend/provedor caiu. É apenas sinal do navegador: `true` não comprova conectividade. Aviso não mostra spinner de reconexão sem tentativa real. O estado STOMP já existente continua sendo a fonte da indicação de reconexão do chat.
- Falhas de resumo por IA, mídia, dashboard e canais permanecem nos componentes locais existentes. Uma falha de query não aciona a boundary global. Não se consulta health nem provedor periodicamente para inferir queda.

## Recuperação e dados

Leituras mantêm uma tentativa adicional e o backoff padrão do TanStack Query; 401/403 não recebem retry adicional. Mutations usam `retry=false` e `networkMode=always`: uma ação offline falha, não fica pausada para execução silenciosa ao reconectar. A recuperação não invoca `resumePausedMutations`, não dá reload automático e não reproduz comandos pendentes.

Em Atendimentos, texto e arquivos ainda selecionados são recuperáveis **em memória da mesma aba**, por usuário e atendimento, após remontagem do composer. Logout limpa esse estado. Não há localStorage/base64/gravação de mensagens no servidor. Sucesso e troca de atendimento mantêm as regras existentes de limpeza. Erros locais/offline preservam o composer montado. Não é backup após fechar/recarregar o navegador; gravação em andamento e referências de citação não são reconstruídas. O Chat Interno não ganha persistência adicional nesta etapa; seu estado permanece montado durante offline.

Textos novos vivem no catálogo `erros`, com defaults locais para catálogos antigos. Tema/logo configurados são usados quando disponíveis; ausência/falha da logo não impede leitura do erro. A falha da própria raiz pode impedir recuperar a identidade da instância: usa marca neutra `CRM`, tokens locais de `globals.css` e as mesmas fontes self-hosted. Não exige CDN em runtime. Não apresenta mensagem/stack/URL do erro; diagnóstico de boundary registra apenas escopo e digest restrito. Diagnóstico de configuração registra caminho público fixo e status, sem payload ou segredo.

## Queda total do frontend/proxy — NÃO coberta por React

Se Next, Traefik, host, DNS ou conexão estiverem completamente indisponíveis, estes componentes não podem ser entregues ao navegador. O compose atual não possui serviço estático de erros. **Esta PR não entrega nem ativa cobertura de queda total.**

Uma solução suportada para Next indisponível é um serviço HTTP estático independente, na rede do proxy, associado ao middleware [Errors do Traefik](https://doc.traefik.io/traefik/reference/routing-configuration/http/middlewares/errorpages/). Ele recebe erros HTTP selecionados e busca a página em um serviço de erro separado. Não atende queda do próprio proxy/host.

Procedimento separado, com autorização operacional:

1. Confirmar versão/provider efetivos do Traefik no Dokploy e guardar configuração atual. Não assumir que labels Docker e Swarm são intercambiáveis.
2. Construir página estática com textos, tema e logo aprovados da instância, todos locais, sem depender de Next/API. Hospedar em serviço independente do Next; não publicar configuração ou credenciais. Não incluir URL original na query/log do serviço.
3. Criar middleware `errors` com `status: ["502-504"]`, `service` apontando para esse serviço estático e `query: "/indisponivel.html"`. Não configurar `statusRewrites`; testar que o status de falha original é preservado. Não interceptar 401/403/404.
4. Associar **somente ao router frontend**. Não substituir JSON da API/BFF, WebSocket ou webhooks por HTML; excluir explicitamente essas rotas, inclusive `/api/auth`, e tráfego RSC/Server Actions. Planejar router/negociação HTTP próprios em vez de aplicar middleware indistintamente. Não enviar Cookies/Authorization ao serviço de erros; definir remoção dos headers conforme a versão instalada, sem alterar autenticação no destino original.
5. Em homologação isolada: provocar Next ausente/sem servidor saudável, provar corpo estático e status 502/503/504, asset local, layout mobile, recuperação; provar que API, 401/403/404, POSTs, RSC e WS não são interceptados. Validar readiness/start-first/rollback atuais.
6. Só depois aprovar janela, plano de rollback e mudanças específicas do stack/Dokploy. Para perda do host/proxy, é necessária camada externa independente; não prometer que outro container na mesma VPS resolve.

## Operação

Nenhuma variável nova, alteração de DNS/proxy, migration ou ação obrigatória no Dokploy. Não houve deploy. O fallback estático requer implantação e testes próprios, não está habilitado nesta entrega.

## Evidências desta entrega — 13/10/2026

- `clean verify -Dmaven.compiler.release=21`: BUILD SUCCESS, execução inicial 21m19s e execução final 18m05s (17:38:39, Brasília), após concluir o catálogo. Inclui Spotless, ArchUnit e Testcontainers.
- Frontend: 15 arquivos/104 testes direcionados aprovados, incluindo sessão 401/403/5xx, offline, não reenvio ao reconectar, rascunho/arquivo após remontagem do **Composer real**, isolamento por identidade, logout, catálogo antigo, fallback e retorno. Typecheck e build aprovados; lint sem erro, quatro avisos preexistentes.
- A suíte completa local Windows foi tentada: `aplicar-marca.test.ts` apresentou 10 falhas preexistentes e a execução deixou de progredir; somente a árvore Vitest desta task foi encerrada. A aprovação da suíte completa é condicionada ao CI Linux, não à bateria direcionada. Número/resultado do último SHA no relatório da PR.
- Playwright CLI headed, sem `@playwright/test` nem reprodução HTML: build de produção do frontend com backend Spring real compatível, PostgreSQL/Redis locais e dados exclusivamente sintéticos. O backend de validação não contém credenciais de canal; falha real 503 de templates não substituiu a conversa inteira. O catálogo anterior sem `erros` também foi exercitado visualmente pelos defaults.
- HTTP 404 confirmado por navegador e cliente HTTP. Entrada direta (history.length=2, mas sem página anterior conhecida) foi ao início/Atendimentos via teclado; histórico do CRM voltou ao login. Não presumimos que history.length=2 signifique uma página útil anterior.
- Offline real do contexto Chromium: texto do composer preservado antes/depois, zero POSTs de envio durante a recuperação, sem F5. Viewports 1440×1000 e 390×844.
- Backend local parado: tela de indisponibilidade, readiness 503; após iniciar somente esse processo e clicar em Tentar novamente, login voltou/readiness 200. Erro SSR provocado por URL **local** de configuração incorreta: HTTP 500, boundary global com diagnóstico sanitizado; restaurar a URL local e clicar no retry nativo recuperou o login sem reload automático. Nenhuma configuração de produção mudou.
- Fontes `loaded`, família computada `Inter, "Inter Fallback", Inter, sans-serif`, sem overflow horizontal nas telas de erro. Comparação visual levou ao ajuste dos raios: na ausência de tema, usam `--radius` local (11px), sem criar cor/fonte nova ou modificar tokens globais.
- Contraste medido no fallback local: título/superfície **9,58:1**, descrição/superfície **5,49:1**, texto/ação primária **4,53:1** (RGB computados no Chromium). Atendem AA nesses pares; não é garantia para toda personalização arbitrária de tema.

Capturas em `frontend/output/playwright`: `404-desktop.png`, `404-celular.png`, `backend-indisponivel-desktop.png`, `backend-indisponivel-celular.png`, `erro-renderizacao-desktop.png`, `erro-renderizacao-celular.png`, `conversa-antes-offline.png`, `offline-desktop.png`, `offline-celular.png`, `conversa-recuperada-celular.png`. Somente evidências finais, sem conversas reais de clientes.

Skills clean-code/architecture-patterns citadas no AGENTS.md não estavam disponíveis; foram seguidas as regras de camadas, segurança e simplicidade do projeto. Skill Playwright e referências oficiais da versão instalada do Next foram lidas antes da implementação.
