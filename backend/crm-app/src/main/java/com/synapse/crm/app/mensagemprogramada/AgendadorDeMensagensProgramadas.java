package com.synapse.crm.app.mensagemprogramada;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.synapse.crm.sharedkernel.identidade.ContextoDeServico;

/** Ponto de entrada do scheduler; operações de banco ficam no bean transacional separado. */
@Component
public class AgendadorDeMensagensProgramadas {

    private static final Logger log = LoggerFactory.getLogger(AgendadorDeMensagensProgramadas.class);

    private final ProcessarMensagemProgramadaUseCase operacoes;
    private final MensagensProgramadasProperties propriedades;

    public AgendadorDeMensagensProgramadas(
            ProcessarMensagemProgramadaUseCase operacoes, MensagensProgramadasProperties propriedades) {
        this.operacoes = operacoes;
        this.propriedades = propriedades;
    }

    @Scheduled(fixedDelayString = "${synapse.suporte.mensagens-programadas.intervalo-ms:1000}")
    public void processarPendentes() {
        ContextoDeServico.executarComo("processador-mensagens-programadas", () -> {
            for (var id : operacoes.idsVencidos(propriedades.lote())) {
                try {
                    operacoes.processar(id);
                } catch (RuntimeException erro) {
                    log.warn("Mensagem programada {} não processada nesta rodada", id, erro);
                }
            }
        });
    }
}
