package com.synapse.crm.sharedkernel.midia;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Mimetype real, por assinatura de bytes (magic bytes).
 */
public interface DetectorDeTipoReal {
    /** O mimetype real do conteudo, ex. {@code image/jpeg}. */
    String detectar(byte[] conteudo);

    /** Deteccao de spool sem ler documento inteiro. O adaptador real inspeciona containers. */
    default String detectar(Path arquivo) {
        try (var entrada = Files.newInputStream(arquivo)) {
            return detectar(entrada.readNBytes(1024 * 1024));
        } catch (IOException e) { throw new UncheckedIOException("falha ao detectar midia", e); }
    }
}
