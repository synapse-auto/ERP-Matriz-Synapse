# Prompt E219 (v2) — mensagens automáticas em excesso e sem origem identificada

Número E219 provisório: confirmar que é o próximo livre em `docs/prompts/` antes de salvar. Substitui a v1, que partia da premissa errada (mensagem duplicada). Não é o caso.

## Leitura obrigatória antes de qualquer coisa

Ler `AGENTS.md` e `docs/13-estado-do-projeto.md`. Depois: `docs/21-entrega-contratos-internos-automacao.md`, `docs/38-contrato-uzapi-autotic.md`, os prompts/verificações E194 (fidelização e festivas em `/internal/v1`), E195/E196, e a migration V80 (`idempotencia comandos automacao lead`) — o padrão de idempotência dela (hash SHA-256 + reserva/conclusão + 409 em reuso de chave com payload diferente) é a referência.

## Origem

Card urgente, status Bloqueado: "várias mensagens ainda não identificadas sendo enviadas para vários clientes muitas vezes". O responsável esclareceu: não é mensagem duplicada. É mensagem automática demais — follow-up, fidelização, festiva, aniversário etc. saindo para muitos clientes, e o mesmo cliente recebendo com frequência demais. "Não identificadas" = não se sabe qual fluxo/regra mandou cada uma.

## Evidência já colhida em produção (FMNA, `fmna_db`, 30/09, somente leitura)

* Migrations até a V83 (28/09). Existem `configuracao fidelizacao aniversario` (V74), `mensagem_festiva` (V7), `mensagem_programada` (1 linha, `ENVIADA`), `mensagem_automacao_idempotencia`, `mensagem_envio_idempotencia`, `comando_automacao_idempotencia`.
* Todas as 642 mensagens de IA dos últimos 7 dias têm `remetente_id` nulo: hoje nenhuma mensagem automática carrega origem (regra, fluxo ou execução). É a causa direta de "não identificadas".
* A tabela `mensagem` é particionada (`mensagem_2026_09` … `_12` e `mensagem_default`); qualquer migration nela exige cuidado (lição da V73) — seguir o padrão do projeto, em lote, sem lock longo.
* As mensagens de saída da FMNA têm 1 `wamid` cada (sem reenvio da mesma linha); `webhook_entrada` sem retentados. A outbox do CRM e o webhook de entrada não são a causa aqui.
* Os follow-ups do lado do n8n ficam em `follow_ups_temporary`, tabela criada à mão fora do Flyway (Dylan). O CRM não vê nem controla essa fila. Não se sabe ainda se os disparos em excesso saem dela, de regras do CRM ou de ambos.

Não provado: qual caminho gerou o excesso. O Bloco 0 mapeia os caminhos; os blocos 1 a 3 são correção de defesa que vale qualquer que seja o caminho.

## Bloco 0 — auditoria no código (sem tocar em produção)

Enumerar todo caminho que produz mensagem automática proativa (não resposta a mensagem do lead): agendadores/regras do CRM (fidelização, festivas, aniversário, E194), `mensagem_programada`, lembretes (E51), e envios que o n8n faz via `/internal/v1` (`/responder`, `/mensagens-enviadas`, transferências). Para cada um, responder com arquivo e linha, numa tabela no relatório:

1. gatilho e janela de execução;
2. quem envia ao provedor (CRM ou n8n direto na Uzapi) e como o CRM fica sabendo;
3. deduplicação por (lead, regra, ocorrência) — existe?
4. limite de frequência por lead (cooldown) ou teto diário — existe?
5. com que `remetente_tipo`/`remetente_id` a mensagem é gravada e se algum campo identifica a origem;
6. se existe chave liga/desliga por regra ou global.
Marcar explicitamente os caminhos sem controle de frequência ou sem idempotência por ocorrência. Isso diz onde o excesso é possível hoje.

## Bloco 1 — identificar a origem de toda mensagem automática

