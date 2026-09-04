# Prompt E105 — Cliente que não dá para "puxar" + importação de leads por CSV

> Leia `AGENTS.md` e `CLAUDE.md`. Branch própria (`fix/puxar-cliente-e-importar-leads`) e PR.
> **Sem merge, sem deploy.** Backend + possivelmente frontend: `./mvnw verify` no reator, e a suíte do
> frontend se tocar `frontend/`.
>
> **Sistema em produção com clientes reais.** As duas partes desta etapa mexem em quem enxerga o quê.
> Se em qualquer momento a sua correção alargar visibilidade de lead, **pare e relate** em vez de
> seguir — tem um incidente aberto exatamente sobre isso.

---

## Contexto: as duas partes são o mesmo assunto

1. Alguns clientes **não conseguem ser "puxados"** por um atendente (iniciar atendimento / transferir
   falha).
2. Precisamos **importar a base de leads de um CSV** para a agenda da Estrutural.

Elas andam juntas porque a segunda pode multiplicar a primeira por mil: lead importado com o dono
errado, ou com o status errado, é lead que ninguém consegue puxar depois — só que aí serão milhares,
não alguns.

---

# PARTE 1 — Diagnóstico primeiro, correção depois

## Bloco 1 — Não conserte antes de reproduzir

O relato é vago de propósito ("acho que é iniciar atendimento ou transferência"). **Sua primeira
entrega é o diagnóstico, não o patch.** Reproduza em teste, identifique o caminho exato, e só então
proponha.

Comece pela hipótese mais forte, que está escrita no javadoc do próprio
`IniciarNovoContatoUseCase`:

> *"Telefone de colega: a RLS esconde a linha e o indice unico impede o insert — os dois casos viram
> o mesmo 404 da RecursoDeAtendimentoIndisponivelException."*

Ou seja: se o lead **já existe e pertence a outro atendente**, a RLS esconde a linha,
`visivelPorTelefone` volta vazio, o código tenta criar, o índice único de `lead.telefone` (V24)
barra, e o atendente recebe 404 — a mesma resposta de "esse número não existe". Ele não tem como
saber que o cliente está com um colega.

Verifique se é isso. E note que hoje **isso é comportamento desenhado**, não bug de implementação:
a RN-CRM-01 existe para o atendente não enxergar carteira alheia. O que está errado é o **produto**,
não necessariamente o código — o atendente fica sem saber o que fazer.

## Bloco 2 — As outras hipóteses, na ordem

Descarte cada uma explicitamente no relatório, com evidência:

- **Telefone que não casa.** `lead.telefone` é dígitos puros com DDI (V24/V26). Um número gravado
  sem o DDI, ou com o nono dígito diferente do que o cliente usa no WhatsApp, vira lead diferente.
  Essa é a segunda hipótese mais forte, e a que mais aparece no Brasil.
- **Lead finalizado.** Depois da E99 o lead volta pela reabertura. Confirme que esse caminho
  funciona para lead de outro atendente e para lead sem dono.
- **Transferência.** A E53 pôs `destinos.exigirAtendenteAtivo`. Atendente inativo ou não elegível
  como destino recusa — corretamente. Confirme se é o caso e qual mensagem o atendente vê.
- **Janela de 24h.** `IniciarNovoContatoUseCase` recusa texto livre fora da janela. Se o atendente
  digita e recebe erro, pode ser isso, e não "não consigo puxar".

## Bloco 3 — O que corrigir

Depende do diagnóstico, então **traga a proposta antes de implementar** se o caminho não for óbvio.
O que já está decidido:

- **Não alargue a RLS.** Nenhuma política nova, nenhum `OR` a mais em `rls_lead` ou
  `rls_atendimento`. Se a correção parecer exigir isso, é a correção errada.
- **404 continua sendo 404.** Nunca responda "esse cliente é do atendente Fulano" a quem não pode
  enxergar o lead — isso vaza a carteira exatamente como a RN-CRM-01 proíbe.
- O que **pode** melhorar é a mensagem genérica: dizer ao atendente que o número não está disponível
  para ele iniciar e que ele deve falar com a gestão, em vez de um erro que parece defeito. Escreva
  a mensagem sem revelar dono, nome ou existência do lead.

---

# PARTE 2 — Importação do CSV

## Bloco 4 — Isso NÃO é migration

O arquivo é a base de clientes **da Estrutural Vidros**. Uma migration roda em toda instância, hoje
e no futuro: a carteira de um cliente entraria no repositório do produto e seria aplicada em
qualquer filho novo. **Não faça migration.**

