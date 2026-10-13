# Auditoria: negociação e resultado comercial por atendimento

## Levantamento em origin/main e6bc480

| Requisito | Já existia | Lacuna corrigida |
| --- | --- | --- |
| Classificar negociação | Contrato interno idempotente, somente serviço | Sem classificador no CRM; preservado |
| Finalização com escolha | Transação única, conflito 409, eventos pós-commit | Pergunta passa de modal central para popover junto a Finalizar |
| Registro manual | PUT autenticado, capacidade finalizar, recorte do lead | Continua sem entrada visual, em todos os papéis/instâncias; removida lógica inacessível do header |
| Menu esquerdo ⋯ | Finalizados e finalização em lote | Preservado; futura entrada manual somente ali, com ciclo explicitamente selecionado |
| Automação registra resultado | Não existia contrato nem origem técnica | POST interno aditivo, identidade AUTOMACAO, sem usuário fictício |
| Cartão comercial | Indicava resultado quando preenchido | Ausência passa a indicar Sem resultado registrado; não implica Não vendeu |
| Dashboard | Vendas por eventos GANHO (ADR-008) | Agregado independente por ciclo (ADR-010), sem substituir a fórmula legada |

## Contrato para Dylan

A classificação permanece `POST /internal/v1/atendimentos/{atendimentoId}/negociacao`,
com `{"emNegociacao":true}`. A análise de preços, PDF e inatividade pertence ao n8n;
nenhuma dessas heurísticas foi implementada no CRM.

O registro explícito do resultado usa:

```text
POST /internal/v1/atendimentos/{atendimentoId}/resultado-venda
X-Synapse-Token: <segredo interno configurado>
Idempotency-Key: <identificador estável exclusivo deste comando>
Content-Type: application/json
```

```json
{"resultado":"VENDEU"}
```

Valores: `VENDEU` ou `NAO_VENDEU`. Resposta 200: `atendimentoId`, `resultado`,
`registradoEm` (UTC/ISO-8601), `origem` (`AUTOMACAO` no primeiro registro técnico).
Se o mesmo resultado já foi registrado por humano, preservam-se ator, data e origem anteriores.
Não há valor monetário presumido, classificação implícita ou finalização por esse comando.

Reutilizar a mesma chave/mesmo corpo devolve a resposta persistida, inclusive após encerrar o ciclo.
Mesma chave com outro comando/corpo: 409. Nova chave com resultado idêntico no ciclo aberto:
200 sem segunda auditoria/evento. Escolha conflitante: 409, sem sobrescrita.
Chave/corpo inválido: 400; token ausente/inválido: 401; ciclo inexistente: 404;
ciclo encerrado com comando novo: 409. Erros usam Problem Details.

Usar o UUID do **atendimento/ciclo**, nunca resolver o ciclo atual somente pelo lead.
Callback antigo não pode escrever em um novo atendimento. Para finalizar depois de registrar,
usar o contrato interno existente `/finalizar` com outra chave estável; negociação sem resultado
continua 422. As duas chamadas técnicas são comandos separados; a escolha humana no popup
continua usando uma única operação transacional de registro e finalização.

## Persistência, autorização e atualização

V102 amplia os CHECKs da V97: `AUTOMACAO` exige ator humano nulo; MANUAL/FINALIZACAO
continuam exigindo usuário. A V97 aplicada não foi editada. Locks existentes, na mesma ordem
lead → atendimento, serializam registro manual, automação e finalização. Resultado não transfere
responsável, não altera participação consentida ou comissão. Mesmo resultado não publica evento
novo. Auditoria/timeline usam identidade técnica AUTOMACAO e os listeners pós-commit existentes.

O registro manual permanece disponível somente na API protegida, sem botão/menu/atalho.
Não foi criada feature flag para um controle inexistente. Uma reativação futura exige autorização,
teste e implementação no menu esquerdo, não no header. O popover permite Cancelar, Escape e
clique fora antes do envio; durante a operação bloqueia novas escolhas/fechamento. Falha conserva
a pergunta e permite tentativa posterior. Finalizar sem negociação ou com resultado anterior
continua direto. O resultado é um indicador no **cartão**, não uma coluna tabular.

## Dashboard e desempenho

`GET /api/v1/dashboard/visao-geral` acrescenta `resultadosComerciais: {vendeu, naoVendeu}`.
Cada linha de atendimento conta no máximo uma vez; ausência não entra em nenhum contador.
Período/meses/fuso aplicam-se a `venda_registrada_em`; coorte de originação opcional aplica-se
a `lead.criado_em`. A autorização gerencial existente é preservada; não amplia visibilidade
do ATENDENTE. A query usa o pool geral e agrega ambos os resultados em um SQL, sem histórico,
N+1, consulta de contagem na inbox ou polling novo.

O cache passa de namespace v2 para v3 para não reutilizar JSON sem o campo novo. Chaves mantêm
usuário/papel/filtros; TTL, degradação Redis e atualização separada dos números ao vivo não mudam.
A leitura fria acrescenta uma consulta: baseline automatizada anterior 20 (21 com coorte),
nova expectativa 21 (22 com coorte); cache quente permanece 4. Duas leituras simultâneas passam
de 24 para 25 consultas totais. DashboardCustoIT mede os números e a latência; DashboardVisaoGeralIT
imprime EXPLAIN (ANALYZE, BUFFERS) em fixture sintética. Isso **não** comprova custo/CPU em produção;
nenhum índice foi criado sem plano representativo. Antes de liberar para produção, medir esse SQL
em base representativa, sobretudo para anos inteiros/coortes amplas.

Os cards/gráficos legados ainda leem eventos GANHO. Não se soma esse número ao resultado por ciclo.
Denominador de conversão, comissão e substituição visual da métrica continuam fora desta mudança
e exigem decisão explícita. O novo agregado é disponibilizado, não reinterpretado na UI.

## Operação

A inbox unificada agora preserva os campos comerciais já projetados pelo painel (negociação,
resultado, valor opcional, origem e metadados de registro). Antes, seu DTO descartava esses campos,
fazendo o cartão mostrar ausência mesmo com resultado persistido. É uma adição compatível ao
contrato; conversas internas continuam sem campos comerciais. Não acrescenta consultas.

Nenhuma variável nova no Dokploy. Aplicar V102 pelo runner de migrations existente no próximo
deploy autorizado; não foi feito deploy, alteração no n8n ou reprocessamento de produção.
Os exemplos não contêm credenciais reais. Resultados concretos dos testes e CI constam na PR.
