-- =========================================================
-- E180: importacoes brasileiras podem trazer trunk (0) ou operadora (0XX).
--
-- A V50 ja corrigiu o nono digito, mas a funcao antiga deixava o prefixo de discagem intacto.
-- Esta migration atualiza a funcao SQL e corrige os registros existentes sem tocar em numeros
-- que nao possam ser interpretados com seguranca.
-- =========================================================

-- Migrations de dados rodam com o mesmo contexto explicito dos jobs. FORCE RLS nao pode virar um
-- no-op silencioso quando o usuario do Flyway nao e superusuario.
SELECT set_config('app.papel', 'SERVICO', TRUE);

DO $$
BEGIN
    IF NOT app_enxerga_todos_os_leads() THEN
        RAISE EXCEPTION
            'contexto de servico nao aplicado: a limpeza do prefixo enxergaria zero leads';
    END IF;
END $$;

-- >>> regra-prefixo-discagem >>><<< regra-prefixo-discagem <<<
-- A regra e deliberadamente por comprimento: depois do trunk (0) ou da operadora (0XX), o
-- restante precisa ter 10 ou 11 digitos. Prefixos de servico nao sao contatos geograficos.
CREATE OR REPLACE FUNCTION app_telefone_com_ddi(entrada TEXT, ddi_padrao TEXT)
RETURNS TEXT LANGUAGE sql IMMUTABLE AS $$
    WITH limpo AS (
        SELECT regexp_replace(entrada, '[^0-9]', '', 'g') AS digitos
    ), sem_prefixo AS (
        SELECT CASE
                   WHEN length(digitos) >= 4
                        AND left(digitos, 4) IN ('0300', '0400', '0500', '0800', '0900')
                       THEN digitos
                   WHEN left(digitos, 1) = '0'
                        AND length(digitos) - 1 IN (10, 11)
                       THEN substr(digitos, 2)
                   WHEN left(digitos, 1) = '0'
                        AND length(digitos) - 3 IN (10, 11)
                       THEN substr(digitos, 4)
                   ELSE digitos
               END AS digitos
          FROM limpo
    )
    SELECT CASE
               WHEN digitos IS NULL THEN NULL
               WHEN length(digitos) >= 4
                    AND left(digitos, 4) IN ('0300', '0400', '0500', '0800', '0900')
                   THEN CASE WHEN length(digitos) < 10 THEN NULL ELSE digitos END
               WHEN length(digitos) < 10 THEN NULL
               WHEN length(digitos) IN (10, 11) THEN ddi_padrao || digitos
               ELSE digitos
           END
      FROM sem_prefixo;
$$;

CREATE OR REPLACE FUNCTION app_telefone_canonico(entrada TEXT, ddi_padrao TEXT)
RETURNS TEXT LANGUAGE sql IMMUTABLE AS $$
    SELECT CASE
               WHEN com_ddi IS NULL THEN NULL
               WHEN length(com_ddi) = 12
                    AND left(com_ddi, 2) = '55'
                    AND substr(com_ddi, 5, 1) BETWEEN '6' AND '9'
                   THEN substr(com_ddi, 1, 4) || '9' || substr(com_ddi, 5)
               ELSE com_ddi
           END
      FROM (SELECT app_telefone_com_ddi(entrada, ddi_padrao) AS com_ddi) AS base;
$$;

COMMENT ON FUNCTION app_telefone_com_ddi(TEXT, TEXT) IS
    'Remove trunk 0 ou operadora 0XX somente quando o restante tem 10/11 digitos; preserva 0300, '
    '0400, 0500, 0800 e 0900. Depois completa o DDI configurado.';

COMMENT ON FUNCTION app_telefone_canonico(TEXT, TEXT) IS
    'Telefone canonico do CRM: prefixo de discagem brasileiro, DDI e nono digito. '
    'Espelha TelefoneCanonico do dominio; TelefoneNonoDigitoIT.Paridade reprova divergencias.';

