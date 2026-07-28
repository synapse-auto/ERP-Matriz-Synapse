-- =========================================================
-- Duas filas duraveis, uma em cada sentido.
--
-- outbox_evento existe desde a V9 e nunca teve escritor: ate a E04 o envio era
-- reacao AFTER_COMMIT, que nao tem durabilidade nem retry. Se o processo morre
-- entre o commit e o listener, a reacao nao acontece e ninguem fica sabendo.
-- Para a timeline isso e aceitavel; para "enviar ao WhatsApp" e uma mensagem
-- que o cliente nunca recebe com o CRM achando que enviou.
--
-- webhook_entrada e a mesma ideia no sentido oposto. O provedor exige resposta
-- rapida e reentrega quando demora — entao o webhook grava e devolve 200, e o
-- processamento acontece fora do ciclo de request.
-- =========================================================

-- --- Saida -----------------------------------------------------------------

ALTER TABLE outbox_evento
    ADD COLUMN proxima_tentativa_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN ultimo_erro          TEXT,
    ADD COLUMN esgotado_em          TIMESTAMPTZ;

COMMENT ON COLUMN outbox_evento.proxima_tentativa_em IS
    'Backoff exponencial: o publisher so pega linhas cuja hora ja chegou. Sem isto, uma linha '
    'que falha em sequencia seria retentada em loop apertado e viraria ataque ao proprio provedor.';

COMMENT ON COLUMN outbox_evento.esgotado_em IS
    'Estourou o limite de tentativas. A linha NAO e apagada nem marcada como publicada: fica aqui '
    'para inspecao, e o job de alerta grita enquanto houver alguma. Descartar em silencio uma '
    'mensagem que o atendente escreveu e a pior falha possivel deste modulo.';

-- O indice da V9 (idx_outbox_pendente) ordena por criado_em e nao conhece
-- backoff nem esgotamento. O publisher varre por outra chave, entao ganha o
-- proprio indice parcial — que so carrega o que ainda esta em jogo.
CREATE INDEX idx_outbox_a_publicar
    ON outbox_evento (proxima_tentativa_em)
 WHERE publicado_em IS NULL AND esgotado_em IS NULL;

-- Alerta: quantas linhas desistiram. Espera-se zero em operacao normal.
CREATE OR REPLACE FUNCTION outbox_esgotadas()
RETURNS BIGINT
LANGUAGE sql
STABLE
AS $$
    SELECT count(*) FROM outbox_evento WHERE esgotado_em IS NOT NULL;
$$;

COMMENT ON FUNCTION outbox_esgotadas() IS
    'Mensagens que o CRM nao conseguiu entregar ao provedor apos todas as tentativas. '
    'Qualquer valor acima de zero e alerta operacional, nao estatistica.';

-- --- Entrada ---------------------------------------------------------------

-- A chave primaria E a idempotencia. Provedores reentregam o mesmo evento —
-- por timeout, por retry proprio, por reprocessamento de fila deles. Sem esta
-- restricao, cada reentrega viraria uma mensagem duplicada na conversa do
-- atendente.
--
-- Por que uma tabela propria e nao um indice unico em `mensagem`: `mensagem` e
-- particionada por enviado_em, e no PostgreSQL um indice unico sobre tabela
-- particionada precisa incluir a chave de particao. UNIQUE (id_externo,
-- enviado_em) nao impediria a mesma mensagem entrar duas vezes com timestamps
-- diferentes, que e exatamente o caso da reentrega.
CREATE TABLE webhook_entrada (
    id_externo    VARCHAR(200) PRIMARY KEY,
    provedor      VARCHAR(40) NOT NULL,
    payload       JSONB NOT NULL,
    recebido_em   TIMESTAMPTZ NOT NULL DEFAULT now(),
    processado_em TIMESTAMPTZ,
    tentativas    SMALLINT NOT NULL DEFAULT 0,
    ultimo_erro   TEXT,
    esgotado_em   TIMESTAMPTZ
);

COMMENT ON TABLE webhook_entrada IS
    'Payload cru do provedor, como chegou. Guardar o cru e o que permite reprocessar depois de '
    'corrigir um bug de traducao — o ACL descarta informacao de proposito, e o que ele descartou '
    'nao volta.';

COMMENT ON COLUMN webhook_entrada.id_externo IS
    'Id da mensagem no provedor. E a chave de idempotencia: reentrega colide e nao vira duplicata.';

CREATE INDEX idx_webhook_a_processar
    ON webhook_entrada (recebido_em)
 WHERE processado_em IS NULL AND esgotado_em IS NULL;
