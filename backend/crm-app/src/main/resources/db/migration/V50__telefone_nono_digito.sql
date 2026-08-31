-- =========================================================
-- E111: o nono digito parte o cliente em dois cadastros.
--
-- A Meta entrega o wa_id de boa parte dos numeros brasileiros SEM o nono digito. A mensagem que
-- chega casa com um lead de 12 digitos; o lead cadastrado a mao ou importado usa o formato de
-- discagem, com 13. Para o indice unico sao dois clientes, e o historico fica partido: 147 mensagens
-- de um lado, duas do outro, template enviado do cadastro sem janela aberta.
--
-- Esta migration e a metade de dados de uma mudanca que so funciona inteira. Normalizar apenas no
-- codigo faria visivelPorTelefone("5561981536371") parar de achar "556181536371" e o cliente ganharia
-- um TERCEIRO cadastro. Por isso a fusao roda no start, antes de a aplicacao atender, no mesmo deploy
-- que leva a regra em Java.
--
-- Ordem obrigatoria (inverter estoura no ux_lead_telefone):
--   1. fundir os pares;
--   2. so entao normalizar o telefone de todos os leads restantes.
--
-- Nada aqui adivinha. Todo caso fora da regra levanta excecao e derruba o deploy: a aplicacao nao
-- subir e recuperavel, fundir o cliente errado nao e.
-- =========================================================

-- ---------------------------------------------------------
-- Contexto de servico.
--
-- lead, atendimento, lembrete e mensagem_programada tem FORCE ROW LEVEL SECURITY desde a V12. FORCE
-- alcanca ate o dono da tabela — que e justamente o usuario que roda as migrations. Sem contexto,
-- app_papel() e NULL, a politica nega tudo e uma migration de dados vira um no-op silencioso: nenhum
-- UPDATE, nenhum erro, nenhum aviso. Hoje a instancia escapa porque o usuario do Flyway e
-- superusuario do container, e superusuario ignora RLS; num Postgres gerenciado, onde o usuario da
-- aplicacao e apenas dono da tabela, nao escaparia.
--
-- SERVICO e o papel que app_e_servico() ja reconhece para jobs e consumidores de fila. set_config
-- com o terceiro argumento TRUE e local a transacao da migration, como o SET LOCAL do
-- AplicadorDeContextoRls: nao vaza para a proxima conexao do pool.
-- ---------------------------------------------------------
SELECT set_config('app.papel', 'SERVICO', TRUE);

DO $$
BEGIN
    IF NOT app_enxerga_todos_os_leads() THEN
        RAISE EXCEPTION
            'contexto de servico nao aplicado: esta migration enxergaria zero leads e fundiria nada';
    END IF;
END $$;

-- >>> regra-do-nono-digito >>>
-- Definicao unica da regra em SQL. O bloco entre estes marcadores e copiado literalmente em
-- docker/provisionamento/simular-fusao-nono-digito.sql, e RegraDoNonoDigitoCompartilhadaTest
-- reprova o build se os dois textos divergirem em um caractere.
--
-- A contraparte Java e TelefoneCanonico; quem prova que as duas concordam e
-- TelefoneCanonicoParidadeIT, que roda a mesma tabela de casos nas duas implementacoes.

-- Somente digitos, mais o DDI da instancia quando o numero veio local. Espelha as duas primeiras
-- etapas de TelefoneCanonico.normalizar. NULL significa "sem telefone ou curto demais para ser um":
-- onde o Java levanta TelefoneInvalidoException, aqui devolve NULL.
CREATE OR REPLACE FUNCTION app_telefone_com_ddi(entrada TEXT, ddi_padrao TEXT)
RETURNS TEXT LANGUAGE sql IMMUTABLE AS $$
    SELECT CASE
               WHEN digitos IS NULL THEN NULL
               WHEN length(digitos) < 10 THEN NULL
               WHEN length(digitos) IN (10, 11) THEN ddi_padrao || digitos
               ELSE digitos
           END
      FROM (SELECT regexp_replace(entrada, '[^0-9]', '', 'g') AS digitos) AS limpo;
