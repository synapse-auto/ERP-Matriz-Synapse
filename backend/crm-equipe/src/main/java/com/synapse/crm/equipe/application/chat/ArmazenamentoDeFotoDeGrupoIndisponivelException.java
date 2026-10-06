package com.synapse.crm.equipe.application.chat;

/** O storage nao gravou a imagem; o grupo continua com a foto anterior. */
public class ArmazenamentoDeFotoDeGrupoIndisponivelException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public ArmazenamentoDeFotoDeGrupoIndisponivelException(Throwable causa) {
        super("Armazenamento de imagens indisponivel. Tente novamente.", causa);
    }
}
