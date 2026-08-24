# Prompt E44 — chat interno da equipe

> Leia `AGENTS.md`, `CLAUDE.md` e `frontend/AGENTS.md`.
> **Depende da E42** (canal pessoal de notificação). Não comece antes de a E42 estar entregue.
> **Pode commitar localmente a qualquer momento** — trabalho solto no working tree já se perdeu
> neste projeto. **Não execute `git push` sem autorização explícita do Marcondes.**

---

## Antes de tudo — a `main` está vermelha, e não é você

A E43 (`f9bc8d8`) foi empurrada e o CI falhou no módulo **`crm-app`**, na fase failsafe: os testes de
integração dela (Testcontainers) não passaram. Todos os outros módulos passaram.

Isso significa três coisas para você:

- **`./mvnw clean verify` no reator inteiro vai falhar, e a falha não é sua.** Verifique o seu
  trabalho com `./mvnw -pl crm-equipe -am verify`, que é o módulo desta etapa.
- **Não toque nos testes de integração da E43, nem no código dela.** Não "conserte" o build vermelho,
  não afrouxe asserção, não marque teste como `@Disabled`. Aqueles testes existem para proteger
  isolamento de carteira e comissão; um deles falhando é informação, não obstáculo.
- **Nada será publicado enquanto a `main` estiver vermelha** — o job `imagens` depende do `backend`.
  Entregue a etapa mesmo assim: o conserto da E43 vem em paralelo, por outro caminho.

Se algum teste seu falhar junto, **separe no relatório** o que quebrou por causa da E44 e o que já
estava quebrado antes de você começar.

---

## Bloco 0 — O schema já existe. Leia antes de modelar.

`V8__chat_interno.sql` **já está aplicado em produção** e cria:

```
chat_interno_conversa     (id, tipo tipo_conversa_chat, criado_em)
chat_interno_participante (conversa_id, usuario_id)
chat_interno_mensagem     (id, conversa_id, remetente_id, tipo, conteudo,
                           midia_url, midia_metadados, enviado_em)
```

`tipo_conversa_chat` é `('DIRETA','GRUPO')`, criado na `V1`. **Nenhuma linha de Java toca nessas
tabelas hoje** — o schema foi criado e o módulo nunca foi escrito.

Regras que decorrem disso:

- **Não recrie essas tabelas e não edite a `V8`.** O que faltar entra em migração **nova**.
- O comentário da `V8` diz que isso pertence ao módulo **`crm-equipe`**. Respeite a fronteira; o
  ArchUnit vai cobrar.
- Duas coisas faltam no schema e você vai precisar delas: **leitura por participante** (para "não
  lidas") e **índice de `(conversa_id, enviado_em)`** para paginar a conversa. Ambas em migração
  nova, com justificativa no comentário do arquivo, no padrão das outras migrações.

## Bloco 1 — Escopo da fase 1

O schema suporta grupo e mídia. **A fase 1 entrega menos de propósito:**

- **conversa DIRETA apenas**, entre dois membros da equipe;
- **texto apenas** — sem áudio, imagem ou documento.

`GRUPO` e mídia ficam para depois. Não construa o que não vai ser usado na primeira semana, mas
**não modele de um jeito que impeça** grupo depois: a tabela já prevê N participantes, então nada no
seu código pode assumir "exatamente dois" fora da criação da conversa.

## Bloco 2 — Privacidade

Isto não é a caixa de atendimento; é conversa entre pessoas da empresa.

- **Ser `GESTOR` não dá direito de ler a conversa direta de dois atendentes.** O recorte é
  participação, não papel. Escreva o teste que prova isso.
- A autorização vive no caso de uso, como todo o resto do projeto.
- Se a RLS do banco precisar de política para essas tabelas, faça — e rode o smoke de RLS. Não
  confie só na checagem de aplicação.

## Bloco 3 — Tempo real

Use **a fila pessoal da E42**, com um tipo de envelope próprio. Não crie um terceiro transporte, não
crie um `/topic/` de broadcast, e não reaproveite o canal do atendimento — chat interno não tem
atendimento.

- Mensagem nova chega para os participantes que estão online.
- O contador de não lidas atualiza sem F5.
- Redis pub/sub é at-most-once: a lista de conversas recarregada na reconexão é o que garante
  convergência. O tempo real é conveniência.

## Bloco 4 — Tela

Superfície própria, item na barra lateral — **não** um painel dentro do atendimento. A conversa com
o cliente e a conversa com o colega não podem se confundir na tela; é assim que alguém manda para o
cliente o que era para o colega.

- Lista de conversas com nome, prévia da última mensagem, horário e não lidas.
- Reaproveite o que já existe: `AvatarIniciais` e `tomDoAvatar` para o tom por pessoa,
  `iniciaisDoNome`, os padrões visuais do composer e das bolhas de `components/atendimentos`.
  **Copiar o componente de atendimento para dentro do chat é proibido** — se for o mesmo comportamento,
  extraia; se for diferente, escreva o seu.
- Todo texto no catálogo (`textos.json` + `schema.ts`). Nenhum literal no JSX.
- Item de menu atrás de feature flag, no padrão dos outros (`sidebar.tsx`), para poder subir
  desligado.

## Bloco 5 — O que NÃO entra

- Grupo, mídia, edição e exclusão de mensagem, reações, menções, busca.
- Notificação por e-mail ou push.
- Qualquer vínculo entre a conversa interna e um atendimento — se isso for desejado depois, é
  decisão de produto, não atalho de implementação.

---

## Verificação

- Teste de que um não-participante — inclusive `GESTOR` e `ADMINISTRADOR` — recebe negativa ao ler
  ou escrever numa conversa da qual não participa.
- Teste de não lidas: zera para quem leu, não zera para o outro.
- Teste de paginação da conversa usando o índice novo.
- Teste de que a mensagem chega em tempo real para o participante online.
- Migração nova aplicada do zero **e** sobre uma base que já tem a `V8`.
- Smoke de RLS.
- Backend: `./mvnw -pl crm-equipe -am verify` **com testes**.
- Frontend: `npm test -- --run`, `npm run lint`, `npm run build`.
- Verificação visual com dois usuários. Se não conseguir, **diga**, não descreva como deveria estar.
