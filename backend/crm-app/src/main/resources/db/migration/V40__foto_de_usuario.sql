-- E50: referencia opaca da foto de perfil no storage proprio de avatares.
-- O arquivo nunca fica no banco; a coluna so aponta para o objeto reprocessado.
ALTER TABLE usuario ADD COLUMN foto_referencia VARCHAR(255);

COMMENT ON COLUMN usuario.foto_referencia IS
    'Referencia opaca do avatar reprocessado no bucket proprio de fotos de usuario.';
