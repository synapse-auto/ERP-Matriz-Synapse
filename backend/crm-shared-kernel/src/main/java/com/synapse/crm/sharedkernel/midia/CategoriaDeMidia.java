package com.synapse.crm.sharedkernel.midia;

/**
 * Categoria de anexo para limites e validacao. {@code VIDEO} (E215) so e produzido pelo envio do
 * atendimento: {@link RegrasDeAnexoBase} nao mapeia nenhum mimetype de video, entao chat interno,
 * logo e foto continuam sem aceitar video.
 */
public enum CategoriaDeMidia {
    IMAGEM, AUDIO, DOCUMENTO, VIDEO
}
