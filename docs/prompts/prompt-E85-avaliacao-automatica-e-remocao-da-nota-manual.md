# E85 — Avaliação automática pós-atendimento e retirada da nota manual

## Objetivo

Substituir o lançamento manual de nota feito pelo atendente no encerramento por uma avaliação automática, disparada pelo gatilho durável da E83. O workflow externo (n8n/WhatsApp/IA) envia a pesquisa e devolve o resultado ao CRM; o atendente não escolhe nota, não vê formulário de avaliação ao finalizar e não pode bloquear o encerramento por esse motivo.

Esta tarefa não autoriza chamar n8n, WhatsApp ou IA reais sem autorização operacional. Implemente contratos, persistência, tela de consulta e testes locais.

## Branch e base

1. Atualize as referências remotas sem alterar `main`.
2. Crie worktree dedicado a partir da `origin/main` atual e branch `codex/e85-avaliacao-automatica`.
3. Confirme que a E83 está na base. Se não estiver, pare e informe; não faça cherry-pick, merge ou cópia manual por conta própria.
4. Leia `AGENTS.md`, documentação de arquitetura e o runbook da E83 antes de editar. Use as skills relevantes disponíveis.

## Escopo obrigatório

### 1. Retirada da nota manual

- Localize a interface, contrato e regra usados para a nota manual na finalização.
- Remova somente esse controle e a validação que o obrigava. Não remova notas internas, resumo de IA, comentários ou histórico não relacionados.
- A finalização individual continua rápida mesmo se o workflow externo estiver desligado, lento ou falhar.
- “Finalizar todos” continua sem iniciar avaliação; atendimento sem responsável humano continua finalizando sem avaliação.

### 2. Estado persistente da avaliação

Modele uma avaliação genérica, ligada a um atendimento, sem campos específicos da Estrutural. Crie migration nova; não altere migrations aplicadas.

Persistir no mínimo:

- identificador e `atendimento_id` único;
- responsável, lead e referência externa/idempotência do workflow — sem token/segredo;
- estados explícitos `PENDENTE_ENVIO`, `ENVIADA`, `RESPONDIDA`, `EXPIRADA`, `FALHOU` (ou equivalentes);
- nota recebida em escala configurável, comentário opcional e datas de criação/envio/resposta/atualização.

O registro nasce na mesma transação que a intenção/outbox E83 ou é derivado dela idempotentemente. Não pode haver duas avaliações para o mesmo atendimento, nem sob concorrência.

### 3. Contratos da automação

- Preserve o disparo assíncrono e resiliente da E83; jamais faça HTTP síncrono na finalização ou no recebimento de mensagens.
- Defina endpoint interno autenticado e versionado em `/internal/v1/...` para a automação registrar resultado: referência/idempotência, estado final, nota quando houver, comentário opcional e instante de resposta.
- Use o padrão real de autenticação do contrato interno, RFC 7807 e idempotência explícita.
- Callback repetido não duplica histórico nem sobrescreve resposta consolidada indevidamente. Valide escala, transições e vínculo avaliação/atendimento/lead no backend.
- Não registre comentário em logs. Documente payloads de ida e retorno com UUIDs/telefones fictícios; nunca token, URL de produção ou credenciais.

### 4. Experiência de tela

- Depois da finalização não pode haver modal, campo, select, estrela ou botão de nota manual.
- Na ficha/histórico do lead, mostre resumo somente quando houver avaliação e o papel já puder visualizar aquele atendimento: estado, nota se respondida, comentário se houver e datas relevantes.
- Para pendente, falha ou expirada, use textos do catálogo e estado neutro; não exponha erro técnico do n8n.
- Sem avaliação, não mostrar card fantasma. Todos os textos novos em `textos.json` e schema Zod; cores por tokens semânticos.
- Preserve responsividade, painéis retraíveis, chat e composer fora da remoção direta do controle manual.

### 5. Segurança, visibilidade e auditoria

- A leitura segue a visibilidade existente de atendimento/lead; atendente A não consulta avaliação de B.
- Apenas a automação autenticada registra resultado no endpoint interno.
- Não amplie permissões de gestão/administração sem decisão explícita.
- Audite apenas eventos seguros: solicitada, enviada, respondida, expirada ou falhou. Nunca inclua token, telefone completo ou comentário sensível.

## Decisões já tomadas

- Só finalização individual elegível inicia avaliação.
- Lote não inicia avaliação.
- Sem responsável humano, finaliza sem avaliação.
- O responsável é preservado mesmo se gestor finalizar.
- O CRM dispara intenção; o workflow externo decide como enviar a pesquisa e pode usar IA. O CRM não embute provedor, prompt ou regra comercial de IA.
- O atendente não lança mais nota manualmente.

## Fora de escopo

- Chamadas reais a n8n/WhatsApp/IA.
- Campanha, relatório agregado de NPS/CSAT e dashboard analítico.
- Alterar worker/outbox E83 para ser síncrono, remover migration/testes E83, introduzir tenant_id, mock, hardcode de segredo, string de UI fora do catálogo ou cor fixa.

## Testes obrigatórios

### Backend com Testcontainers/Postgres real

- Finalização individual elegível cria uma única avaliação/intenção sob concorrência.
- Lote e atendimento sem responsável não criam avaliação.
- Timeout/falha externa não impede finalização nem segura transação.
- Callback autorizado registra resposta; ausência de token é 401, token inválido é 403 e usuário normal não alcança contrato interno.
- Callback repetido é idempotente; estado inválido e nota fora da escala são rejeitados.
- Atendente A não consulta avaliação de B; demais papéis respeitam regra existente.
- Fluxo preserva responsável, atendimento e lead; teste o ponto de entrada real do worker/listener.

### Frontend e visual

- O fluxo de finalizar não renderiza/exige mais nota manual.
- Teste estado vazio, pendente, respondido e falha/expirado da ficha com contratos reais.
- Teste que UI não mostra avaliação inexistente e usa catálogo de textos.
- Em navegador autenticado com dados de demonstração, finalize atendimento e confirme ausência da nota manual; simule callback local controlado e confira o resumo respondido. Capture desktop e mobile sem dados reais de cliente.

### Comandos

- `cd backend && ./mvnw clean verify` com Java 21 e Testcontainers.
- `cd frontend && npm ci`, `npm run lint`, `npm run typecheck`, `npm test -- --run`, `npm run build`.
- `git diff --check`.

## Configuração, commit e CI

- Atualize `.env.example`, README/tabela de variáveis e runbook E83 para novas configurações opcionais. Em `dokploy-stack.yml`, use `${VAR:-}`; nunca `${VAR:?}` para avaliação.
- No relatório, inclua **ação necessária no Dokploy antes do próximo deploy**, com nomes e exemplos sem segredos.
- Faça commits Conventional Commits, envie para `origin/codex/e85-avaliacao-automatica` e abra PR contra `main` depois das verificações locais.
- Aguarde CI e informe URL/número da run e resultado por job. Não faça merge, deploy ou chamada real ao workflow sem autorização posterior.

## Relatório final

Siga as sete seções de `AGENTS.md`, incluindo SHA, branch, confirmação de push, arquivos/migration/contratos novos, evidências de testes negativos, CI, variáveis novas e dependências ainda externas (workflow e validação com Dylan).
