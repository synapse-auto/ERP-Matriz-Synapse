# Prompt E53 — a transferência entrega o lead para quem não pode recebê-lo

> Leia `AGENTS.md`, `CLAUDE.md`, `docs/22-bugs-abertos-26-08.md` (bug 3) e `docs/13-estado-do-projeto.md`.
> **Pode commitar localmente a qualquer momento.** Não execute `git push` sem autorização explícita.

**Esta etapa vai sozinha para produção.** Não junte nada dela com outra correção: é a única que causa
perda silenciosa de lead e precisa poder subir e ser revertida isolada.

---

## O defeito

`TransferirAtendimentoUseCase.executar` grava `atendente_id = paraAtendenteId` e chama
`leads.transferirPara(...)` **sem verificar nada sobre o destino**: não checa se o usuário existe, se
está ativo, nem se o papel é `ATENDENTE`.

O caminho da Automação valida tudo isso — `TransferirAtendimentoDaAutomacaoUseCase` usa
`AtendenteParaTransferenciaRepositorio` e lança `AtendenteDestinoInvalidoException`. O caminho humano
não injeta nenhum dos dois. A assimetria nasceu porque os dois foram escritos em etapas diferentes.

O estrago vem da RN-CRM-01: a visibilidade do atendente é "meus leads + os sem dono". Um lead
transferido para gestor, subgestor, administrador ou usuário desativado deixa de ser sem dono e não
pertence a nenhum atendente — **some da lista de todo mundo**, sem erro, sem aviso, e a comissão passa
a contar para quem não vende.

## Bloco 1 — Validar o destino, com a mesma regra dos dois lados

No `executar` (caminho humano), antes de qualquer escrita:

- `paraAtendenteId == null` continua significando "devolver para a IA" e permanece válido.
- Destino não nulo precisa **existir**, estar **ativo** e ter papel **`ATENDENTE`**. Qualquer outra
  coisa é recusada.
- **Reuse o que já existe.** `AtendenteParaTransferenciaRepositorio` e
  `AtendenteDestinoInvalidoException` já resolvem exatamente isso no caminho da Automação. Injete os
  mesmos; não escreva uma segunda validação com regra própria, que é como a divergência começou.
- A recusa vira **422** com RFC 7807, nomeando o destino recusado e dizendo o motivo (inexistente,
  inativo, papel não elegível). O controller que expõe a transferência humana precisa do
  `@ExceptionHandler` correspondente.

Confirme no relatório se `Atendimento.transferirPara` faz alguma validação hoje — se fizer, diga
qual, para não duplicarmos regra em duas camadas.

## Bloco 2 — O diálogo para de oferecer o impossível

`frontend/src/components/atendimentos/dialogo-transferir.tsx` monta o botão "Assumir para mim" a
partir de `usuarios?.find((u) => u.email === email)`, sem olhar papel. Um gestor ou administrador
logado vê e clica.

- "Assumir para mim" só aparece quando **quem está logado tem papel `ATENDENTE`**.
- A lista de colegas já filtra `papel === "ATENDENTE" && ativo` — mantenha.
- Se mesmo assim o backend recusar, mostre o **detalhe do problema** que veio no 422, não a mensagem
  genérica de erro. Quem transfere precisa saber por que não deu.

## Bloco 3 — Quantos leads já estão órfãos (relatar, não consertar)

Antes de qualquer coisa, descubra o estrago existente e **relate**:

```sql
SELECT l.id, l.nome, l.telefone, u.nome AS dono, u.papel, u.ativo
  FROM lead l
  JOIN usuario u ON u.id = l.atendente_id
 WHERE l.atendente_id IS NOT NULL
   AND (u.papel <> 'ATENDENTE' OR u.ativo = FALSE);
```

E o equivalente em `atendimento.atendente_id`.

**Não escreva migration de correção e não reatribua ninguém.** Reconciliar dono de lead envolve
conversa e comissão; é decisão do Marcondes, exatamente como as migrations V24 e V26 já decidiram
para telefone duplicado — elas **interrompem** e exigem correção manual em vez de mesclar sozinhas.
Siga a mesma política: relate a lista, não toque nos dados.

## Bloco 4 — O que não pode acontecer

- Nenhuma mudança no caminho `/internal/v1` da Automação. Ele já está certo; esta etapa só alinha o
  humano com ele.
- Nenhuma alteração na RN-CRM-01 nem na visibilidade. O bug não é a regra de visibilidade, é a
  ausência de validação antes de gravar.
- Nenhuma operação nova no OpenAPI. Confirme que a contagem do `OpenApiIT` **não** mudou.

---

## Verificação

- `./mvnw clean verify` no reator inteiro, verde.
- Teste de que transferir para um **gestor** responde 422 e **não** altera `atendimento.atendente_id`
  nem `lead.atendente_id` — as duas escritas precisam não ter acontecido.
- Teste equivalente para subgestor, administrador, usuário **inativo** e UUID inexistente.
- Teste de que transferir para um atendente ativo continua funcionando e move lead e atendimento
  juntos.
- Teste de que `paraAtendenteId = null` continua devolvendo para a IA.
- Frontend: teste de que "Assumir para mim" não é renderizado para papel diferente de `ATENDENTE`.

## Relatório

1. A lista de leads e atendimentos já órfãos (Bloco 3), com contagem.
2. Se `Atendimento.transferirPara` valida algo hoje.
3. Se a contagem do `OpenApiIT` mudou.
