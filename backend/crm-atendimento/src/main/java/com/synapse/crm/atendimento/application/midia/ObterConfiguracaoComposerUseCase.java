package com.synapse.crm.atendimento.application.midia;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import com.synapse.crm.atendimento.domain.mensagem.TipoMensagem;
import com.synapse.crm.atendimento.domain.midia.TiposDeMidiaPermitidos;

/** Configuracao operacional minima que o composer precisa antes de iniciar uma gravacao. */
@Service
public class ObterConfiguracaoComposerUseCase {

    private final LimiteDeAnexoRepositorio limites;

    private final long tempoNotificacaoSegundos;

    public ObterConfiguracaoComposerUseCase(
            LimiteDeAnexoRepositorio limites,
            @Value("${synapse.atendimentos.tempo-notificacao-segundos:8}") long tempoNotificacaoSegundos) {
        this.limites = limites;
        if (tempoNotificacaoSegundos < 1 || tempoNotificacaoSegundos > 60) {
            throw new IllegalArgumentException("tempo de notificacao deve estar entre 1 e 60 segundos");
        }
        this.tempoNotificacaoSegundos = tempoNotificacaoSegundos;
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
        return new Resultado(tamanhoMaximoAudio, duracaoMaximaAudio, tempoNotificacaoSegundos);
    }

    public record Resultado(long tamanhoMaximoAudioBytes, long duracaoMaximaAudioSegundos,
            long tempoNotificacaoSegundos) {}
}
