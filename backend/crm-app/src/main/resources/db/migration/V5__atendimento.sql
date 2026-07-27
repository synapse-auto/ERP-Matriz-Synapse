-- =========================================================
-- Modulo crm-atendimento: atendimento e mensagem.
-- Maior volume de escrita do sistema e o caminho critico do produto.
-- =========================================================

CREATE TABLE atendimento (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    lead_id             UUID NOT NULL REFERENCES lead(id),
    canal_id            UUID REFERENCES canal(id),
    -- Declarada aqui, e nao por ALTER TABLE depois como no documento:
    -- canal_credencial ja existe desde a V3. O historico aponta para a
    -- credencial vigente na epoca, para a troca de numero nao corromper nada.
    canal_credencial_id UUID REFERENCES canal_credencial(id),
    atendente_id        UUID REFERENCES usuario(id),
    status              status_atendimento NOT NULL DEFAULT 'EM_IA',
    iniciado_em         TIMESTAMPTZ NOT NULL DEFAULT now(),
    finalizado_em       TIMESTAMPTZ
);

-- FKs que ficaram pendentes nas migrations anteriores por ordem de criacao.
ALTER TABLE avaliacao
    ADD CONSTRAINT fk_avaliacao_atendimento
    FOREIGN KEY (atendimento_id) REFERENCES atendimento(id) ON DELETE CASCADE;

ALTER TABLE evento_timeline
    ADD CONSTRAINT fk_evento_timeline_atendimento
    FOREIGN KEY (atendimento_id) REFERENCES atendimento(id) ON DELETE SET NULL;

-- =========================================================
-- mensagem: particionada por mes em enviado_em.
--
-- Particionar mantem os indices da particao corrente pequenos e permite
-- arquivar meses antigos sem lock na tabela ativa (RNF-CRM-01).
--
-- A chave de particao precisa fazer parte da PK, dai PRIMARY KEY (id, enviado_em).
-- =========================================================
CREATE TABLE mensagem (
    id              UUID NOT NULL DEFAULT gen_random_uuid(),
    atendimento_id  UUID NOT NULL REFERENCES atendimento(id),
    remetente_tipo  remetente_tipo NOT NULL,
    remetente_id    UUID,
    tipo            tipo_mensagem NOT NULL,
    conteudo        TEXT,
    midia_url       TEXT,
    midia_metadados JSONB,  -- nome do arquivo, mimetype, tamanho, legenda (RF-CRM-68)
    status_entrega  status_entrega NOT NULL DEFAULT 'ENVIADO',
    enviado_em      TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (id, enviado_em)
) PARTITION BY RANGE (enviado_em);

-- ---------------------------------------------------------
-- Rede de seguranca: particao DEFAULT.
--
-- Um INSERT numa faixa sem particao falha, e falhar ali significa parar de
-- enviar e receber mensagem. Comparando os modos de falha no cenario em que
-- todas as salvaguardas falham juntas:
--
--   sem DEFAULT  -> o INSERT falha e a mensagem do cliente se perde. Irrecuperavel.
--   com DEFAULT  -> a linha cai aqui e o atendimento segue. Divida recuperavel.
--
-- A regra de precedencia do produto decide entre os dois. O cenario nao e
-- hipotetico: basta o job mensal falhar em silencio por alguns meses sem que a
-- aplicacao reinicie, porque a verificacao de boot so protege quem reinicia.
--
-- O custo e real e conhecido: enquanto houver linha aqui na faixa de um mes,
-- criar a particao daquele mes falha (o Postgres teria de mover as linhas).
-- Por isso ela nunca pode ser silenciosa — um job diario alerta se houver
-- qualquer linha. Rede de seguranca sem alarme some do radar ate a limpeza
-- ficar cara.
-- ---------------------------------------------------------
CREATE TABLE mensagem_default PARTITION OF mensagem DEFAULT;

COMMENT ON TABLE mensagem_default IS
    'ANOMALIA, nao estado normal. Toda linha aqui chegou numa faixa sem particao mensal: '
    'o job de particionamento falhou ou a janela fechou. Linhas aqui impedem criar a particao '
    'do mes correspondente, entao devem ser drenadas (criar a particao do mes e mover as linhas) '
    'assim que o alerta disparar. Contagem esperada em operacao normal: zero.';

