package com.synapse.crm.app.atendimento;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.synapse.crm.atendimento.application.finalizacaomassa.ProcessarFinalizacaoEmMassaUseCase;
import com.synapse.crm.atendimento.application.finalizacaomassa.PublicarAvisosDeFinalizacaoEmMassaUseCase;
import com.synapse.crm.sharedkernel.identidade.ContextoDeServico;

/**
 * Dois ciclos curtos e independentes: processar a operacao ativa e entregar os avisos. Cada rodada e limitada (lote
 * e maximo por rodada), entao nunca segura a conexao do chat por mais que alguns itens; uma falha numa rodada e
 * logada e a seguinte retoma.
 */
@Component
public class AgendadorDeFinalizacaoEmMassa {

    static final String MARCADOR_ALARME = "[ALERTA_FINALIZACAO_EM_MASSA]";
    private static final int AVISOS_POR_RODADA = 50;
    private static final Logger log = LoggerFactory.getLogger(AgendadorDeFinalizacaoEmMassa.class);

    private final ProcessarFinalizacaoEmMassaUseCase processar;
    private final PublicarAvisosDeFinalizacaoEmMassaUseCase avisos;

    public AgendadorDeFinalizacaoEmMassa(
            ProcessarFinalizacaoEmMassaUseCase processar, PublicarAvisosDeFinalizacaoEmMassaUseCase avisos) {
        this.processar = processar;
        this.avisos = avisos;
    }

    @Scheduled(fixedDelayString = "${synapse.atendimento.finalizacao-em-massa.intervalo-ms:1000}")
    public void processarOperacao() {
        try {
            ContextoDeServico.executarComo("finalizacao-em-massa", processar::executar);
        } catch (RuntimeException erro) {
            log.error("{} rodada de processamento falhou: tipoErro={}", MARCADOR_ALARME, erro.getClass().getSimpleName(), erro);
        }
    }

    @Scheduled(fixedDelayString = "${synapse.atendimento.finalizacao-em-massa.avisos-intervalo-ms:2000}")
    public void publicarAvisos() {
        try {
            ContextoDeServico.executarComo("avisos-finalizacao-em-massa", () -> avisos.executar(AVISOS_POR_RODADA));
        } catch (RuntimeException erro) {
            log.error("{} rodada de avisos falhou: tipoErro={}", MARCADOR_ALARME, erro.getClass().getSimpleName(), erro);
        }
    }
}
