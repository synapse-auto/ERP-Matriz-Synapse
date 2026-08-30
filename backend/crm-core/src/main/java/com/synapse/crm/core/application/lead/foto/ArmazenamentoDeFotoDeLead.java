package com.synapse.crm.core.application.lead.foto;

import java.util.Optional;

/**
 * Porta do bucket de fotos de lead; a entrega continua passando pela aplicacao.
 *
 * <p>O browser nunca recebe URL do storage. A referencia devolvida por {@link #salvar} e opaca e
 * usa prefixo proprio ({@code lead/}), separado do prefixo {@code avatar/} das fotos de usuario:
 * {@link #buscar} e {@link #remover} filtram por ele justamente para os dois nao se confundirem.
 */
public interface ArmazenamentoDeFotoDeLead {

    String salvar(byte[] conteudo, String mimetype);

    Optional<Arquivo> buscar(String referencia);

    void remover(String referencia);

    record Arquivo(byte[] conteudo, String mimetype) {}
}