CREATE OR REPLACE FUNCTION mensagens_na_particao_default()
RETURNS BIGINT
LANGUAGE sql
STABLE
AS $$
    SELECT count(*) FROM mensagem_default;
$$;

COMMENT ON FUNCTION mensagens_na_particao_default() IS
    'Quantas linhas cairam na rede de seguranca. Qualquer valor acima de zero e alerta.';

-- ---------------------------------------------------------
-- Criacao de particoes.
--
-- A DEFAULT acima e ultimo recurso, nao substituto: a criacao continua
-- antecipada (varios meses a frente), idempotente, e conferida na
-- inicializacao da aplicacao.
-- ---------------------------------------------------------
CREATE OR REPLACE FUNCTION criar_particao_mensagem(p_mes DATE)
RETURNS TEXT
LANGUAGE plpgsql
AS $$
DECLARE
    v_inicio DATE := date_trunc('month', p_mes)::date;
    v_fim    DATE := (date_trunc('month', p_mes) + INTERVAL '1 month')::date;
    v_nome   TEXT := 'mensagem_' || to_char(v_inicio, 'YYYY_MM');
BEGIN
    IF to_regclass('public.' || quote_ident(v_nome)) IS NOT NULL THEN
        RETURN v_nome;
    END IF;

    EXECUTE format(
        'CREATE TABLE %I PARTITION OF mensagem FOR VALUES FROM (%L) TO (%L)',
        v_nome, v_inicio, v_fim);
    RETURN v_nome;
EXCEPTION
    -- Duas instancias podem rodar o job na mesma janela; a perdedora apenas segue.
    WHEN duplicate_table THEN
        RETURN v_nome;
    -- A particao DEFAULT ja tem linhas dessa faixa. O Postgres se recusa a criar
    -- a particao porque teria de mover linhas. Traduzimos o erro cru para algo
    -- que diga o que fazer: drenar a DEFAULT primeiro.
    WHEN check_violation THEN
        RAISE EXCEPTION
            'Nao foi possivel criar a particao % porque mensagem_default ja contem linhas '
            'dessa faixa. Drene a DEFAULT antes: mova as linhas do mes para uma tabela '
            'temporaria, crie a particao e devolva as linhas. Detalhe do banco: %',
            v_nome, SQLERRM
            USING ERRCODE = 'check_violation';
END;
$$;

COMMENT ON FUNCTION criar_particao_mensagem(DATE) IS
    'Cria a particao mensal de mensagem que cobre p_mes. Idempotente.';

CREATE OR REPLACE FUNCTION garantir_particoes_mensagem(p_meses_a_frente INT DEFAULT 3)
RETURNS INT
LANGUAGE plpgsql
AS $$
DECLARE
    v_total INT := 0;
BEGIN
    FOR i IN 0..p_meses_a_frente LOOP
        PERFORM criar_particao_mensagem(
            (date_trunc('month', now()) + make_interval(months => i))::date);
        v_total := v_total + 1;
    END LOOP;
    RETURN v_total;
END;
$$;

COMMENT ON FUNCTION garantir_particoes_mensagem(INT) IS
    'Garante a janela inteira do mes corrente ate p_meses_a_frente, nao so o proximo mes.';

CREATE OR REPLACE FUNCTION particoes_mensagem_faltantes(p_meses_a_frente INT DEFAULT 1)
RETURNS TABLE (mes DATE, nome_esperado TEXT)
LANGUAGE sql
STABLE
AS $$
    SELECT m::date,
           'mensagem_' || to_char(m, 'YYYY_MM')
      FROM generate_series(
               date_trunc('month', now()),
               date_trunc('month', now()) + make_interval(months => p_meses_a_frente),
               INTERVAL '1 month') AS m
     WHERE to_regclass('public.mensagem_' || to_char(m, 'YYYY_MM')) IS NULL
     ORDER BY m;
$$;

COMMENT ON FUNCTION particoes_mensagem_faltantes(INT) IS
    'Meses da janela que ainda nao tem particao. Vazio = seguro para escrever.';

-- Janela inicial: mes corrente + 3 meses a frente.
SELECT garantir_particoes_mensagem(3);
