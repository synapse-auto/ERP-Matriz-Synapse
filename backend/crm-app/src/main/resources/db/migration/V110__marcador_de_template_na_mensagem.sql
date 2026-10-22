-- E233: marcador de "esta mensagem foi um envio de template".
--
-- O historico grava o corpo renderizado do template como TEXTO, sem prefixo tecnico (o prefixo
-- "[template nome]" so existe quando a interface nao manda o corpo). Entao nada na linha dizia que
-- a mensagem foi um template, e o frontend nao tinha como reagir a "template enviado, aguardando
-- resposta" sem adivinhar pelo texto ou pelo horario. A coluna passa a dizer, e quem escreve decide
-- a partir do tipo do conteudo de envio, nunca do texto.
--
-- Aditiva e barata: ADD COLUMN com DEFAULT constante no PG 11+ e so metadado, mesmo na tabela
-- particionada — nenhuma particao e reescrita (EnvioDeTemplateMigrationIT confere o relfilenode). Nao
-- ha indice: a coluna e lida junto com a linha, nunca filtrada. Nao ha backfill: as linhas antigas
-- valem FALSE; o frontend so reconhece templates enviados depois deste deploy. A imagem anterior
-- segue funcionando porque o INSERT e o SELECT dela nao citam a coluna.

ALTER TABLE mensagem ADD COLUMN envio_de_template BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN mensagem.envio_de_template IS
    'TRUE quando a mensagem saiu como template aprovado (E233). Vem do tipo do conteudo de envio, nunca do texto. Linhas anteriores a V110 valem FALSE.';
