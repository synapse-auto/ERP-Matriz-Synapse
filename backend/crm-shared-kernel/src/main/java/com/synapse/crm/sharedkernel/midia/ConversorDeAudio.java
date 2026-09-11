package com.synapse.crm.sharedkernel.midia;

import java.util.Objects;

/**
 * Porta para normalizar uma gravação de áudio antes de ela entrar no storage ou ser entregue ao
 * provedor.
 *
 * <p>O caso de uso decide quando a origem é uma gravação do composer. A implementação fica na
 * infraestrutura para que o domínio não conheça codecs, processos do sistema ou FFmpeg.
 */
public interface ConversorDeAudio {

    /** Converte os bytes para um contêiner OGG com codec Opus. */
    Resultado converterParaOggOpus(byte[] conteudo, String mimetype);

    /**
     * Converte os bytes para AAC em ADTS para compatibilidade com registros fragmentados legados.
     * Gravações novas do composer usam {@link #converterParaOggOpus(byte[], String)}.
     */
    default Resultado converterParaAacAdts(byte[] conteudo, String mimetype) {
        throw new UnsupportedOperationException("conversão AAC/ADTS não implementada");
    }

    record Resultado(byte[] conteudo, String mimetype) {
        public Resultado {
            Objects.requireNonNull(conteudo, "conteudo convertido");
            Objects.requireNonNull(mimetype, "mimetype convertido");
        }
    }
}
