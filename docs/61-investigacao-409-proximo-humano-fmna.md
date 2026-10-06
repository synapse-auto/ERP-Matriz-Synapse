# 61 — Investigação do 409 "nenhum atendente está online e disponível" (FMNA, E222 parte 2)

Auditoria **só de código** (sem acesso à VPS nem ao n8n), feita sobre `origin/main` (`0cb0d49`), antes do PR de
observabilidade ([#269](https://github.com/synapse-auto/ERP-Matriz-Synapse/pull/269)). Linhas citadas são de
`origin/main`. O que depende de dado real está em §6.

## 0. Resposta curta

* O 409 com **essa mensagem** só nasce de **um lugar**: a consulta de candidatos voltou **vazia** (§1). Nada ligado
  ao lead ou ao atendimento entra nessa consulta (§3, §4). **H1 (pool vazio) é a única hipótese que o código
  sustenta; H2 (exclusão por lead/atendimento) o código descarta.**
* "Pool vazio" tem quatro portas no SQL (ativo, papel, marcado para a IA, `ONLINE`) e **a presença é 100% manual**:
  só muda por clique no rodapé da sidebar (§5). Não há heartbeat, timeout, logout nem histórico. Isso torna
  plausível, **mas não provado**, o padrão observado (transferências às 10:17, 10:47, 10:59 e 16:07, nenhuma entre
  11:00 e 16:07): quem trabalhava podia estar com o status diferente de `ONLINE` ou sem a marcação para a IA.
* Achado novo (H3): a consulta usa **`JOIN` em `disponibilidade_atendente_ia`**, e **criar usuário não cria linha
  ali** (§2). Quem foi criado depois dos backfills V34/V51 e nunca passou pelo toggle **nunca entra no rodízio**,
  mesmo ativo e `ONLINE`.
* A causa **nas execuções da Fêmina de 05/10 não está provada**: o estado de presença daquela hora não fica gravado
  em lugar nenhum. O PR #269 faz a **próxima** ocorrência trazer os números (log + `motivo`).

## 1. Onde nasce o 409 e a mensagem (item 1)

| O quê | Arquivo:linha |
|---|---|
| Endpoint | `TransferenciaAutomacaoInternalController.java:159` (`POST /{id}/transferir-proximo-humano`), sem corpo, só `Idempotency-Key` |
| Caso de uso de comando (idempotência + transação) | `ComandosAutomacaoUseCase.java:100` |
| Única condição que lança | `TransferirAtendimentoDaAutomacaoUseCase.java:44-46`: `listarDisponiveis.executar().stream().findFirst().orElseThrow(NenhumAtendenteDisponivelException::new)` |
| Mensagem | `NenhumAtendenteDisponivelException.java:7` |
| Vira 409 | `TransferenciaAutomacaoInternalController.java:204` (handler `aoConflitar`, título `Operacao nao pode ser aplicada`) |

* Antes da consulta há um lock consultivo (`TransferirAtendimentoDaAutomacaoUseCase.java:42`,
  `bloquearDistribuicaoDaAutomacao`): serializa distribuições, não filtra ninguém.
* **Log:** nenhum. A exceção é tratada pelo `@ExceptionHandler` sem logar — por isso as 4585 linhas da VPS não têm
  "nenhum atendente". (Corrigido no #269.)
* `tipo_transferencia` **não existe no repositório inteiro** (nem em `origin/main`): o endpoint não recebe
  parâmetros. Se o nó do n8n envia `tipo_transferencia=geral`, o CRM **ignora**. Não verificado como o n8n envia.
* **Três 409 diferentes saem do mesmo handler** (mesmo título): chave reutilizada, atendimento fora de `EM_IA`/finalizado
  e este (sem destino). Só o `detail` os distingue.

## 2. Consulta de candidatos (item 2)

`AtendenteDisponivelRepositorioJdbc.java`: estratégia lida de `configuracao_automacao` (`ia.distribuicao.sequencial`,
linhas 123-128; ausente ⇒ menor carga). Duas consultas com **os mesmos filtros**, só a ordem muda:

* Menor carga: linhas 29-67. Sequencial: linhas 69-99.
* `FROM disponibilidade_atendente_ia d JOIN usuario u ON u.id = d.atendente_id` (linhas 57-58 e 91-92)
* `WHERE d.disponivel_para_ia = TRUE AND u.ativo = TRUE AND u.papel IN ('ATENDENTE','SUBGESTOR') AND
  u.status_presenca = 'ONLINE'` (linhas 61-63 e 94-96).
* `carga` e `recebimentos` entram por `LEFT JOIN`: só **ordenam**, nunca excluem.

**De onde vêm os dois campos:**

| Campo | Onde está de verdade |
|---|---|
| Disponível para a IA | tabela `disponibilidade_atendente_ia.disponivel_para_ia` (`V2__equipe.sql:42-46`, `DEFAULT FALSE`), **não** em `usuario` — por isso a coluna `usuario.disponivel_para_ia` não existe na VPS. As notas de setembro estavam erradas |
| Presença | `usuario.status_presenca` (`V2__equipe.sql:12`, `DEFAULT 'OFFLINE'`) |
| Ativo / papel | `usuario.ativo`, `usuario.papel` |

**H3 — linha de disponibilidade que nunca existe.** Só três caminhos criam/alteram a linha:
backfills V34 (`ATENDENTE`) e V51 (`SUBGESTOR`), o toggle `atualizarDisponibilidadeParaIa`
(`EquipeRepositorioJdbc.java:31`, upsert) e `desativar` (`:28`, grava `FALSE`). **`criar` (`:23`) insere só em
`usuario`.** Como a consulta usa `JOIN`, um atendente criado depois do V51 e nunca ligado no toggle **não aparece**,
mesmo ativo e `ONLINE`.

## 3. Exclusões ligadas ao lead ou ao atendimento (item 3)

**Nenhuma entra na consulta.** O SQL não recebe o id do atendimento nem do lead. Não há filtro por responsável atual
do lead, atendente atual/anterior, participantes, carteira/fidelização, canal, setor, nem por `tipo_transferencia`
(que não existe). Dois atendimentos diferentes, no mesmo instante, recebem exatamente o mesmo resultado.

## 4. Lead/atendimento que já têm responsável (itens 4 e 6)

Depois que há candidato, `TransferirAtendimentoUseCase.transferir` (`:123`) faz, nesta ordem:

1. carrega o atendimento (404 se não existe/não alcança);
2. `if (exigirOrigemIa && status != EM_IA)` → `TransferenciaDaAutomacaoInvalidaException` (`:134-136`) → **409 com
   outra mensagem** ("atendimento … nao esta sob responsabilidade da IA"). Cobre `EM_ATENDIMENTO` e `FINALIZADO`;
3. valida o destino (`destinos.exigirAtendenteAtivo`, `:152`) e grava (`leads.transferirPara`, `:167`).

Consequências:

* O rodízio **não tenta o responsável antes** e **não o trata**: um lead com responsável humano cujo atendimento
  está `EM_IA` (ex.: o do Ezequiel às 11:00) passa pelo mesmo pool e, se houver candidato, o responsável do lead é
  **trocado** (`leads.transferirPara`).
* **Responsável não elegível não gera 409 "nenhum atendente"**: o responsável nunca é consultado. Se não há
  candidato, o 409 é o desta investigação; se há, a transferência segue.
* **Ordem importa:** a consulta do pool vem **antes** do estado do atendimento. Atendimento já finalizado ou
  `EM_ATENDIMENTO` com pool vazio devolve "nenhum atendente", **não** a mensagem de estado inválido. Atendimento
  inexistente com pool vazio também.
* Por isso, o atendimento `a4e957bb…` (criado 11:00:35, sem atendente) só pode ter dado "nenhum atendente" se o pool
  estava vazio naquele instante.

## 5. Como a presença muda (item 5)

* Única escrita de `status_presenca` fora da migration: `EquipeRepositorioJdbc.atualizarPresenca` (`:30`), chamada
  por `AtualizarMinhaPresencaUseCase` ← `PATCH /api/v1/usuarios/me/presenca` (`UsuarioController.java:126`), e
  `desativar` (`:28`, força `OFFLINE`).
* No frontend, o **único** caller é o seletor manual do rodapé (`sidebar.tsx:161`, e o equivalente mobile em
  `navegacao-inferior.tsx`); não há `useEffect` que ponha `ONLINE` no login nem `OFFLINE` ao sair/ocultar a aba.
* O WebSocket **não toca** presença: `LimpezaDeAssinaturasListener.aoDesconectar` só remove assinaturas.
  Heartbeat STOMP (E193/E203) é de conexão, não de presença.
* **Sem timeout, sem logout, sem histórico:** nenhuma coluna de "quando mudou", nenhuma tabela de presença. Só
  `disponibilidade_atendente_ia.atualizado_em` registra **quando a marcação para a IA mudou** (não a presença).

Efeito prático: `ONLINE` significa "o último clique foi ONLINE". Quem fecha o navegador continua `ONLINE`
(falso positivo); quem trabalha sem clicar, ou voltou de uma pausa e não clicou, fica `OFFLINE`/`AUSENTE` e **sai do
rodízio** (falso negativo).

## 6. Hipóteses

| | Veredito do código |
|---|---|
| **H1** nenhum candidato elegível naquele instante | **Sustentada** — é a única condição que produz a mensagem (§1). |
| **H2** exclusão por lead/atendimento (responsável não elegível etc.) | **Descartada** para esta mensagem (§3, §4). |
| **H3** outra condição | **Achada:** linha de disponibilidade inexistente (JOIN) e presença manual sem histórico (§2, §5). |

Dentro de H1, o código **não decide** qual dos quatro filtros zerou nas execuções da Fêmina. Dados que decidiriam
(rodar na VPS, só leitura):

```sql
-- estado atual do rodízio, por pessoa (sem telefone)
SELECT u.nome, u.papel, u.ativo, u.status_presenca,
       d.disponivel_para_ia, d.atualizado_em AS marcacao_mudou_em,
       (d.atendente_id IS NULL) AS sem_linha_de_disponibilidade
  FROM usuario u LEFT JOIN disponibilidade_atendente_ia d ON d.atendente_id = u.id
 WHERE u.papel IN ('ATENDENTE','SUBGESTOR')
 ORDER BY u.nome;

-- estratégia em uso
SELECT valor FROM configuracao_automacao WHERE chave = 'ia.distribuicao.sequencial';
```

Leitura: Joanna, Claudia e Debora com `ativo`, `disponivel_para_ia = true` e `ONLINE` ⇒ o pool não deveria estar
vazio e a hipótese cai; algum deles `OFFLINE`/`AUSENTE` ou `false`/sem linha ⇒ H1 confirmada com a porta certa.
`marcacao_mudou_em` mostra se a marcação mudou perto de 11:00. **A presença daquela hora não é recuperável** (sem
histórico); só a próxima ocorrência, já com o log do #269, prova.

## 7. O que ficou sem prova

* Presença e marcação para a IA às 11:00–16:07 de 05/10 (sem histórico gravado).
* Se o n8n envia `tipo_transferencia` e onde (o CRM ignora).
* Se aconteceu com clientes reais (só se sabe dos testes com os números do Ezequiel e do Lucas).
* A lista de execuções com erro do nó no n8n (pendência humana).
* O que a IA do n8n faz com a conversa depois do 409 (fora do repositório).

## 8. Decisão de negócio (não aplicada)

Hoje, sem candidato, o `409` desfaz a transação: o atendimento **continua `EM_IA`** (aparece em Potenciais para quem
tem acesso, RN-CRM-01) e **ninguém é avisado**. Opções para o responsável decidir com a FMNA:

| Opção | Como | Risco |
|---|---|---|
| **A. Mensagem "sem atendentes agora"** | O n8n trata o ramo de erro e avisa o paciente (e para o retry, que em 409 não ajuda). Sem mudança no CRM. | Paciente fica sem prazo; depende do Dylan. |
| **B. Fila com nova tentativa** | O CRM guarda "aguardando humano" e redistribui quando alguém ficar elegível, com aviso à equipe. | Fila cresce sem ninguém olhando; precisa de SLA/alarme e de dono; mais código e estado. |
| **C. Fallback para um grupo maior** | Relaxar um filtro (ex.: aceitar `AUSENTE`, ou subgestor sem marcação, ou cair para gestão). | Entrega a quem não está na tela; mexe em quem é elegível (regra comercial das comissões). |
| **D. Presença confiável** | Presença derivada de sessão/heartbeat com timeout, em vez de clique. | Muda o significado de `ONLINE` para todos; maior; precisa de testes de concorrência. |

Recomendação mínima, sem novo código: **A** agora (Dylan) + conferir com a SQL de §6 quem está de fato elegível antes
do teste com clientes. **B/C/D** só depois de a causa estar provada com o log do #269.

## 9. Fora de escopo e pendências humanas

Não alterado: quem é elegível, ordem do rodízio, rodízio sequencial, os outros dois caminhos de transferência, fluxo
do n8n, deploy. Pendências: lista de execuções com erro do nó "Transferir Proximo Humano" com data e hora; Dylan
tratar o ramo de erro (hoje há retry automático e "continuar mesmo se falhar", e retry sobre 409 não ajuda).
