# Prompt E36 — Automação: disponibilidade para a IA e recursos do assistente

> Leia `AGENTS.md` e `frontend/AGENTS.md`. Entrega em 25/08.
> Commite e faça push por bloco.
> Ao encerrar: `cd backend && ./mvnw clean verify` e o **número da run** do CI.

---

## Contexto

A E35 e a E35b entregaram as abas **Follow-up** e **Fidelização**. Esta etapa completa a aba
**Geral · IA**, que hoje tem só os quatro cards de telemetria e a lista de parâmetros chave/valor
(`frontend/src/components/automacao/pagina-automacao.tsx`).

Continua valendo a regra que abre a `V7__automacao_config.sql`:

```sql
-- O CRM configura a automacao; nao a executa (RN-CRM-07).
```

> **Não construa agendador, `@Scheduled`, varredura nem disparo.** Cadastro e leitura. O projeto já
> quebrou uma vez com `@Scheduled` — auto-invocação, mensagens quebradas nos dois sentidos, build
> verde.

### Duas coisas saíram do escopo por decisão do arquiteto

**Avaliação por atendimento** depende da escala do CSAT, que segue em aberto: o banco guarda
`nota SMALLINT CHECK (nota BETWEEN 1 AND 5)` e o protótipo mostra "9,4 / 10". Decidir isso exige o
cliente, e mudar depois — com avaliação real dentro — é migração de dado. Fica para etapa própria.

**Rotinas pré-definidas** exigem schema novo: `horario_trabalho` é por **papel**
(`aplicavel_a VARCHAR(20)`) e o protótipo mostra rotina por **atendente**. É o item mais caro da
tela e o menos usado no primeiro dia. Fase 2, por escrito ao cliente.

> **Não implemente nenhuma das duas.** Se achar que cabem, **pare e avise** — não são esquecimento.

---

## Bloco 1 — Disponibilidade para a IA, separada da presença

Este é o bloco que justifica a etapa.

O protótipo mostra um alternador por atendente com "4 de 6 online" ao lado: **presença e
disponibilidade são coisas distintas na tela.** No código, não são:

```java
// EquipeRepositorioJdbc.atualizarPresenca
INSERT INTO disponibilidade_atendente_ia(atendente_id, disponivel_para_ia)
SELECT id, ? FROM usuario WHERE id = ? AND papel = 'ATENDENTE'
ON CONFLICT (atendente_id) DO UPDATE SET disponivel_para_ia = EXCLUDED.disponivel_para_ia
```

A flag é escrita **junto com a presença**, e só para quem é `ATENDENTE`. Hoje não existe "atendente
online, fora do rodízio da IA": quem fica ONLINE entra no rodízio, sem escolha. Está registrado em
`docs/14` e o protótipo confirma que é requisito.

Requisitos:

- Endpoint para alternar `disponivel_para_ia` de um atendente **sem tocar em `status_presenca`**.
- Autorização espelhando os casos de uso administrativos de
  `com.synapse.crm.automacaoconfig.application.regras`
  (`hasAnyRole('GESTOR','SUBGESTOR','ADMINISTRADOR')`). Não invente papel novo, não afrouxe.
- **Ficar OFFLINE continua tirando da lista da IA** — presença ausente vence disponibilidade
  marcada. O contrário deixa de valer: ficar ONLINE **não liga a flag sozinho** depois desta etapa.
- Alternar disponibilidade de quem não é `ATENDENTE` → erro, sem escrita. A lista é de quem recebe
  lead.
- A tela mostra presença (o ponto e o "4 de 6 online") **e** o alternador, como coisas separadas.

**A migração é a parte perigosa.** Atendentes que hoje dependem do gatilho de presença não podem
sair do rodízio em silêncio quando a imagem subir — de manhã ninguém recebe lead e ninguém sabe por
quê. Decida o que fazer com as linhas existentes e **relate**: preservar o valor atual é o caminho
mais provável.

> **Ponto de parada.** `GET /internal/v1/atendentes/disponiveis` filtra por
> `disponivel_para_ia = TRUE AND ativo AND papel = 'ATENDENTE' AND status_presenca = 'ONLINE'`. Se
> separar os dois conceitos mudar **quem** esse endpoint devolve de um jeito que você não consiga
> preservar, pare e avise. É a lista que decide para quem a IA entrega lead, e os atendentes
> trabalham por comissão — errar isso é incidente comercial, não bug de tela.

## Bloco 2 — Recursos de IA