DO $$
DECLARE
    ddi_padrao     TEXT := '${telefone_ddi_padrao}';
    tabelas_com_fk TEXT[] := ARRAY[
        'atendimento',
        'campanha_mensagem_metrica',
        'evento_timeline',
        'lead_tag',
        'lembrete',
        'mensagem_programada',
        'mensagem_envio_idempotencia'];
    problema       TEXT;
    par            RECORD;
    linha          RECORD;
    extra          RECORD;
    deixados       INT := 0;
    fundidos       INT := 0;
    normalizados   INT := 0;
    atendimentos_finalizados INT := 0;
BEGIN
    IF ddi_padrao !~ '^[0-9]{1,3}$' THEN
        RAISE EXCEPTION 'DDI padrao deve conter de um a tres digitos; recebido: %', ddi_padrao;
    END IF;

    -- Se uma tabela nova apontar para lead, apagar um perdedor sem trata-la criaria orfaos ou
    -- abortaria no meio da fusao. Falhar fechado e mais seguro que presumir o catalogo.
    SELECT string_agg(format('%s (%s)', conrelid::regclass, conname), ', ' ORDER BY conname)
      INTO problema
      FROM pg_constraint
     WHERE confrelid = 'lead'::regclass
       AND contype = 'f'
       AND conrelid::regclass::text <> ALL (tabelas_com_fk);
    IF problema IS NOT NULL THEN
        RAISE EXCEPTION
            'FK apontando para lead que esta migration nao preve: %. Trate-a antes de fundir.',
            problema;
    END IF;

    -- Uma mesma chave canonica em tres leads nao e o caso deterministico dos 28 pares medidos.
    -- Nao adivinhar: a migration continua e deixa o grupo para revisao manual.
    SELECT string_agg(format('%s -> [%s]', canonico, ids), E'\n' ORDER BY canonico)
      INTO problema
      FROM (
            SELECT app_telefone_canonico(telefone, ddi_padrao) AS canonico,
                   string_agg(id::text, ', ' ORDER BY id) AS ids
              FROM lead
             WHERE telefone IS NOT NULL
               AND telefone !~ '^55[1-9][0-9]{9,10}$'
               AND app_telefone_canonico(telefone, ddi_padrao) ~ '^55[1-9][0-9]{9,10}$'
             GROUP BY 1
            HAVING count(*) > 2
           ) AS grupos;
    IF problema IS NOT NULL THEN
        RAISE NOTICE
            'prefixo: grupos com mais de dois candidatos ficam para revisao manual:%',
            E'\n' || problema;
    END IF;

    -- A fusao automatica so vale para o caso comprovado da importacao: o telefone malformado e o
    -- lead sem mensagens e sem telefone_provedor; o gêmeo tem conversa e e o sobrevivente. Qualquer
    -- outro desenho fica intacto para nao apagar historico ou dono por inferencia.
    FOR par IN
        WITH candidatos AS (
            SELECT l.id,
                   l.nome,
                   l.telefone,
                   l.telefone_provedor,
                   app_telefone_canonico(l.telefone, ddi_padrao) AS canonico,
                   (SELECT count(*)
                      FROM mensagem m
                      JOIN atendimento a ON a.id = m.atendimento_id
                     WHERE a.lead_id = l.id) AS mensagens,
                   EXISTS (SELECT 1 FROM atendimento a WHERE a.lead_id = l.id) AS tem_conversa
              FROM lead l
             WHERE l.telefone IS NOT NULL
        ), pares AS (
            SELECT bad.id AS perdedor,
                   bad.nome AS nome_perdedor,
                   bad.telefone AS telefone_perdedor,
                   bad.canonico,
                   survivor.id AS sobrevivente,
                   survivor.nome AS nome_sobrevivente,
                   survivor.telefone AS telefone_sobrevivente
              FROM candidatos bad
              JOIN candidatos survivor
                ON survivor.id <> bad.id
               AND survivor.canonico = bad.canonico
             WHERE bad.telefone !~ '^55[1-9][0-9]{9,10}$'
               AND bad.canonico ~ '^55[1-9][0-9]{9,10}$'
               AND (bad.telefone_provedor IS NULL OR btrim(bad.telefone_provedor) = '')
               AND bad.mensagens = 0
               AND survivor.tem_conversa
        )
        SELECT p.*
          FROM pares p
         WHERE (SELECT count(*) FROM candidatos c WHERE c.canonico = p.canonico) = 2
         ORDER BY p.canonico, p.perdedor
    LOOP
        -- A mesma protecao da V50: uma metrica duplicada nao pode ser escolhida silenciosamente.
        IF EXISTS (
            SELECT 1
              FROM campanha_mensagem_metrica perdida
              JOIN campanha_mensagem_metrica mantida
                ON mantida.lead_id = par.sobrevivente
               AND mantida.campanha_mensagem_id = perdida.campanha_mensagem_id
             WHERE perdida.lead_id = par.perdedor) THEN
            RAISE EXCEPTION
                'Metrica de campanha nos dois lados do par % (leads % e %); fusao manual obrigatoria.',
                par.canonico, par.sobrevivente, par.perdedor;
        END IF;

        INSERT INTO lead_tag (lead_id, tag_id)
        SELECT par.sobrevivente, tag_id FROM lead_tag WHERE lead_id = par.perdedor
        ON CONFLICT DO NOTHING;
        DELETE FROM lead_tag WHERE lead_id = par.perdedor;

        UPDATE lembrete            SET lead_id = par.sobrevivente WHERE lead_id = par.perdedor;
        UPDATE mensagem_programada SET lead_id = par.sobrevivente WHERE lead_id = par.perdedor;
        UPDATE campanha_mensagem_metrica
           SET lead_id = par.sobrevivente WHERE lead_id = par.perdedor;
        UPDATE mensagem_envio_idempotencia
           SET lead_id = par.sobrevivente WHERE lead_id = par.perdedor;
        UPDATE evento_timeline     SET lead_id = par.sobrevivente WHERE lead_id = par.perdedor;
        UPDATE atendimento         SET lead_id = par.sobrevivente WHERE lead_id = par.perdedor;
        UPDATE audit_log            SET lead_id = par.sobrevivente WHERE lead_id = par.perdedor;

        -- Se um registro importado trouxe um atendimento vazio, preserva somente um atendimento
        -- aberto por lead. O historico permanece, e participantes dos extras sao encerrados como na
        -- maquina de fusao da V50.
        FOR extra IN
            SELECT ranqueados.id, ranqueados.atendente_id, ranqueados.mensagens
              FROM (
                    SELECT a.id,
                           a.atendente_id,
                           count(m.id) AS mensagens,
                           row_number() OVER (
                               ORDER BY count(m.id) DESC, a.iniciado_em ASC, a.id ASC) AS posicao
                      FROM atendimento a
                      LEFT JOIN mensagem m ON m.atendimento_id = a.id
                     WHERE a.lead_id = par.sobrevivente
                       AND a.status <> 'FINALIZADO'
                     GROUP BY a.id
                   ) ranqueados
             WHERE ranqueados.posicao > 1
        LOOP
            UPDATE atendimento
               SET status = 'FINALIZADO',
                   finalizado_em = now()
             WHERE id = extra.id;
            UPDATE atendimento_participante
               SET saiu_em = now()
             WHERE atendimento_id = extra.id
               AND saiu_em IS NULL;
            atendimentos_finalizados := atendimentos_finalizados + 1;
        END LOOP;

        UPDATE lead sobrevivente
           SET foto_url = COALESCE(NULLIF(sobrevivente.foto_url, ''), NULLIF(perdedor.foto_url, '')),
               email = COALESCE(NULLIF(sobrevivente.email, ''), NULLIF(perdedor.email, '')),
               empresa = COALESCE(NULLIF(sobrevivente.empresa, ''), NULLIF(perdedor.empresa, '')),
               cpf = COALESCE(NULLIF(sobrevivente.cpf, ''), NULLIF(perdedor.cpf, '')),
               localizacao =
                   COALESCE(NULLIF(sobrevivente.localizacao, ''), NULLIF(perdedor.localizacao, '')),
               codigo = COALESCE(NULLIF(sobrevivente.codigo, ''), NULLIF(perdedor.codigo, '')),
               canal_origem_id = COALESCE(sobrevivente.canal_origem_id, perdedor.canal_origem_id),
               etapa_atendimento_id =
                   COALESCE(sobrevivente.etapa_atendimento_id, perdedor.etapa_atendimento_id),
               atendente_responsavel_id =
                   COALESCE(sobrevivente.atendente_responsavel_id, perdedor.atendente_responsavel_id),
               notas = COALESCE(NULLIF(sobrevivente.notas, ''), NULLIF(perdedor.notas, '')),
               resumo_ia = COALESCE(NULLIF(sobrevivente.resumo_ia, ''), NULLIF(perdedor.resumo_ia, '')),
               dados_customizados =
                   COALESCE(perdedor.dados_customizados, '{}'::jsonb)
                   || COALESCE(sobrevivente.dados_customizados, '{}'::jsonb),
               foto_referencia = COALESCE(sobrevivente.foto_referencia, perdedor.foto_referencia),
               foto_hash = CASE
                               WHEN sobrevivente.foto_referencia IS NULL
                                   THEN COALESCE(sobrevivente.foto_hash, perdedor.foto_hash)
                               ELSE sobrevivente.foto_hash
                           END,
               foto_atualizada_em = GREATEST(
                   sobrevivente.foto_atualizada_em, perdedor.foto_atualizada_em),
               num_atendimentos = sobrevivente.num_atendimentos + perdedor.num_atendimentos,
               num_mensagens = sobrevivente.num_mensagens + perdedor.num_mensagens,
               ultima_interacao_em =
                   GREATEST(sobrevivente.ultima_interacao_em, perdedor.ultima_interacao_em),
               ultima_mensagem_do_lead_em = GREATEST(
                   sobrevivente.ultima_mensagem_do_lead_em, perdedor.ultima_mensagem_do_lead_em),
               resumo_ia_atualizado_em = GREATEST(
                   sobrevivente.resumo_ia_atualizado_em, perdedor.resumo_ia_atualizado_em),
               preenchimento_automatico_avaliado_em = GREATEST(
                   sobrevivente.preenchimento_automatico_avaliado_em,
                   perdedor.preenchimento_automatico_avaliado_em)
          FROM lead perdedor
         WHERE sobrevivente.id = par.sobrevivente
           AND perdedor.id = par.perdedor;

        DELETE FROM lead WHERE id = par.perdedor;
        fundidos := fundidos + 1;
        RAISE NOTICE
            'prefixo: fusao % | sobrevivente % (nome %) | perdedor % (nome %, telefone %)',
            par.canonico,
            par.sobrevivente,
            par.nome_sobrevivente,
            par.perdedor,
            par.nome_perdedor,
            par.telefone_perdedor;
    END LOOP;

    -- Atualiza somente candidatos confiaveis sem outro lead com a mesma chave. O WHERE torna o
    -- passo idempotente e deixa pares nao comprovados para a lista manual abaixo.
    FOR linha IN
        SELECT l.id,
               l.nome,
               l.telefone,
               app_telefone_canonico(l.telefone, ddi_padrao) AS canonico
          FROM lead l
         WHERE l.telefone IS NOT NULL
           AND l.telefone !~ '^55[1-9][0-9]{9,10}$'
           AND app_telefone_canonico(l.telefone, ddi_padrao) ~ '^55[1-9][0-9]{9,10}$'
         ORDER BY l.id
    LOOP
        IF NOT EXISTS (
            SELECT 1
              FROM lead outro
             WHERE outro.id <> linha.id
               AND app_telefone_canonico(outro.telefone, ddi_padrao) = linha.canonico) THEN
            UPDATE lead SET telefone = linha.canonico WHERE id = linha.id;
            normalizados := normalizados + 1;
        END IF;
    END LOOP;

    SELECT count(*),
           string_agg(
               format('%s | %s | %s -> %s', id, nome, telefone,
                      app_telefone_canonico(telefone, ddi_padrao)),
               E'\n' ORDER BY id)
      INTO deixados, problema
      FROM lead l
     WHERE l.telefone IS NOT NULL
       AND l.telefone !~ '^55[1-9][0-9]{9,10}$';

    IF deixados > 0 THEN
        RAISE NOTICE
            'prefixo: % telefone(s) deixado(s) para revisao manual:%',
            deixados,
            E'\n' || problema;
    END IF;

    RAISE NOTICE
        'prefixo de discagem: % leads normalizados, % pares fundidos, % atendimentos extras '
        'finalizados, % deixados para revisao',
        normalizados, fundidos, atendimentos_finalizados, deixados;
END $$;
