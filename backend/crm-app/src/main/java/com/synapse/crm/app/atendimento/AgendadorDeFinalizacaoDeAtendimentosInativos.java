package com.synapse.crm.app.atendimento;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.synapse.crm.atendimento.application.FinalizarAtendimentosInativosUseCase;
import com.synapse.crm.automacaoconfig.application.ConfiguracaoAutomacaoRepositorio;
import com.synapse.crm.automacaoconfig.domain.ConfiguracaoAutomacao;
import com.synapse.crm.sharedkernel.identidade.ContextoDeServico;

/**
 * Varre atendimentos humanos parados e os encerra em lotes curtos.
 *
 * <p>O job não toca em {@code EM_IA}: esses leads já estão na fila de Potenciais e encerrá-los
 * removeria justamente a população que a automação deve distribuir. A transição dos humanos usa o
 * caso de uso de finalização, portanto mantém avaliação, timeline, lead e eventos pós-commit.
 */
@Component
public class AgendadorDeFinalizacaoDeAtendimentosInativos {

    static final String CHAVE_HORAS = "atendimento.finalizar_apos_horas";

    private static final Logger log =
            LoggerFactory.getLogger(AgendadorDeFinalizacaoDeAtendimentosInativos.class);

    private final ConfiguracaoAutomacaoRepositorio configuracoes;
    private final FinalizarAtendimentosInativosUseCase finalizar;
    private final FinalizacaoAtendimentoInativoProperties propriedades;
    private final Clock relogio;

    public AgendadorDeFinalizacaoDeAtendimentosInativos(
            ConfiguracaoAutomacaoRepositorio configuracoes,
            FinalizarAtendimentosInativosUseCase finalizar,
            FinalizacaoAtendimentoInativoProperties propriedades,
            Clock relogio) {
        this.configuracoes = configuracoes;
        this.finalizar = finalizar;
        this.propriedades = propriedades;
        this.relogio = relogio;
    }

    @Scheduled(fixedDelayString = "${synapse.atendimento.finalizar-inativos.intervalo-ms:300000}")
    public void finalizarInativos() {
        ContextoDeServico.executarComo("finalizador-atendimentos-inativos", this::executarRodada);
    }

    private void executarRodada() {
        ConfiguracaoAutomacao configuracao = configuracoes.porChave(CHAVE_HORAS).orElse(null);
        if (configuracao == null) {
            log.error("{} parâmetro {} não encontrado; rodada não executada", "[ALERTA_CONFIG_AUTOMACAO]", CHAVE_HORAS);
            return;
        }
        int horas;
        try {
            horas = Integer.parseInt(configuracao.valor().trim());
        } catch (NumberFormatException erro) {
            log.error("{} parâmetro {} inválido; rodada não executada", "[ALERTA_CONFIG_AUTOMACAO]", CHAVE_HORAS, erro);
            return;
        }
        if (horas < 1 || horas > 720) {
            log.error("{} parâmetro {} fora da faixa; rodada não executada", "[ALERTA_CONFIG_AUTOMACAO]", CHAVE_HORAS);
            return;
        }

        Instant agora = Instant.now(relogio);
        var resultado = finalizar.executar(agora, Duration.ofHours(horas), propriedades.lote());
        if (resultado.candidatos() > 0) {
            log.info(
                    "Finalização automática de atendimentos inativos: candidatos={}, finalizados={}, "
                            + "ignorados={}, falhas={}, corte={}",
                    resultado.candidatos(),
                    resultado.finalizados(),
                    resultado.ignorados(),
                    resultado.falhas(),
                    resultado.corte());
        }
    }
}
