# Prompt E106 — Aba "Todos": o atendente vê o que é dele

> Leia `AGENTS.md` e `CLAUDE.md`. Branch própria (`fix/recorte-da-aba-todos`) e PR.
> **Sem merge, sem deploy.** Backend: `./mvnw -pl crm-atendimento -am verify` (suba para o reator se
> tocar mais de um módulo). Frontend só se o Bloco 4 provar que precisa. **Sem migration.**
>
> **P0, produção, cliente reclamando agora.** Etapa curta e cirúrgica. Não aproveite para melhorar
> nada que não esteja escrito aqui.

---

## O problema

Na aba **Todos** de Atendimentos, todo atendente vê as conversas que a IA está tocando — inclusive as
mensagens exatas do cliente. A equipe leu isso como vazamento.

Não é. A política RLS diz que lead **sem dono** (`status_basico = 'IA'`) é visível a todos os
atendentes — é a RN-CRM-01 funcionando como foi desenhada, para o grupo "Potenciais". A aba `TODOS`
é a única sem recorte próprio: ela devolve tudo que a RLS deixar passar.

Diagnóstico já feito em produção: um atendente enxerga **1** lead com dono diferente do dele, e por
participação, que é comportamento desenhado da V36. Todo o resto que ele vê são leads em IA.
**Não é vazamento de política. É recorte de tela faltando.**

## Bloco 0 — A regra que não pode ser quebrada

**Não toque na RLS.** Nenhuma política nova, nenhum `OR` removido de `rls_lead` ou `rls_atendimento`,
nenhuma migration.

O motivo é concreto e já mordeu antes. `IniciarNovoContatoUseCase` usa
`leads.visivelPorTelefone(telefone)` para reaproveitar um lead existente quando o atendente digita
um número. Se a RLS deixar de mostrar o lead sem dono para o atendente, essa busca volta vazia, o
código tenta criar, o índice único de `lead.telefone` (V24) barra, e o atendente recebe 404 — o
mesmo sintoma de "não consigo puxar esse cliente" que já está aberto como P1.

Ou seja: **estreitar a RLS conserta a tela e quebra o produto.** O recorte é na consulta da tela,
não na política.

## Bloco 1 — Aba "Todos" de Atendimentos

Em `PainelDeAtendimentosRepositorioJdbc`, `TODOS` é o único caso sem `WHERE`. Passa a ter recorte,
**dependente do papel**:

- **Atendente:** vê o lead de que ele é responsável (em qualquer estado, inclusive finalizado) e
  aquele em que ele é participante ativo.
- **Gestor, subgestor e administrador:** nada muda. A visão geral é o trabalho deles.

O sinal de papel já existe e já chega até aqui: `ListarAtendimentosVisiveisUseCase` passa
`!atual.enxergaTodosOsLeads()` como `restritoAoProprioAtendente`. Hoje `TODOS` ignora esse
parâmetro. Use o mesmo, não invente um segundo.

**Formule pela posse, não pelo status.** "Excluir os `EM_IA`" está errado: sumiria também o lead que
é seu e que voltou para a IA. A pergunta certa é "isto é meu?", não "em que estado isto está".

Cuidados:

- `listar`, `listarPaginado` e `contar` compartilham as constantes de `WHERE` justamente para não
  existirem duas definições de "o que é visível". Mantenha assim — o contador tem que bater com a
  lista.
- A E99 acabou de entrar: lead finalizado que é seu **continua aparecendo**, agora abaixo da
  divisória. Não deixe o recorte novo comer os finalizados.
- Potenciais não muda. É lá que os leads em IA continuam vivos, e é para lá que o atendente vai
  quando quiser pegar um.

## Bloco 2 — A Agenda NÃO muda. Nada de ocultar contato.

Decisão do cliente, tomada depois da primeira versão deste prompt: **todos os usuários continuam
enxergando todos os contatos na Agenda.** Não há ocultação, nem por papel, nem por "tem conversa ou
não".

Isso é instrução explícita, não omissão. Se ao ler o Bloco 1 você achar elegante aplicar o mesmo
recorte à Agenda — **não aplique.** `ListarLeadsUseCase` fica exatamente como está.

Vale inclusive para os contatos que serão importados do CSV (E105): eles vão aparecer para todo
mundo na Agenda, de propósito. Note que isso **não** polui Atendimentos: aquela consulta parte de
`FROM atendimento a JOIN lead l`, e contato sem nenhum atendimento não gera cartão em aba nenhuma.

Se você encontrar algum problema concreto de desempenho na Agenda com base grande, **relate** em vez
de resolver escondendo linha.

## Bloco 3 — O lead que aparece por participação

Não mexa nisso nesta etapa. É comportamento desenhado na V36 e o volume é 1 lead, não é o incidente.

Mas **relate**: existe algum caminho que encerra participação automaticamente ao finalizar o
atendimento? Se não existir, diga isso claramente — participação que só termina por ação manual
acumula, e daqui a alguns meses vira o problema que hoje é só um caso.

## Bloco 4 — Frontend

A expectativa é **zero mudança**. As abas e contadores já vêm do backend. Se alguma tela assumir que
"Todos" é superconjunto das outras e quebrar, conserte o mínimo e explique. Se nada quebrar, diga que
não tocou.

## Bloco 5 — Testes

Sem estes testes esta etapa não vale nada, porque o que ela muda é quem vê o quê:

- Atendente A **não** vê, na aba Todos, lead em IA sem dono; vê o mesmo lead em Potenciais.
- Atendente A vê, na aba Todos, o lead dele — inclusive o finalizado (regressão da E99).
- Atendente A vê, na aba Todos, o lead em que é participante ativo.
- Gestor vê tudo na aba Todos, exatamente como antes.
- `contar` bate com `listar` em todas as abas e para os dois papéis.
- **Agenda inalterada:** um teste que prove que a listagem de leads devolve o mesmo conjunto de
  antes, para atendente e para gestão. É a trava do Bloco 2.
- **Regressão do "puxar":** com o recorte novo em vigor, o atendente ainda consegue iniciar contato
  digitando o telefone de um lead que ele não enxerga na lista, e o sistema **reaproveita o lead
  existente em vez de criar outro**. Esse é o teste que prova que você não estreitou a RLS.

## Verificação

```
./mvnw -pl crm-atendimento -am verify      # na raiz de backend/
```

## Relatório

1. O `WHERE` novo de `TODOS`, e como ele trata o lead finalizado próprio.
2. Confirmação de que `ListarLeadsUseCase` e a Agenda ficaram intocados.
3. Confirmação de que **nenhuma** política RLS e **nenhuma** migration foram tocadas.
4. O resultado do teste de regressão do "puxar cliente".
5. O que você encontrou sobre encerramento de participação ao finalizar (Bloco 3).
