-- =========================================================
-- Funil padrao de cinco etapas + PERDIDO.
--
-- Por que este arquivo existe: `provisionar-instancia.sql` monta o funil a
-- partir de SYNAPSE_ETAPAS_JSON, mas o exemplo de instancia entregava so tres
-- etapas, e `seed-demonstracao.sql` exige no minimo cinco. Uma instancia
-- provisionada pelo exemplo nascia, portanto, incapaz de receber o seed.
--
-- Este script aplica exatamente o mesmo bloco de etapas do provisionamento,
-- sem tocar em canal, credencial, tags ou configuracao. Use quando a instancia
-- ja esta provisionada e so o funil precisa ser corrigido.
--
-- ATENCAO: `ON CONFLICT (ordem) DO UPDATE` RENOMEIA as etapas que ja existem
-- nas ordens 1..6. Leads que estavam na ordem 2 passam a exibir o nome novo
-- da ordem 2. Em homologacao isso e desejado; contra dados de cliente real,
-- confira antes qual nome ocupa cada ordem.
--
-- Os nomes abaixo sao um funil comercial generico. Confirme com o cliente
-- antes de usar em producao.
-- =========================================================

\set ON_ERROR_STOP on

BEGIN;

-- Limpar o GANHO atual antes de gravar o novo evita violar
-- idx_etapa_unica_ganha no meio do INSERT quando a etapa vencedora muda de
-- lugar no funil. A transacao inteira continua atomica para os demais leitores.
UPDATE etapa_atendimento SET resultado = 'EM_ANDAMENTO' WHERE resultado = 'GANHO';

WITH etapas AS (
    SELECT nome, ordem, cor_visual, resultado
      FROM jsonb_to_recordset('[
        {"nome":"Novo lead",        "ordem":1,"cor_visual":"#64748B","resultado":"EM_ANDAMENTO"},
        {"nome":"Em atendimento",   "ordem":2,"cor_visual":"#3B86E6","resultado":"EM_ANDAMENTO"},
        {"nome":"Orçamento enviado","ordem":3,"cor_visual":"#F59E0B","resultado":"EM_ANDAMENTO"},
        {"nome":"Negociação",       "ordem":4,"cor_visual":"#8B5CF6","resultado":"EM_ANDAMENTO"},
        {"nome":"Venda fechada",    "ordem":5,"cor_visual":"#22C55E","resultado":"GANHO"},
        {"nome":"Perdido",          "ordem":6,"cor_visual":"#EF4444","resultado":"PERDIDO"}
      ]'::jsonb)
           AS entrada(nome TEXT, ordem SMALLINT, cor_visual TEXT, resultado TEXT)
)
INSERT INTO etapa_atendimento (id, nome, ordem, cor_visual, resultado)
SELECT gen_random_uuid(), nome, ordem, cor_visual,
       COALESCE(resultado, 'EM_ANDAMENTO')::resultado_etapa
  FROM etapas
ON CONFLICT (ordem) DO UPDATE
    SET nome = EXCLUDED.nome,
        cor_visual = EXCLUDED.cor_visual,
        resultado = EXCLUDED.resultado;

-- Falha alto se o resultado nao satisfaz o que o seed exige, em vez de deixar
-- o proximo passo descobrir sozinho.
DO $$
BEGIN
    IF (SELECT count(*) FROM etapa_atendimento) < 5 THEN
        RAISE EXCEPTION 'funil continua com menos de cinco etapas';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM etapa_atendimento WHERE resultado = 'GANHO') THEN
        RAISE EXCEPTION 'funil ficou sem etapa GANHO';
    END IF;
END $$;

COMMIT;

\echo 'Funil aplicado. Etapas:'
SELECT ordem, nome, resultado, cor_visual FROM etapa_atendimento ORDER BY ordem;
