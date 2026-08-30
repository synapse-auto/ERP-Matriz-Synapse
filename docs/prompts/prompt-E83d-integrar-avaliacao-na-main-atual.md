# Prompt E83d — Integrar E83/E83b sobre a main atual

> Leia `AGENTS.md` por inteiro antes de agir. Leia também `docs/03-modelo-dados-postgres.md`, `docs/04-adrs-e-api.md`, `docs/35-runbook-webhook-avaliacao.md`, `docs/relatorio-E83-webhook-avaliacao.md` e `docs/relatorio-E83b-deadlock-finalizacao-mensagens.md`.
>
> Esta etapa não cria um novo comportamento de avaliação. Ela transporta, sem perder cobertura, a E83 e a correção E83b para uma base atual de `main`, que avançou depois de `aed6f16`.

---

## Estado confirmado antes da etapa

Fonte da E83:

```text
worktree: C:\Users\marcondes\Desktop\projeto_matriz-e83
branch: codex/e83-webhook-avaliacao
checkpoint E83: 0ba32863ec574fed70286fa56e1e6457dbd38c36
base original: aed6f16d711ec39cc3cdfc62a93dc1653a435157
```

No worktree fonte, a E83b ainda está **não commitada** e deve conter somente:

```text
M backend/crm-app/src/test/java/com/synapse/crm/app/atendimento/WebhookAvaliacaoIT.java
M backend/crm-atendimento/src/main/java/com/synapse/crm/atendimento/infrastructure/persistencia/AtendimentoRepositorioJdbc.java
M docs/35-runbook-webhook-avaliacao.md
M docs/relatorio-E83-webhook-avaliacao.md
A docs/relatorio-E83b-deadlock-finalizacao-mensagens.md
```

`origin/main` confirmado na revisão era `d5ba368`, sete commits à frente da base E83. **Não use esse SHA como verdade no momento de executar**: faça `git fetch origin` e registre o SHA de `origin/main` que existir então.

---

## Bloco 0 — Conferir e congelar a correção no worktree de origem

1. No worktree `projeto_matriz-e83`, confira branch, `HEAD`, merge/rebase inexistente e o diff exato acima.
2. Antes de qualquer Git mutável, rode pelo menos a classe dirigida que contém a regressão:

   ```powershell
   cd backend
   .\mvnw.cmd -pl crm-app -am verify -Dit.test=WebhookAvaliacaoIT
   ```

   Se a forma de seleção não for compatível com Failsafe nesta base, use o comando Maven equivalente que execute de fato `WebhookAvaliacaoIT`; não transforme IT em teste unitário nem a pule.

3. Faça `git diff --check`. Se houver arquivo além dos cinco esperados, conflito, mudança não relacionada ou teste vermelho, **pare** e reporte. Não use `reset --hard`, `checkout --`, `clean`, stash, cópia manual ou edição de outro worktree para “limpar”.
4. Commite **somente** esses cinco arquivos no worktree fonte:

   ```text
   test: cobrir concorrencia entre finalizacao e recebimento
   ```

   Esse commit é o registro imutável da E83b. Não faça push da branch antiga apenas para usá-la como ponte.

> **Por que primeiro aqui:** a E83b não é um patch de documentação; ela contém a regressão que prova que `FOR NO KEY UPDATE` evita o ciclo com `KEY SHARE`. Aplicar arquivos soltos depois de mudar de base perde rastreabilidade e pode descartar o teste que motivou a correção.

---

## Bloco 1 — Criar a integração limpa a partir da main atual

Depois do commit E83b, crie uma branch e worktree novos, sem reutilizar `projeto_matriz-e83`, `main` ou qualquer worktree de frontend:

```powershell
git fetch origin
git worktree add -b codex/e83-avaliacao-main <DIRETORIO_NOVO> origin/main
```

No worktree novo:

1. Confirme que `HEAD` começou exatamente em `origin/main` recém-buscada e que a árvore está limpa.
2. Faça cherry-pick, nesta ordem, dos dois commits da fonte:

   ```powershell
   git cherry-pick 0ba32863ec574fed70286fa56e1e6457dbd38c36
   git cherry-pick <SHA_DO_COMMIT_E83B_CRIADO_NO_BLOCO_0>
   ```

3. Não faça merge da `main` dentro da branch antiga, não rebase a fonte e não faça `git merge` direto em `main`. A nova branch é a única superfície de integração.

4. Caso haja conflito:

   - resolva somente depois de comparar os dois lados e identificar a intenção;
   - preserve integralmente a E83: outbox na transação da finalização, configuração opcional, `FOR NO KEY UPDATE`, guarda `WHERE status <> 'FINALIZADO'`, executor/circuit breaker separados, contrato e matriz E83b;
   - preserve integralmente o comportamento que chegou pela `main` mais recente, especialmente alterações de template/novo contato que não pertencem ao webhook de avaliação;
   - acrescente teste de regressão se o conflito tocar finalização, transferência, recebimento, outbox, configuração, RLS ou contrato HTTP;
   - nunca resolva excluindo uma seção inteira, escolhendo “ours/theirs” sem leitura, ou removendo o teste concorrente para fazer o build passar.

