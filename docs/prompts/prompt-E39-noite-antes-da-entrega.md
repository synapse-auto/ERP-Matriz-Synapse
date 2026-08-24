# Prompt E39 — a noite antes da entrega

> Leia `AGENTS.md`. 
> Commite e faça push **por bloco**.
> Ao encerrar: `cd backend && ./mvnw clean verify` e o **número da run** do CI.

---

## A regra que vale mais que qualquer bloco

**Execute na ordem. Feche cada bloco com teste antes de abrir o próximo.**

Se um bloco não fechar — trava, exige decisão, quebra outro teste — **pare naquele bloco,
commite o que já está fechado, e escreva no relatório onde parou e por quê.**

Cinco blocos inteiros e testados valem mais do que nove pela metade. Ninguém vai revisar isto
antes do cliente ver.

**Nenhum bloco pode deixar a aplicação em estado pior do que encontrou.** O ambiente subiu de um
500 no login há poucas horas.

---

## Bloco 0 — A moldura em volta do conteúdo

`frontend/src/app/(shell)/layout.tsx`:

```tsx
<div className="min-w-0 flex-1 p-5">
  <main className="flex h-full flex-col overflow-y-auto rounded-lg bg-card shadow-sm">
```

O `p-5` cria 20px de respiro e o `main` vira um cartão flutuante sobre o canvas. O resultado é uma
moldura em volta de tudo, que não existe no protótipo.

- Remova o respiro e o arredondamento: o conteúdo encosta nas bordas, ao lado da sidebar.
- **A `Sidebar` também não pode flutuar** — confira se ela encosta no topo, na esquerda e embaixo.

> **Ponto de parada.** A E28 consertou o scroll interno: `min-h-0 flex-1 overflow-hidden` no shell e
> `overflow-y-auto` no `main`. Se tirar o `p-5` fizer a página inteira rolar como landing page de
> novo, **reverta e pare.** Aquele defeito custou uma etapa; não vale trocar moldura por scroll
> quebrado.

Teste: a lista de Atendimentos rola dentro dela mesma e a janela não ganha barra de rolagem.

## Bloco 1 — `#reset` devolve o atendimento para a IA

Quando uma mensagem chega num atendimento **já transferido para humano**, o backend verifica se o
conteúdo é exatamente `#reset`.

- Comparação sobre o texto **trimado**, sem diferenciar maiúscula de minúscula.
- Sendo `#reset`: o atendimento volta para a IA, com o mesmo caminho que o `PATCH
  /internal/v1/atendimentos/{id}/modo-ia` já usa (E33). **Não duplique a regra** — reutilize o caso
  de uso.
- A mensagem `#reset` **não** vai para a conversa como mensagem do cliente. É comando, não conversa.
- O evento vai para a Automação pelo repasse que já existe. Não crie caminho novo.
- Timeline e auditoria registram a devolução com ator Sistema, não com UUID falso.
- Atendimento que **já está** com a IA: `#reset` não faz nada e não erra.

Testes: `#reset` devolve para IA; `#RESET` e ` #reset ` também; `#resetar` **não**; a mensagem não
aparece na conversa; atendimento já com IA fica inalterado.

## Bloco 2 — Agenda compartilhada e responsável pelo último atendimento

**Mudança de regra de negócio pedida pelo cliente.** Hoje o atendente só enxerga a própria carteira;
o cliente mudou de ideia e quer uma agenda só, visível para todos.

Duas partes:

**a) Todos os atendentes veem todos os leads.** A `V12` recorta `lead`, `atendimento`, `lembrete` e
`mensagem_programada` por `atendente_responsavel_id`. A visibilidade passa a ser da equipe inteira.

**b) O responsável passa a ser o último atendente que atendeu o lead.** Deixa de ser carteira fixa e
passa a ser consequência do último atendimento humano. `atendente_responsavel_id` continua
existindo e continua significando "responsável" — só muda quem o escreve e quando.

> **Não apague a RLS.** Mantenha as políticas e o `synapse_app`: a infraestrutura de isolamento é o
> que protege o filho seguinte, que pode ter carteira. O que muda é a **política de visibilidade**,
> não a existência dela.

> **O smoke RLS vai falhar, e isso é esperado.** `docker/verificacao/smoke-rls.sql` afirma que cada
> atendente vê **um** lead. Com agenda compartilhada, os dois veem **dois**. Atualize o smoke com a
> expectativa nova e **diga no relatório que o fez e por quê**. Ajustar teste para acompanhar
> decisão de negócio é legítimo; ajustar teste para parar de reclamar não é — deixe claro qual dos
> dois você fez.

> **Ponto de parada.** Se mudar a visibilidade exigir tocar em algo que você não consegue testar
> nesta noite — Dashboard por atendente, ranking da Equipe, comissão — **pare, commite o que fechou
> e relate.** Atendente ver lead que não devia é incidente comercial; o contrário também.

