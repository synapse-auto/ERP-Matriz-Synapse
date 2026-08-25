package com.synapse.crm.equipe.application.usuario;

import java.util.Optional;

/** Porta do bucket proprio de fotos de usuario; a entrega continua passando pela aplicacao. */
public interface ArmazenamentoDeAvatar {

    String salvar(byte[] conteudo, String mimetype);

    Optional<Arquivo> buscar(String referencia);

    void remover(String referencia);

    record Arquivo(byte[] conteudo, String mimetype) {}
}
