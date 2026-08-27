package com.synapse.crm.atendimento.domain.midia;

import java.util.Map;
import java.util.Optional;

import com.synapse.crm.atendimento.domain.mensagem.TipoMensagem;
import com.synapse.crm.sharedkernel.midia.RegrasDeAnexoBase;

/**
 * Mapeador local de mimetypes para TipoMensagem do atendimento, e os tetos de tamanho da Meta Cloud API
 * usados como fallback quando ninguem configurou um limite em {@code configuracao_automacao}.
 */
public final class TiposDeMidiaPermitidos {

    private static final Map<TipoMensagem, Long> TETO_DA_META_EM_BYTES = Map.of(
            TipoMensagem.IMAGEM, 5L * 1024 * 1024,
            TipoMensagem.AUDIO, 16L * 1024 * 1024,
            TipoMensagem.DOCUMENTO, 100L * 1024 * 1024);

    private TiposDeMidiaPermitidos() {}

    /** Vazio quando o mimetype real nao esta na allowlist da base. */
    public static Optional<TipoMensagem> tipoDe(String mimetypeReal) {
        return RegrasDeAnexoBase.categoriaDe(mimetypeReal)
                .map(categoria -> switch (categoria) {
                    case IMAGEM -> TipoMensagem.IMAGEM;
                    case AUDIO -> TipoMensagem.AUDIO;
                    case DOCUMENTO -> TipoMensagem.DOCUMENTO;
                });
    }

    public static long tetoDaMetaEmBytes(TipoMensagem tipo) {
        Long teto = TETO_DA_META_EM_BYTES.get(tipo);
        if (teto == null) {
            throw new IllegalArgumentException(tipo + " nao e um tipo de midia com teto conhecido");
        }
        return teto;
    }
}