Testes: dois atendentes e dois leads, cada um vê **os dois**; o responsável muda quando outro
atendente assume; transferência atualiza o responsável; o gestor continua vendo tudo; smoke RLS
atualizado passando.

## Bloco 3 — Finalizar conversas ativas no fim do expediente

Ao fim do expediente, os atendimentos ativos são finalizados automaticamente.

- O horário vem de configuração, **não de constante no código**. Se `configuracao_automacao` servir,
  use; senão, justifique.
- Finalização pelo mesmo caso de uso que o botão "Finalizar" usa. Não duplique a regra.
- Ator Sistema na timeline.
- **Idempotente:** rodar duas vezes no mesmo dia não refinaliza nem duplica evento.

> **Cuidado com `@Scheduled`.** O caso 3 da lista do `docs/13`: `@Scheduled` com auto-invocação
> quebrou mensagens nos dois sentidos com o build verde. Agendado chama serviço injetado, nunca
> método da própria classe.

> Isto **não** viola a RN-CRM-07: finalizar atendimento é do CRM. O que é da Automação é regra de
> mensagem — follow-up, fidelização.

Testes: atendimento ativo é finalizado no horário; já finalizado não muda; execução repetida é
inócua; o horário sai da configuração e não do código.

## Bloco 4 — Trocar o próprio e-mail

Nas configurações do usuário, trocar o e-mail da conta.

- Só o próprio e-mail. **Nenhum caminho para trocar o de outro** neste bloco.
- E-mail é `UNIQUE` em `usuario`: duplicado devolve `422` com mensagem clara, não erro de constraint.
- Formato inválido → `422`.
- O e-mail é o login. Diga no relatório o que acontece com a sessão ativa e com o próximo login —
  se o token carrega o e-mail, isso precisa estar tratado.

Testes: troca válida; e-mail já usado → `422` sem gravar; formato inválido → `422`; tentar trocar o
de outro usuário → `403`.

## Bloco 5 — Registrar o que não coube

**Este bloco é obrigatório mesmo que você pare antes dele.** Faça-o por último, mas faça.

Acrescente a `docs/14-pendencias-de-funcionalidade.md`, cada item com uma linha de escopo e o
motivo de ter ficado fora:

- **Aba "Informações para a IA"** na Automação — tabelas enviadas à IA. Falta definir **quais**
  tabelas; sem isso não é implementável
- **Modelos de filho por nicho** (clínica, etc.) na Base PAI — arquitetural, muda o provisionamento
- **Aba de Novidades da matriz** — módulo novo, gerenciado por ADM, abre sozinho quando há novidade
- **Templates da Meta por login**, com imagem e arquivo — integra com a API de templates da Meta
- **Entrar e sair de um atendimento em andamento**, com pedido de permissão pela Agenda — muda o
  modelo de posse do atendimento
- **Login como outro usuário pela administração** — ver a seção final
- **Chat interno da equipe** (fase 2) — na aba Atendimentos, ícone próprio, sem resumo de IA e sem
  painel de lead. Explicitamente fora do escopo atual

---

## Definição de pronto

- [ ] Blocos executados **em ordem**, cada um com teste, commit e push próprios
- [ ] Bloco 5 feito, mesmo que os anteriores não tenham fechado
- [ ] Nenhum bloco parcialmente commitado
- [ ] Smoke RLS atualizado **conscientemente**, se o Bloco 2 entrou
- [ ] CI verde com **número da run**
- [ ] A aplicação sobe e o login abre — confirme, não presuma

## No relatório

1. **Em qual bloco você parou e por quê.** É a informação mais importante do relatório.
2. **Os nomes dos testes novos, um por linha.** Não informe o total da suíte.
3. Se o Bloco 2 entrou: o que mudou na visibilidade, o que mudou no smoke, e o que **não** foi
   verificado (Dashboard, ranking, comissão).
4. Se o Bloco 0 quebrou o scroll e você reverteu.
5. Variável nova no Dokploy: expectativa **nenhuma**.
6. O SHA final **e o SHA curto** — `SYNAPSE_IMAGE_TAG` usa a tag curta, nunca `latest`.

---

## Fora desta etapa, por decisão — leia antes de "ajudar"

**Login como outro usuário sem senha.** É o pedido mais arriscado da lista. Feito às pressas e sem
revisão, vira porta de acesso a qualquer conta. Se entrar algum dia, entra com trilha de auditoria,
restrição a ADMINISTRADOR, sessão marcada como impersonação e registro de quem entrou em quem.
**Não implemente hoje.** Documente no Bloco 5.

Também fora, por tamanho: templates da Meta, entrar/sair de atendimento, modelos de filho por nicho,
aba de novidades, aba "Informações para a IA", chat interno.

E o de sempre: nenhuma execução, varredura ou disparo de regra de automação — isso é do n8n, por
RN-CRM-07.
