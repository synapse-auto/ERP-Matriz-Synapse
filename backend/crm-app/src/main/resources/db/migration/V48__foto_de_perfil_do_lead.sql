-- E97: foto de perfil do lead entregue pela integracao externa (n8n + UAZAPI).
-- A Meta nao entrega a foto do contato; quem varre e quem chama e a integracao.
-- O CRM so recebe, reprocessa e guarda — RN-CRM-07: o CRM configura a automacao,
-- nao a executa.
--
-- Como em usuario.foto_referencia (V40), o arquivo nunca fica no banco: a coluna
-- so aponta para o objeto ja reprocessado no bucket proprio.
--
-- lead.foto_url continua existindo e continua editavel na ficha (V4): ela pode
-- guardar URL externa legada. A precedencia esta na leitura — foto_referencia
-- ganha de foto_url —, nao numa migracao de dados que apagaria o que o atendente
-- digitou.
ALTER TABLE lead ADD COLUMN foto_referencia VARCHAR(255);
ALTER TABLE lead ADD COLUMN foto_hash CHAR(64);
ALTER TABLE lead ADD COLUMN foto_atualizada_em TIMESTAMPTZ;

COMMENT ON COLUMN lead.foto_referencia IS
    'Referencia opaca da foto reprocessada no bucket proprio de avatares (prefixo lead/).';

COMMENT ON COLUMN lead.foto_hash IS
    'SHA-256 em hexadecimal dos bytes ORIGINAIS recebidos da integracao. E o que torna o '
    'polling barato: reenvio com o mesmo hash responde INALTERADA sem tocar storage nem banco.';

COMMENT ON COLUMN lead.foto_atualizada_em IS
    'Instante em que o CRM gravou a foto atual. Nulo quando o lead nunca recebeu foto da integracao.';