$$;

-- Assinante de oito digitos comecando em 6, 7, 8 ou 9 e celular que perdeu o nono digito: ganha o 9.
-- Comecando em 2, 3, 4 ou 5 e fixo e fica como esta. Nove digitos ja e canonico. Qualquer outro
-- tamanho, e qualquer DDI que nao seja o do Brasil, passa intacto.
--
-- A inferencia e segura porque no Brasil fixo nunca comeca em 6, 7, 8 ou 9, e desde 2016 todo
-- celular tem nove digitos comecando em 9. A regra e do pais, nao do cliente: ela olha o DDI do
-- proprio numero, entao um contato portugues na base de um filho brasileiro nao e tocado.
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

-- Assinante de oito digitos comecando em 0 ou 1 nao existe no plano de numeracao brasileiro. A
-- regra nao diz o que fazer com ele e esta migration nao inventa: quem chama isto aborta.
CREATE OR REPLACE FUNCTION app_telefone_fora_da_regra(com_ddi TEXT)
RETURNS BOOLEAN LANGUAGE sql IMMUTABLE AS $$
    SELECT com_ddi IS NOT NULL
       AND length(com_ddi) = 12
       AND left(com_ddi, 2) = '55'
       AND substr(com_ddi, 5, 1) < '2';
$$;
-- <<< regra-do-nono-digito <<<

COMMENT ON FUNCTION app_telefone_canonico(TEXT, TEXT) IS
    'Telefone canonico do CRM, incluindo o nono digito de celular brasileiro. '
    'Espelha TelefoneCanonico do dominio; TelefoneCanonicoParidadeIT reprova se so um mudar.';

DO $$
DECLARE
    -- Mesmo valor que o normalizador em runtime recebe por synapse.telefone.ddi-padrao.
    ddi_padrao     TEXT := '${telefone_ddi_padrao}';
    tabelas_com_fk TEXT[] := ARRAY[
        'atendimento',
        'campanha_mensagem_metrica',
        'evento_timeline',
        'lead_tag',
        'lembrete',
        'mensagem_programada'];
    problema       TEXT;
    par            RECORD;
    fundidos       INT := 0;
    normalizados   INT := 0;
