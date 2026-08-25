package com.synapse.crm.equipe.application.usuario;

/** Valida e reprocessa uma imagem sem transportar o formato original para o storage. */
public interface ProcessadorDeAvatar {

    Resultado processar(byte[] original);

    record Resultado(byte[] conteudo, String mimetype) {}
}
