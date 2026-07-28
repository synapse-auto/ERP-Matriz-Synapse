-- =========================================================
-- webhook_entrada.payload: JSONB -> TEXT.
--
-- JSONB normaliza ao gravar (reordena chaves, insere espaco apos ':'). O que
-- fica salvo deixa de ser, byte a byte, o que o provedor mandou. Para um log
-- de auditoria isso e uma mentira silenciosa: "isto foi o que recebemos" e
-- guarda algo diferente.
--
-- Duas consequencias praticas descobertas na E05/E06:
--   1. reprocessar um webhook nao permite reconferir a assinatura HMAC contra
--      o corpo salvo, porque o corpo salvo nao e mais o corpo assinado;
--   2. um parser ingenuo que case no payload cru falha ao reler da coluna.
--
-- O que se perde: os operadores JSONB (->, ->>, @>) para consultar dentro do
-- payload. Nao e perda real — esta tabela e log de entrada, nao fonte de
-- consulta; quem precisa dos campos usa o TradutorDeCanal, nao SQL ad hoc.
-- =========================================================

ALTER TABLE webhook_entrada ALTER COLUMN payload TYPE TEXT USING payload::text;
