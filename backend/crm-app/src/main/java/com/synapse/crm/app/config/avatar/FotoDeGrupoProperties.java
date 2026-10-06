package com.synapse.crm.app.config.avatar;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Limites de dimensao da foto de grupo do chat interno. O tamanho em bytes nao entra aqui: reutiliza
 * {@code anexo.tamanho_maximo_imagem_mb}, que a gestao ja edita em tempo de execucao.
 *
 * <p>Bytes nao limitam pixels: um PNG de poucos KB pode declarar 30000x30000 e custar gigabytes ao
 * ser decodificado. Por isso o teto e em pixels totais, nao so por lado.
 *
 * @param ladoMinimoPx menor lado aceito; abaixo disso a miniatura de 256px sairia borrada
 * @param ladoMaximoPx maior lado aceito
 * @param pixelsMaximos largura x altura maxima; 24 milhoes cobrem fotos de 24 MP (cerca de 96 MB
 *     decodificada), acima disso a decodificacao ameacaria o heap da instancia
 */
@ConfigurationProperties("synapse.chat-interno.foto-grupo")
public record FotoDeGrupoProperties(int ladoMinimoPx, int ladoMaximoPx, long pixelsMaximos) {

    static final int LADO_MINIMO_PADRAO = 64;
    static final int LADO_MAXIMO_PADRAO = 8000;
    static final long PIXELS_MAXIMOS_PADRAO = 24_000_000L;

    public FotoDeGrupoProperties {
        ladoMinimoPx = ladoMinimoPx <= 0 ? LADO_MINIMO_PADRAO : ladoMinimoPx;
        ladoMaximoPx = ladoMaximoPx <= 0 ? LADO_MAXIMO_PADRAO : ladoMaximoPx;
        pixelsMaximos = pixelsMaximos <= 0 ? PIXELS_MAXIMOS_PADRAO : pixelsMaximos;
        if (ladoMinimoPx > ladoMaximoPx) {
            throw new IllegalArgumentException("lado-minimo-px nao pode ser maior que lado-maximo-px");
        }
    }
}
