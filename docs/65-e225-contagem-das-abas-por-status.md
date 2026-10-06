# 65. E225 (B5) — contagem das abas do painel filtrando direto pelo status

Mudança de **consulta**, sem mudança de valor, de política de RLS, de migration, de contrato HTTP nem cache. **Ganho de
desempenho não está declarado**: ele só vale com o EXPLAIN real antes/depois que o responsável roda na VPS (abaixo).

## O problema (medido na VPS, janela de pico, JIT desligado)

A contagem da aba **TODOS** — `COUNT(DISTINCT a.lead_id) FROM atendimento a JOIN lead l ON l.id = a.lead_id WHERE EXISTS
(SELECT 1 FROM atendimento aberto WHERE aberto.lead_id = a.lead_id AND aberto.status IN ('EM_ATENDIMENTO','EM_IA'))` — tinha
1.831 chamadas, 540 ms de média e 989 s de tempo desde o reset, ~68% do tempo de todas as contagens. O `EXISTS` é avaliado
para **cada linha de `atendimento`** (todo o histórico), sob a RLS, mesmo que só as linhas abertas decidam o resultado.

## A mudança

`PainelDeAtendimentosRepositorioJdbc.SQL_CONTAR_*` (as contagens que `contar()` usa):

| Aba | Antes | Depois |
|---|---|---|
| TODOS | `WHERE EXISTS (aberto ... status IN (EM_ATENDIMENTO, EM_IA))` | `WHERE a.status IN ('EM_ATENDIMENTO', 'EM_IA')` |
| POTENCIAIS | `WHERE EXISTS (... status = 'EM_IA')` | `WHERE a.status = 'EM_IA'` |
| ATIVOS | `WHERE EXISTS (... EM_ATENDIMENTO AND atendente_id = ?)` | `WHERE a.status = 'EM_ATENDIMENTO' AND a.atendente_id = ?` |
| PENDENTES (atendente e gestão) | `WHERE EXISTS (atendimento visivel LEFT JOIN LATERAL (última mensagem LEAD/ATENDENTE) ... )` | a mesma `LATERAL` sai do `EXISTS` e passa a ser um `LEFT JOIN LATERAL` de `a`, filtrando `a.status = 'EM_ATENDIMENTO'` antes; o `EXISTS` de convite fica, correlacionado a `a.id` |
| FINALIZADOS | `WHERE NOT EXISTS (aberto ...)` | **não muda** (é um `NOT EXISTS`; não tem forma equivalente) |

A listagem **não muda**: ela continua usando os `WHERE_*` por lead como fonte da fase 1 (`escolher`).

## Por que o valor é o mesmo (e como foi provado)

A contagem antiga contava os leads (visíveis pela RLS de `lead`) que tinham alguma linha `a` visível e um atendimento aberto
**visível** do mesmo lead. O atendimento aberto é, ele próprio, uma linha de `atendimento`; filtrar `a` pelo status aberto devolve
os mesmos leads, e `COUNT(DISTINCT a.lead_id)` continua contando cada lead uma vez. `a` e `aberto`/`visivel` são a mesma tabela sob
a mesma política de RLS: um lead cujo único atendimento aberto é de um colega (invisível) fica de fora nas duas formas. Em
PENDENTES, as condições dependem só da linha do atendimento aberto (status, dono, última mensagem LEAD/ATENDENTE, convite), então
podem ser avaliadas nessa linha.

**Prova (teste, não só argumento):** `PainelContagemEquivalenciaIT` roda a consulta antiga (recomposta com `contar(WHERE_*)`) e a
nova sob a RLS real (`SET LOCAL ROLE synapse_app` + `app.papel` + `app.usuario_id`) e compara o número, em cada aba, para
**ATENDENTE** (dois usuários), **OPERADOR** e **GESTOR**, com: atendimento de outro atendente (invisível), participante ativo e
participante que saiu, convite pendente vigente e convite expirado, lead visível cujo único aberto é de um colega, lead com
**aberto e finalizado**, lead **só com finalizado**, lead com dois atendimentos abertos (conta uma vez), última mensagem de
sistema depois da do lead e atendimento sem mensagens. O teste exige contagens não nulas e que os papéis divirjam onde a RLS
manda (para não ser vacuo). Foi validado contra uma **mutação deliberada** da contagem nova de PENDENTES (trocar `'LEAD'` por
`'ATENDENTE'`): o IT falhou. `PainelDeAtendimentosRepositorioJdbcTest` fixa a forma do SQL.

## Por que só estas abas

Foram aplicadas nas quatro abas de andamento porque a equivalência vale (todas as condições dependem só da linha do atendimento
aberto, e `EXISTS`/filtro direto leem a mesma tabela sob a mesma RLS) **e** o IT a prova para cada uma. FINALIZADOS não entra:
"lead sem nenhum atendimento aberto" não se escreve como filtro de linha.

## Como medir (o que falta para declarar ganho)

Scripts gerados do mesmo código, em `docker/operacoes/`: `e225-explain-contagem-antes.sql` e `e225-explain-contagem-depois.sql`.
Cada aba tem uma medição **literal** e uma **genérica** (`PREPARE` + `plan_cache_mode = force_generic_plan`), cada uma com **3
repetições** na mesma transação; compare o **menor** `Execution Time`.

```bash
for v in antes depois; do
  psql -d matriz_hml -v papel=GESTOR -v usuario=<uuid-do-gestor> -f docker/operacoes/e225-explain-contagem-$v.sql > saida-gestor-$v.txt
  psql -d matriz_hml -v papel=ATENDENTE -v usuario=<uuid-do-atendente> -f docker/operacoes/e225-explain-contagem-$v.sql > saida-atendente-$v.txt
done
grep -E "^-- |Execution Time" saida-gestor-antes.txt saida-gestor-depois.txt
```

Observação do responsável (uma execução, com ruído, como `synapse_app`): TODOS com `GESTOR 387 = 387` e `ATENDENTE 120 = 120`;
93,2 → 7,2 ms e 437,5 → 13,5 ms. Isso é indício; **o número que vale é o das 3 repetições**.

## Limite conhecido

Se a contagem de TODOS ainda pesar depois disto, o próximo passo é desnormalizar (coluna de "tem atendimento aberto" no lead
mantida na mesma transação que muda o status), em PR separado e com a mesma prova de equivalência. Não há cache neste PR.
