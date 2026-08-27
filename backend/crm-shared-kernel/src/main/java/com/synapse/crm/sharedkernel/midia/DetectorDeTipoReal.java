package com.synapse.crm.sharedkernel.midia;

/**
 * Mimetype real, por assinatura de bytes (magic bytes).
 */
public interface DetectorDeTipoReal {
    /** O mimetype real do conteudo, ex. {@code image/jpeg}. */
    String detectar(byte[] conteudo);
}
