-- E209: a outbox de envio nunca reenvia um despacho cujo resultado nao foi registrado.
--
-- Risco que fecha (docs/22): o worker chama o provedor fora de transacao e so depois grava o resultado.
-- Se o processo morre (todo deploy mata o container) ou a transacao de resultado falha DEPOIS de a
-- Meta aceitar, a linha volta a ser elegivel quando o lease expira e a mensagem sai de novo. A API da
-- Meta nao aceita chave de idempotencia do cliente, entao a garantia tem de ser nossa.
--
-- despachado_em: gravado numa transacao curta IMEDIATAMENTE antes da chamada ao provedor (nao na
-- reserva: reservar um lote e morrer antes de chamar nao pode virar "ambiguo"). Reagendar apos recusa
-- conhecida limpa a marca; aceitar mantem como evidencia.
-- despacho_ambiguo_em: lease expirou com despachado_em preenchido e sem publicado_em. A linha tambem
-- recebe esgotado_em, o que a tira de toda consulta de reserva e a conta no alarme ja existente.
--
-- Colunas anuladas sem default: alteracao so de catalogo, sem reescrever a tabela.

ALTER TABLE outbox_evento
    ADD COLUMN despachado_em        TIMESTAMPTZ,
    ADD COLUMN despacho_ambiguo_em  TIMESTAMPTZ;

COMMENT ON COLUMN outbox_evento.despachado_em IS
    'Marca gravada antes de chamar o provedor. Preenchida e sem publicado_em apos o lease = resultado desconhecido: nao reenviar.';
COMMENT ON COLUMN outbox_evento.despacho_ambiguo_em IS
    'Quando a conciliacao viu um despacho sem resultado. A linha fica esgotada, para conferencia manual; nunca e reenviada sozinha.';
