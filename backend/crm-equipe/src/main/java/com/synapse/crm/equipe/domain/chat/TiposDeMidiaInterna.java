package com.synapse.crm.equipe.domain.chat;

import java.util.Optional;
import java.util.Set;

import com.synapse.crm.sharedkernel.midia.CategoriaDeMidia;
import com.synapse.crm.sharedkernel.midia.IsoBmffAudioOnly;
import com.synapse.crm.sharedkernel.midia.RegrasDeAnexoBase;

/** Classificação interna por bytes, independente dos provedores de WhatsApp. */
public final class TiposDeMidiaInterna {
    private static final Set<String> CONTEINERES = Set.of("video/mp4", "video/quicktime", "video/3gpp", "audio/mp4");
    private static final Set<String> MP4 = Set.of("isom", "iso2", "iso4", "iso5", "iso6", "mp41", "mp42", "avc1", "dash", "mmp4");
    private static final Set<String> TRES_GP = Set.of("3gp4", "3gp5", "3gp6", "3gp7", "3gs7", "3g2a", "3g2b");

    private TiposDeMidiaInterna() {}
    public record Classificacao(CategoriaDeMidia categoria, String mimetype) {}

    public static Optional<Classificacao> classificar(String detectado, byte[] bytes) {
        String mime = IsoBmffAudioOnly.mimetypeDeAudioSeCamuflado(detectado, bytes);
        if (CONTEINERES.contains(mime) && IsoBmffAudioOnly.temTrilhaDeVideo(bytes)) {
            if (!IsoBmffAudioOnly.ehVideoValido(bytes)) return Optional.empty();
            String marca = IsoBmffAudioOnly.marcaPrincipal(bytes);
            if (MP4.contains(marca)) return Optional.of(new Classificacao(CategoriaDeMidia.VIDEO, "video/mp4"));
            if (TRES_GP.contains(marca)) return Optional.of(new Classificacao(CategoriaDeMidia.VIDEO, "video/3gpp"));
            return Optional.empty(); // M4A com vídeo e QuickTime não se tornam áudio nem vídeo permitido.
        }
        return RegrasDeAnexoBase.categoriaDe(mime).map(categoria -> new Classificacao(categoria, mime));
    }
}
