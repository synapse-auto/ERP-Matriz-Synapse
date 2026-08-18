# Verificação e publicação da E31

A E31 (`prompt-E31-ajustes-finos.md`) foi implementada por **outro agente**. Sua tarefa não é
reimplementar: é **verificar o que existe, publicar se ainda não estiver publicado, e relatar**.

Leia `AGENTS.md` e o `prompt-E31-ajustes-finos.md` antes de começar.

> **Não reescreva a implementação de outro agente.** Correção mecânica — formatação, import não
> usado, erro trivial de lint — pode. Qualquer mudança de **comportamento**: pare, não altere, e
> relate. Uma reimplementação silenciosa destrói o trabalho e a rastreabilidade de quem fez.

---

## 1. Estado do repositório

```
git log --oneline -12
git status --short
git rev-parse HEAD; git rev-parse origin/main
```

Se houver alterações da E31 sem commit, commite **por bloco** (Conventional Commits, mensagem em
português) e faça push. Se `HEAD` estiver à frente de `origin/main`, faça push.

Alterações em `docs/` que não sejam desta etapa: deixe fora do índice, não commite junto.

## 2. Verificações de conteúdo

Cada item abaixo tem um veredito: **presente e correto**, **presente mas errado**, ou **ausente**.
Não aceite "o plano dizia que faria" como evidência — confira o arquivo.

### Bloco 4 é o de maior risco, comece por ele

O primeiro plano do agente propunha `--border: transparent`, o que apagaria as **divisórias
estruturais** do layout, não só o contorno dos cards. Foi corrigido no papel; confirme no código.

- `--border` **não** pode estar `transparent` nem equivalente. Confira `globals.css` e as chaves
  de borda do `tema.json` (`borda`, `bordaForte`, `bordaSuave`).
- As divisórias entre as colunas continuam existindo: `composer.tsx`,
  `painel-da-conversa.tsx`, `lista-conversas.tsx`, `painel-lateral-lead.tsx`.
- `button.tsx` variante `outline` continua com contorno visível.
- Campos de formulário mantêm borda e o anel de foco continua contrastando.
- Vale em claro **e** escuro.

Se houver capturas antes/depois de Atendimentos, Dashboard e Equipe, avalie. Se não houver —
o prompt as exigia — gere você mesmo com Playwright em 1280×800 e anexe ao relatório; se não
conseguir, registre a ausência em vez de ignorá-la.

### Blocos 1 e 2 — os testes precisam existir e testar o que dizem

- `pagina-equipe.test.tsx`: a mutação de desativar **não** é chamada ao abrir o diálogo nem ao
  cancelar, e é chamada **uma vez** ao confirmar. Um teste que só verifica se o diálogo aparece
  não serve — leia o corpo do teste.
- `password-input.test.tsx`: o `type` alterna entre `password` e `text`, o rótulo acessível
  acompanha, e o valor digitado é preservado na alternância.
- O `PasswordInput` está em uso nos três lugares: login, troca de senha (as três caixas) e
  criação de usuário na Equipe.
- O texto do diálogo diz que o usuário **perde o acesso mas nada é apagado**, e que pode ser
  reativado. Só "Deseja realmente desativar {nome}?" não cumpre o pedido.

### Bloco 3 — logo

- `logoUrl` alimenta o favicon (`generateMetadata`) e a marca da sidebar.
- Com `logoUrl` nulo, o comportamento antigo continua intacto — nenhum erro no console.
- **Nenhum arquivo de marca de cliente dentro de `frontend/`.**
- Sobras do scaffold removidas de `public/`: `next.svg`, `vercel.svg`, `file.svg`, `globe.svg`,
  `window.svg`.
- Se `<img>` foi usado, o `eslint-disable` do `@next/next/no-img-element` está presente e
  justificado em comentário.

### Premissa inventada — busque e elimine

O agente afirmou, em uma versão do plano, que o tema vinha de uma tabela `configuracao_tema`.
**Essa tabela não existe.** O tema é `tema.json`, lido do classpath por
`ConfiguracaoDeInstanciaResources` e servido em `GET /api/v1/config/tema`.

```
grep -ri "configuracao_tema" . --exclude-dir=.git --exclude-dir=node_modules
```

Qualquer ocorrência em código, comentário ou documentação é desinformação: remova ou corrija.

## 3. Executar os testes

Frontend, que é onde esta etapa vive:

```
cd frontend && npm run lint && npm run test && npm run build
```

Backend, porque `textos.json` mora em `backend/crm-app/src/main/resources/`:

```
cd backend && ./mvnw clean verify
```

Falha aqui **não** é autorização para reescrever a implementação. Conserto mecânico, sim;
mudança de comportamento, pare e relate.

## 4. Publicar e acompanhar o CI

Com tudo verde e o push feito, acompanhe a run até o fim:

```
gh run list -L 3
gh run view <id>
```

**Um job vermelho não significa código quebrado.** Este repositório já teve runs falhando por
429/503 do GitHub ao baixar `docker/metadata-action`, e por contagem congelada no `OpenApiIT`
quando endpoints novos entram. Abra o log, identifique a causa, e:

- causa externa (rede, rate limit do GitHub): re-execute o job;
- causa no código: relate, não conserte por conta própria se implicar mudar comportamento.

Só considere a etapa publicada com a run **inteira** verde e o número dela em mãos.

## 5. Relatório

Siga o formato do `AGENTS.md`. Além dele, responda diretamente:

- Quais dos itens da seção 2 estão **presentes e corretos**, **presentes mas errados** e
  **ausentes** — um veredito por item, com o arquivo como evidência.
- O que você corrigiu (só o mecânico) e o que **deixou** por exigir mudança de comportamento.
- SHA final, estado do push, e o número da run do CI.
- Se as capturas antes/depois das bordas existem, e o que elas mostram.

Se nada precisou ser publicado porque já estava tudo no `origin/main` e verde, diga isso — é um
resultado legítimo, e melhor que inventar trabalho.
