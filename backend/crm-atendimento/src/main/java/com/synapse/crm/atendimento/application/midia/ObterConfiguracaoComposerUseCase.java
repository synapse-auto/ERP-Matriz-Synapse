package com.synapse.crm.atendimento.application.midia;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import com.synapse.crm.atendimento.domain.mensagem.TipoMensagem;
import com.synapse.crm.atendimento.domain.midia.TiposDeMidiaPermitidos;

/** Configuracao operacional minima que o composer precisa antes de iniciar uma gravacao. */
@Service
public class ObterConfiguracaoComposerUseCase {

    private final LimiteDeAnexoRepositorio limites;

    public ObterConfiguracaoComposerUseCase(LimiteDeAnexoRepositorio limites) {
        this.limites = limites;
    }

    @PreAuthorize("isAuthenticated()")
    public Resultado executar() {
        long tamanhoMaximoAudio = limites
                .limiteEmBytes(TipoMensagem.AUDIO)
                .orElseGet(() -> TiposDeMidiaPermitidos.tetoDaMetaEmBytes(TipoMensagem.AUDIO));
        long duracaoMaximaAudio = limites
                .duracaoMaximaAudioEmSegundos()
                .orElseThrow(() -> new IllegalStateException(
                        "configuracao gravacao_audio.duracao_maxima_segundos ausente"));
        return new Resultado(tamanhoMaximoAudio, duracaoMaximaAudio);
    }

    public record Resultado(long tamanhoMaximoAudioBytes, long duracaoMaximaAudioSegundos) {}
}