* Toda mensagem automática passa a registrar origem: tipo de automação (follow-up, fidelização, festiva, aniversário, lembrete, resposta da IA, outro), id da regra/fluxo quando houver e id de execução do n8n quando o n8n informar.
* Armazenar em coluna nova nullable em `mensagem` (respeitando a partição e o padrão de migration em lote do projeto) ou no mecanismo equivalente que o Bloco 0 indicar — justificar a escolha.
* Os endpoints que o n8n usa para registrar/enviar aceitam esses campos como opcionais (compatível com o n8n atual) e registram aviso quando faltam. Atualizar OpenAPI e o snapshot de `/internal/v1`.
* Os caminhos do próprio CRM (Bloco 0) já gravam a origem.
* Entregar ao responsável uma forma de ver, por origem e por dia, quantas mensagens saíram e para quantos leads (endpoint interno somente leitura ou consulta documentada em `docs/18`).

## Bloco 2 — reserva de envio com política de frequência

Reaproveitar o padrão da V80; checar antes se `mensagem_automacao_idempotencia` já cobre parte.

* Antes de enviar pelo WhatsApp uma mensagem proativa, o chamador (CRM ou n8n) reserva o envio informando (lead, tipo de automação, regra, ocorrência) e uma chave. O CRM responde "pode enviar" ou "não envie" com o motivo (chave já usada, cooldown ativo, teto diário atingido, tipo desligado).
* Quando o envio é registrado, a reserva fecha na mesma transação. Reserva sem resultado vai para uma lista de conferência manual, nunca reenvio automático.
* Chave reutilizada com payload diferente → 409.
* Política configurável por instância: cooldown por (lead, tipo de automação) e teto diário por lead, mais chave liga/desliga por tipo e global. Não inventar valores: deixar os padrões em configuração, sugerir um valor conservador no relatório e deixar o responsável confirmar antes do deploy. O cooldown vale só para mensagens proativas; resposta da IA a mensagem do lead não é afetada.
* Endpoint(s) em `/internal/v1`, `X-Synapse-Token`, `hasRole('SERVICO')`.

## Bloco 3 — contenção imediata

Se o Bloco 0 mostrar caminho do CRM sem controle, propor (sem aplicar) a chave de desligar por tipo para estancar até o deploy. Listar o que cada chave interrompe. Os follow-ups do n8n só param pelo n8n; registrar isso no relatório.

## Testes (obrigatório)

* Bloco 1: mensagem automática do CRM grava origem; n8n sem os campos continua funcionando com aviso; n8n com os campos grava e o endpoint de visão agrega certo.
* Bloco 2: duas reservas simultâneas com a mesma chave → uma vence; cooldown e teto bloqueiam com o motivo certo; resposta a mensagem do lead não é bloqueada; tipo desligado bloqueia; registro de envio fecha a reserva; reserva aberta aparece na lista; chave com payload diferente → 409.
* Regressão: fluxo normal de resposta da IA, envio humano e `/mensagens-enviadas` atual.
* `./mvnw clean verify` local; CI verde; confirmar o job `imagens` verde antes de citar tag.

## Não-objetivos

* Não alterar workflow nem tabelas do n8n (`follow_ups_temporary` etc.); entregar a especificação para o Dylan.
* Não apagar, reprocessar nem reenviar nada; não contatar clientes; não mexer em mensagens já gravadas.
* Não tratar a E209 (`despachado_em`) aqui — é outra frente, ainda pendente.
* Não mexer em MinIO, `CANAL_CB_*` nem no fluxo de mídia do E218.

## Pendências humanas (não bloqueiam este prompt)

* Rodar a consulta de disparos ativos por texto (IA sem mensagem do lead nos 30 min anteriores, agrupada por dia e texto) para saber qual regra está estourando; confirma a causa depois.
* Dylan aplicar no n8n a reserva de envio e o envio dos campos de origem.
* Confirmar de qual instância é o card (dados acima são só da FMNA) e os valores de cooldown e teto.

## Relatório final exigido

* Confirmação de leitura de `AGENTS.md` e `docs/13-estado-do-projeto.md`.
* Tabela do Bloco 0 com arquivo/linha e os caminhos sem controle.
* Diff e migrations (número real da próxima, conferido), testes, resultado local e da CI, job `imagens`.
* Atualização de `docs/38`, `docs/21` e do runbook (`docs/18`).
* O que ficou sem prova e a especificação entregue ao n8n.