Entregue um **script de importação operacional**, na linha do que já existe em
`docker/provisionamento/` — rodado uma vez, à mão, contra a instância certa, com o arquivo passado
por fora e **nunca commitado**. Adicione o CSV ao `.gitignore` se for preciso deixá-lo na pasta
durante o desenvolvimento.

## Bloco 5 — Regras da importação

- **Idempotente.** Rodar duas vezes não pode duplicar nada. `lead.telefone` tem índice único parcial
  (V24, `WHERE telefone IS NOT NULL`) — use-o. Reimportar é o caso normal, não a exceção.
- **Telefone canônico.** Só dígitos, com DDI, exatamente como `TelefoneCanonico` normaliza no
  código. Não escreva uma segunda regra de normalização em SQL: se a do código e a do script
  divergirem, você cria leads duplicados que ninguém consegue puxar — que é justamente a Parte 1
  deste prompt. Diga no relatório como garantiu que as duas regras são a mesma.
- **Linha inválida não derruba a importação.** Telefone vazio, curto demais, com letras: registre e
  siga. No fim, relate quantas entraram, quantas já existiam e quantas foram recusadas, **com o
  motivo** — e sem imprimir o telefone completo no log.
- **Lead que já existe não é sobrescrito.** Se o número já está na base, com dono e histórico, a
  importação não pode mexer em nada dele. `DO NOTHING`, não `DO UPDATE`.

## Bloco 6 — Com que status os leads nascem: já decidido

**`status_basico = 'IA'`, sem dono.** Não é escolha sua, e não precisa trazer opções.

O cliente decidiu que **todos os usuários enxergam todos os contatos na Agenda** — sem ocultação,
nem por papel, nem por "tem conversa ou não". Como a política `rls_lead` mostra a todo atendente o
lead com `status_basico = 'IA'`, esse é exatamente o status que entrega o combinado.

Duas consequências que você deve confirmar lendo o código, e relatar:

- **Atendimentos não é poluído.** `PainelDeAtendimentosRepositorioJdbc` consulta
  `FROM atendimento a JOIN lead l` — parte do atendimento. Contato importado não tem atendimento
  nenhum, então não gera cartão em aba alguma, nem em Todos nem em Potenciais. Ele só aparece lá no
  dia em que aquele número mandar mensagem, como cliente novo.
- **A Agenda passa a listar milhares de linhas para todo mundo**, de propósito. Se você encontrar
  problema concreto de desempenho — consulta sem índice, paginação ausente, tela carregando tudo de
  uma vez —, **relate**. Não resolva escondendo linha: ocultar contato contraria a decisão do
  cliente.

O que continua valendo: lead que **já existe** não é tocado. Se o número já está na base com dono e
histórico, a importação não mexe em status, dono, nem em nada. `DO NOTHING`, nunca `DO UPDATE`.

## Bloco 7 — Antes de rodar em produção

- O script tem que ter um modo **simulação** (conta e valida sem gravar). Rodar primeiro assim, em
  produção, e mostrar o resultado ao Marcondes.
- Backup do banco antes da execução real. Diga no relatório qual comando usar.
- Rodar **em homologação primeiro**, com o arquivo real, e comparar as contagens.

---

## Testes

- Parte 1: teste que reproduz o caso diagnosticado, e que falha se a correção regredir.
- Parte 1: um teste garantindo que a resposta **não** revela dono nem existência do lead alheio.
- Parte 2: importar o mesmo arquivo duas vezes deixa a base idêntica à primeira execução.
- Parte 2: telefone em formatos diferentes (com DDI, sem DDI, com máscara, com e sem nono dígito)
  resulta no mesmo lead, ou é recusado explicitamente — **nunca** em dois leads.
- Parte 2: lead pré-existente com dono e histórico sai intacto da importação.

## Verificação

```
./mvnw verify        # no reator, na raiz de backend/
npm run lint && npm run typecheck && npm run test && npm run build   # só se tocar frontend/
```

## Relatório

1. **O diagnóstico da Parte 1**, com o caminho exato e as hipóteses descartadas, cada uma com
   evidência.
2. A mensagem que o atendente passa a ver, e por que ela não vaza nada.
3. Onde ficou o script de importação, como se roda, e como se roda em simulação.
4. Como você garantiu que a normalização de telefone do script é a mesma do código.
5. Quantas linhas o arquivo tem, quantas entrariam, quantas já existem e quantas seriam recusadas.
6. As duas confirmações do Bloco 6 (Atendimentos não é poluído; estado da Agenda com base grande).
7. O resultado da execução em **modo simulação**, em homologação e em produção — e **nada gravado em
   produção até o Marcondes autorizar** olhando esses números.