BEGIN
    IF ddi_padrao !~ '^[0-9]{1,3}$' THEN
        RAISE EXCEPTION 'DDI padrao deve conter de um a tres digitos; recebido: %', ddi_padrao;
    END IF;

    -- --- Bloco 2: o que aponta para lead, levantado do catalogo e nao de memoria -------------
    -- Uma FK nova que ninguem previu deixaria linha orfa ou estouraria no meio do deploy. Uma FK
    -- prevista que sumiu significa que este script esta tratando uma tabela que nao existe mais.
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

    SELECT string_agg(esperada, ', ' ORDER BY esperada)
      INTO problema
      FROM unnest(tabelas_com_fk) AS esperada
     WHERE NOT EXISTS (
               SELECT 1 FROM pg_constraint
                WHERE confrelid = 'lead'::regclass
                  AND contype = 'f'
                  AND conrelid::regclass::text = esperada);
    IF problema IS NOT NULL THEN
        RAISE EXCEPTION 'FK para lead esperada e ausente: %. O schema mudou sob esta migration.',
            problema;
    END IF;

    -- --- Bloco 5: telefone fora dos formatos previstos ---------------------------------------
    SELECT string_agg(format('%s | %s | %s', id, nome, telefone), E'\n' ORDER BY id)
      INTO problema
      FROM lead
     WHERE telefone IS NOT NULL
       AND (telefone !~ '^[0-9]+$'
            OR app_telefone_com_ddi(telefone, ddi_padrao) IS NULL
            OR app_telefone_fora_da_regra(app_telefone_com_ddi(telefone, ddi_padrao)));
    IF problema IS NOT NULL THEN
        RAISE EXCEPTION
            'Telefone fora dos formatos previstos; correcao manual obrigatoria:%',
            E'\n' || problema;
    END IF;

    -- --- Bloco 5: mais de dois leads no mesmo final -------------------------------------------
    -- A regra de fusao decide entre DOIS cadastros. Tres nao e "o mesmo caso, so que maior": e
    -- historico em tres lugares e comissao de mais de um atendente.
    SELECT string_agg(format('%s -> [%s]', canonico, leads), E'\n' ORDER BY canonico)
      INTO problema
      FROM (
            SELECT app_telefone_canonico(telefone, ddi_padrao) AS canonico,
                   string_agg(format('%s | %s | %s', id, nome, telefone), '; ' ORDER BY id) AS leads
              FROM lead
             WHERE telefone IS NOT NULL
             GROUP BY 1
            HAVING count(*) > 2
           ) AS grupos;
    IF problema IS NOT NULL THEN
        RAISE EXCEPTION
            'Mais de dois leads no mesmo telefone canonico; reconciliacao manual obrigatoria:%',
            E'\n' || problema;
    END IF;

    -- --- Bloco 3: fusao par a par --------------------------------------------------------------
    -- Sobrevive quem tem a conversa: mais mensagens, empate mais atendimentos, empate o mais antigo.
    -- As contagens vem das tabelas, nao dos contadores desnormalizados de lead: se um contador tiver
    -- deriva, quem decide de que lado esta o historico e o historico.
    FOR par IN
        WITH canonicos AS (
            SELECT id,
                   nome,
                   telefone,
                   criado_em,
                   app_telefone_canonico(telefone, ddi_padrao) AS canonico
              FROM lead
             WHERE telefone IS NOT NULL
        ), duplicados AS (
            SELECT canonico FROM canonicos GROUP BY canonico HAVING count(*) = 2
        ), medidos AS (
            SELECT c.*,
                   (SELECT count(*) FROM atendimento a WHERE a.lead_id = c.id) AS atendimentos,
                   (SELECT count(*)
                      FROM mensagem m
                      JOIN atendimento a ON a.id = m.atendimento_id
                     WHERE a.lead_id = c.id) AS mensagens
              FROM canonicos c
              JOIN duplicados d ON d.canonico = c.canonico
        ), ordenados AS (
            SELECT m.*,
                   row_number() OVER (
                       PARTITION BY canonico
                       ORDER BY mensagens DESC, atendimentos DESC, criado_em ASC, id ASC) AS posicao
              FROM medidos m
        )
        SELECT s.canonico,
               s.id       AS sobrevivente,
               s.nome     AS nome_sobrevivente,
               s.telefone AS telefone_sobrevivente,
               p.id       AS perdedor,
               p.nome     AS nome_perdedor,
               p.telefone AS telefone_perdedor
          FROM ordenados s
          JOIN ordenados p ON p.canonico = s.canonico AND p.posicao = 2
         WHERE s.posicao = 1
         ORDER BY s.canonico
    LOOP
        -- Metrica de campanha e por (mensagem, lead). Duas linhas da mesma mensagem para os dois
        -- cadastros nao se somam sozinhas: somar, escolher ou descartar e decisao comercial.
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

        -- A PK de lead_tag e (lead_id, tag_id): a mesma tag nos dois cadastros colidiria.
        INSERT INTO lead_tag (lead_id, tag_id)
        SELECT par.sobrevivente, tag_id FROM lead_tag WHERE lead_id = par.perdedor
        ON CONFLICT DO NOTHING;
        DELETE FROM lead_tag WHERE lead_id = par.perdedor;

        UPDATE lembrete            SET lead_id = par.sobrevivente WHERE lead_id = par.perdedor;
        UPDATE mensagem_programada SET lead_id = par.sobrevivente WHERE lead_id = par.perdedor;
        UPDATE campanha_mensagem_metrica
           SET lead_id = par.sobrevivente WHERE lead_id = par.perdedor;

        -- evento_timeline e append-only na aplicacao (V4). Aqui a linha nao muda de conteudo, muda
        -- de dono: sem isto o ON DELETE CASCADE apagaria a timeline do perdedor junto com ele.
        UPDATE evento_timeline     SET lead_id = par.sobrevivente WHERE lead_id = par.perdedor;

        -- mensagem aponta para atendimento, nao para lead: mover o atendimento leva as mensagens.
        UPDATE atendimento         SET lead_id = par.sobrevivente WHERE lead_id = par.perdedor;

        -- audit_log nao tem FK para lead; lead_id ali e coluna de filtro. Reapontar mantem o
        -- historico do cliente alcancavel. entidade_id nao e tocado: ele registra em qual linha a
        -- acao aconteceu, e aquela linha existiu.
        UPDATE audit_log           SET lead_id = par.sobrevivente WHERE lead_id = par.perdedor;

        -- Campo vazio no sobrevivente pode ser preenchido pelo perdedor. Campo preenchido nos dois
        -- fica com o do sobrevivente e o valor do perdedor e descartado — nunca concatenado. O nome
        -- NAO e fundido: nome e escolha humana, e a operacao corrige pela tela.
        UPDATE lead sobrevivente
           SET email       = COALESCE(NULLIF(sobrevivente.email, ''), NULLIF(perdedor.email, '')),
               empresa     = COALESCE(NULLIF(sobrevivente.empresa, ''), NULLIF(perdedor.empresa, '')),
               cpf         = COALESCE(NULLIF(sobrevivente.cpf, ''), NULLIF(perdedor.cpf, '')),
               localizacao =
                   COALESCE(NULLIF(sobrevivente.localizacao, ''), NULLIF(perdedor.localizacao, '')),
               codigo      = COALESCE(NULLIF(sobrevivente.codigo, ''), NULLIF(perdedor.codigo, '')),
               -- Os contadores sao desnormalizados e acabaram de receber as linhas do perdedor.
               num_atendimentos = sobrevivente.num_atendimentos + perdedor.num_atendimentos,
               num_mensagens    = sobrevivente.num_mensagens + perdedor.num_mensagens,
               -- GREATEST ignora NULL no Postgres. A janela de 24h passa a valer para a conversa
               -- inteira, que e o que o cliente viveu.
               ultima_interacao_em =
                   GREATEST(sobrevivente.ultima_interacao_em, perdedor.ultima_interacao_em)
          FROM lead perdedor
         WHERE sobrevivente.id = par.sobrevivente
           AND perdedor.id = par.perdedor;

        DELETE FROM lead WHERE id = par.perdedor;
        fundidos := fundidos + 1;

        RAISE NOTICE
            'fusao % : sobrevivente % (nome %, telefone %) | perdedor apagado % (nome %, telefone %)',
            par.canonico,
            par.sobrevivente, par.nome_sobrevivente, par.telefone_sobrevivente,
            par.perdedor, par.nome_perdedor, par.telefone_perdedor;
    END LOOP;

    -- --- Bloco 5: colisao que sobrou depois da fusao -------------------------------------------
    -- Verificada ANTES do UPDATE: abortar com o telefone ainda intacto e melhor que abortar no meio.
    SELECT string_agg(format('%s -> [%s]', canonico, leads), E'\n' ORDER BY canonico)
      INTO problema
      FROM (
            SELECT app_telefone_canonico(telefone, ddi_padrao) AS canonico,
                   string_agg(format('%s | %s | %s', id, nome, telefone), '; ' ORDER BY id) AS leads
              FROM lead
             WHERE telefone IS NOT NULL
             GROUP BY 1
            HAVING count(*) > 1
           ) AS grupos;
    IF problema IS NOT NULL THEN
        RAISE EXCEPTION
            'Colisao de telefone que a fusao nao previu; reconciliacao manual obrigatoria:%',
            E'\n' || problema;
    END IF;

    -- --- Bloco 4, etapa 2: normalizar todos os leads restantes ---------------------------------
    UPDATE lead
       SET telefone = app_telefone_canonico(telefone, ddi_padrao)
     WHERE telefone IS NOT NULL
       AND telefone IS DISTINCT FROM app_telefone_canonico(telefone, ddi_padrao);
    GET DIAGNOSTICS normalizados = ROW_COUNT;

    RAISE NOTICE 'nono digito: % pares fundidos, % telefones normalizados', fundidos, normalizados;
END $$;