Dois interruptores no protótipo: **resumo automático por IA** e **preenchimento automático**.

- **Resumo** já tem casa: `configuracao_resumo_ia`, singleton com `ativo`, `gatilho` e
  `quantidade_mensagens` (`CHECK (id = 1)`). Exponha o que existe; não duplique em
  `configuracao_automacao`.
- **Preenchimento automático** entra como chave em `configuracao_automacao`, com `tipo = 'BOOLEAN'`
  e `descricao` preenchida. **Decisão do arquiteto: não criar tabela para um interruptor.**
- Os dois precisam ser legíveis pelo n8n em `/internal/v1`. Interruptor que o painel liga e a
  Automação não lê é interruptor decorativo — e o usuário não tem como perceber.

## Bloco 3 — Quem escreve a telemetria

Os quatro cards leem `status_automacao_telemetria`, singleton. Existe
`RegistrarEventoDeAutomacaoUseCase` com `@PreAuthorize("hasRole('SERVICO')")` — provável escritor,
confirme.

Responda no relatório: **quem atualiza essa linha hoje, e com que frequência.** Se ninguém atualiza,
a tela mostra zero — e zero parece dado, não parece ausência.

Se for o n8n, documente o contrato em `docs/16`. Se ninguém escreve, é achado de etapa e vai em item
próprio.

> **Não invente um job para preencher a telemetria.** Se estiver vazia, o conserto é combinar com o
> Dylan, não criar um agendador que a RN-CRM-07 proíbe.

---

## Testes — a proteção nasce com um teste que a viola

Pelo controller real, como a E35b fez. Teste que chama caso de uso direto não conta.

- Alternar `disponivel_para_ia` **não altera** `status_presenca`.
- Mudar presença para ONLINE **não liga** a flag. Este é o teste que prova que a separação
  aconteceu; sem ele o bloco não está feito.
- Atendente ONLINE com a flag desligada **não** aparece em `/internal/v1/atendentes/disponiveis`.
- Atendente OFFLINE com a flag ligada **não** aparece.
- Alternar disponibilidade de um SUBGESTOR ou GESTOR → erro, **e nada gravado**.
- Autorização: ATENDENTE tentando alternar → `403`, **e nada gravado** (verifique o banco, não só o
  status).
- Interruptores de IA: o que o painel grava é exatamente o que `/internal/v1` devolve.
- Valor fora da faixa em `configuracao_automacao` → `422` da aplicação, não do `CHECK` do banco.
- Front: alternador refletindo o estado real, e presença exibida separada da disponibilidade.

## Definição de pronto

- [ ] Disponibilidade alternável sem tocar em presença; presença não liga a flag
- [ ] OFFLINE continua vencendo disponibilidade marcada
- [ ] Papel não-ATENDENTE recusado, sem escrita
- [ ] Autorização espelhada dos casos de uso administrativos existentes
- [ ] Migração das linhas de `disponibilidade_atendente_ia` decidida e relatada
- [ ] Resumo e preenchimento automático legíveis pelo n8n
- [ ] Quem escreve `status_automacao_telemetria` respondido
- [ ] Avaliação por atendimento e rotinas **não** implementadas
- [ ] Nenhum literal de UI, nenhuma cor hardcoded
- [ ] Os testes acima, pelo controller
- [ ] CI verde com **número da run**

## No relatório

1. **Os nomes dos testes novos, um por linha** — classe e método. Não informe o total da suíte:
   "N testes passaram" não distingue teste novo de teste que já passava.
2. Confirme que o teste de separação **falha** quando você reintroduz a escrita da flag junto com a
   presença. Teste que passa de primeira sobre código não testado merece desconfiança.
3. O que aconteceu com as linhas de `disponibilidade_atendente_ia` já existentes.
4. Quem escreve a telemetria, e com que frequência.
5. Variável nova no Dokploy: expectativa **nenhuma**.
6. O SHA final **e o SHA curto** — `SYNAPSE_IMAGE_TAG` usa a tag curta que o
   `type=sha,format=short` publica, nunca o hash de 40 caracteres e nunca `latest`.

---

## Fora desta etapa

Avaliação por atendimento (depende da escala do CSAT). Rotinas pré-definidas por atendente (schema
novo, fase 2). `mensagem_festiva` — tabela existe, sem aba no protótipo, fora da primeira entrega.
Qualquer execução, varredura ou disparo de regra: é do n8n, por RN-CRM-07.
