-- Executar somente no banco da instancia explicitamente escolhida pelo operador (hoje: Femina).
-- Nao e migration Flyway: a Base PAI nao habilita o card de informacoes do chatbot em outros filhos.
-- Ver docs/69-informacoes-do-chatbot-no-historico.md.
--
-- Pre-requisito: a versao com a V101 ja implantada nesta instancia (a V101 cria a linha da flag
-- desligada). O script recusa em vez de criar a linha por conta propria, para nao mascarar um
-- deploy que ainda nao aconteceu. Repetir o script e neutro.
--
-- Desfazer (nao apaga os cards ja gravados; so para de aceitar e de exibir):
--   UPDATE feature_flag SET habilitado = FALSE WHERE chave = 'informacoes_chatbot_historico';
DO $habilitar_informacoes_do_chatbot$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM feature_flag WHERE chave = 'informacoes_chatbot_historico') THEN
        RAISE EXCEPTION
            'flag informacoes_chatbot_historico ausente: implante a versao com a V101 antes de habilitar; nenhuma alteracao aplicada';
    END IF;

    UPDATE feature_flag
       SET habilitado = TRUE
     WHERE chave = 'informacoes_chatbot_historico';
END;
$habilitar_informacoes_do_chatbot$;
