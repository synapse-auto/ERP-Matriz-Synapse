package com.synapse.crm.equipe.application.chat;

import java.util.Optional;

/**
 * Porta do storage das fotos de grupo (prefixo proprio no bucket de avatares). A entrega continua
 * passando pela aplicacao: o navegador nunca recebe URL do bucket nem credencial.
 */
public interface ArmazenamentoDeFotoDeGrupo {

    String salvar(byte[] conteudo, String mimetype);

    Optional<Arquivo> buscar(String referencia);

    void remover(String referencia);

    record Arquivo(byte[] conteudo, String mimetype) {}
}
