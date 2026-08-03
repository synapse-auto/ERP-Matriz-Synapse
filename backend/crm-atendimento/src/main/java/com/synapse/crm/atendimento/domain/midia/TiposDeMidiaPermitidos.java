package com.synapse.crm.atendimento.domain.midia;

import java.util.Map;
import java.util.Optional;

import com.synapse.crm.atendimento.domain.mensagem.TipoMensagem;

/**
 * Allowlist de mimetype real (nao extensao, nao {@code Content-Type} declarado) para cada
 * {@link TipoMensagem} de midia, e os tetos de tamanho da Meta Cloud API usados como fallback
 * quando ninguem configurou um limite mais apertado em {@code configuracao_automacao}.
 *
 * <p>Java puro de proposito (so {@code java.util.Map}): e regra de negocio — quais tipos de arquivo
 * o produto aceita —, nao detalhe de infraestrutura, entao mora no dominio junto de
 * {@link DetectorDeTipoReal} e nao atras de framework nenhum.
 *
 * <p><b>Os tetos abaixo precisam ser confirmados contra a documentacao atual da Meta antes de
 * production:</b> historicamente 5 MB (imagem), 16 MB (audio), 100 MB (documento) — e exatamente o
 * tipo de numero que muda sem aviso.
 */
public final class TiposDeMidiaPermitidos {

    private static final Map<String, TipoMensagem> POR_MIMETYPE = Map.ofEntries(
            Map.entry("image/jpeg", TipoMensagem.IMAGEM),
            Map.entry("image/png", TipoMensagem.IMAGEM),
            Map.entry("image/webp", TipoMensagem.IMAGEM),
            Map.entry("audio/ogg", TipoMensagem.AUDIO),
            Map.entry("audio/mpeg", TipoMensagem.AUDIO),
            Map.entry("audio/mp4", TipoMensagem.AUDIO),
            Map.entry("audio/amr", TipoMensagem.AUDIO),
            Map.entry("audio/aac", TipoMensagem.AUDIO),
            Map.entry("application/pdf", TipoMensagem.DOCUMENTO),
            Map.entry("application/msword", TipoMensagem.DOCUMENTO),
            Map.entry(
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    TipoMensagem.DOCUMENTO),
            Map.entry("application/vnd.ms-excel", TipoMensagem.DOCUMENTO),
            Map.entry(
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    TipoMensagem.DOCUMENTO),
            Map.entry("text/plain", TipoMensagem.DOCUMENTO));

    private static final Map<TipoMensagem, Long> TETO_DA_META_EM_BYTES = Map.of(
            TipoMensagem.IMAGEM, 5L * 1024 * 1024,
            TipoMensagem.AUDIO, 16L * 1024 * 1024,
            TipoMensagem.DOCUMENTO, 100L * 1024 * 1024);

    private TiposDeMidiaPermitidos() {}

    /** Vazio quando o mimetype real nao esta na allowlist — rejeitar, mesmo com extensao "certa". */
    public static Optional<TipoMensagem> tipoDe(String mimetypeReal) {
        return Optional.ofNullable(POR_MIMETYPE.get(mimetypeReal));
    }

    public static long tetoDaMetaEmBytes(TipoMensagem tipo) {
        Long teto = TETO_DA_META_EM_BYTES.get(tipo);
        if (teto == null) {
            throw new IllegalArgumentException(tipo + " nao e um tipo de midia com teto conhecido");
        }
        return teto;
    }
}