> **Ponto de parada.** Se a `main` atual já tiver uma implementação de webhook de avaliação, migration `V44` com identidade diferente, endpoint que colida, ou uma política de finalização incompatível, pare antes de escolher qual contrato vence. Informe os SHAs, arquivos e o conflito semântico. Não renumere uma migration aplicada nem duplique tabela/outbox.

---

## Bloco 2 — Provar a integração, não apenas os commits antigos

A validação anterior em `aed6f16` não certifica a nova base. No worktree integrado, rode:

```powershell
cd backend
.\mvnw.cmd spotless:apply clean verify
cd ..\frontend
npm ci
npm run lint
npm run typecheck
npm test -- --run
npm run build
cd ..
git diff --check
```

Além do reator completo, confira explicitamente nos relatórios de Failsafe:

- `WebhookAvaliacaoIT` — a matriz completa, incluindo `recebimentoPausadoAntesDoContador_eFinalizacaoNaoEntramEmDeadlock`;
- `AvaliacaoAtendimentoIT` — contrato da coleta/CSAT;
- `RlsIsolamentoIT` — isolamento real;
- `OpenApiIT` — contrato público sem redução de operações.

Revise o diff acumulado contra `origin/main`:

- Deve conter a E83 e E83b, e não arquivos temporários, `target/`, logs, credenciais, `.env` real, outputs do Playwright ou prompts não relacionados.
- `.env.example`, `README.md`, `application.yml` e `docker/dokploy-stack.yml` devem manter as variáveis `AUTOMACAO_AVALIACAO_*` opcionais via default vazio (`${VAR:-}`), nunca `:?obrigatoria`.
- Nenhum segredo efetivo, URL privada indevida ou valor de cabeçalho real pode entrar no Git.
- A migration nova deve continuar única, aditiva e compatível com banco que já tenha migrations anteriores aplicadas.

Se `spotless:apply` mudar arquivos, revise e inclua essa formatação no commit correspondente antes de declarar validação concluída.

---

## Bloco 3 — Publicação controlada

Após a integração verde, a nova branch já deve conter dois commits cherry-picked rastreáveis. Só crie um commit adicional se a resolução de conflito ou a formatação tiver gerado mudança real; use Conventional Commits e descreva precisamente a integração.

Não faça merge em `main`, deploy, tag, ativação de variáveis ou chamada real ao n8n/WhatsApp nesta etapa.

Faça push de `codex/e83-avaliacao-main` somente após autorização explícita. Com push autorizado:

1. confirme que o SHA chegou a `origin/codex/e83-avaliacao-main`;
2. acompanhe e informe o número e resultado da run de CI;
3. aguarde revisão/autorizaçao separada antes de abrir/aceitar merge para `main`.

---

## Definição de pronto

- [ ] A E83b foi registrada em commit próprio no worktree fonte, sem arquivos estranhos.
- [ ] Uma nova branch/worktree partiu da `origin/main` realmente atualizada.
- [ ] `0ba3286` e o commit E83b foram transportados na ordem correta, sem merge/rebase da árvore fonte suja.
- [ ] O comportamento E83 (outbox, configuração opcional, autorização, retry/lease/circuito) foi preservado.
- [ ] `FOR NO KEY UPDATE` e a guarda contra reabertura do atendimento continuam presentes e cobertos pela regressão real.
- [ ] Reator Maven completo, frontend completo e `git diff --check` passam na base integrada.
- [ ] Não há segredo, migration reescrita, variável obrigatória nova no Dokploy, deploy, tag ou chamada externa real.
- [ ] Push/CI só são declarados se autorizados e comprovados por SHA/run; caso contrário constam como não verificados.

## No relatório final

Siga os sete itens de `AGENTS.md` e informe também:

1. SHA de `origin/main` usado como base, SHA do commit E83b e SHAs finais da branch de integração.
2. Se houve conflito, cada arquivo, a decisão tomada e a regressão que a protege.
3. Resultado individual de `WebhookAvaliacaoIT`, `AvaliacaoAtendimentoIT`, `RlsIsolamentoIT` e `OpenApiIT`, além do total do reator.
4. Variáveis Dokploy: expectativa **nenhuma adicional**; liste apenas as 14 E83 que já existiam e confirme que permanecem opcionais.
5. Push e CI remoto: confirmação de `origin` e número da run, ou “não autorizado/não verificado”.

---

## Fora desta etapa

- Alterar payload, URL, header, segredo, regras de elegibilidade, dono comercial, CSAT, lease, backoff, circuit breaker ou workflow n8n/WhatsApp da E83.
- Ativar a integração, configurar Dokploy, chamar endpoint real, fazer replay/retroatividade ou rotacionar credencial.
- Alterações de frontend e de templates que não forem estritamente necessárias para resolver conflito de integração.
- Merge para `main`, deploy ou tag.
