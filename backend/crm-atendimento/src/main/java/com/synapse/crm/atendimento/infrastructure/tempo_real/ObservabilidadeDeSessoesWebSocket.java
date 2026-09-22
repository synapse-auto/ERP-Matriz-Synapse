package com.synapse.crm.atendimento.infrastructure.tempo_real;

import java.util.Collection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Medicao local do broker STOMP em memoria.
 *
 * <p>Uma sessao e uma aba/dispositivo conectado; um usuario pode possuir mais de uma. O registro
 * do Spring e atualizado pelo proprio ciclo CONNECT/DISCONNECT, por isso nao mantemos uma segunda
 * colecao que pudesse vazar quando a conexao cai silenciosamente.
 */
@Component
class ObservabilidadeDeSessoesWebSocket {

    static final String MARCADOR = "[METRICA_WEBSOCKET_SESSOES]";

    private static final Logger log = LoggerFactory.getLogger(ObservabilidadeDeSessoesWebSocket.class);

    private final SimpUserRegistry usuarios;

    ObservabilidadeDeSessoesWebSocket(@Lazy SimpUserRegistry usuarios) {
        this.usuarios = usuarios;
    }

    /** Snapshot barato, limitado à instância atual; Redis não é uma fonte de sessões STOMP. */
    MetricasDeSessoesWebSocket medir() {
        Collection<SimpUser> usuariosAtivos = usuarios.getUsers();
        int sessoesAtivas = usuariosAtivos.stream().mapToInt(usuario -> usuario.getSessions().size()).sum();
        return new MetricasDeSessoesWebSocket(sessoesAtivas, usuariosAtivos.size());
    }

    /** Ponto de entrada real do agendador: deixa um número correlacionável no log sem dado pessoal. */
    @Scheduled(fixedDelayString = "${synapse.tempo-real.intervalo-metricas}")
    void registrarMetricas() {
        MetricasDeSessoesWebSocket metricas = medir();
        log.info(
                "{} sessoesAtivas={} usuariosAutenticadosUnicos={}",
                MARCADOR,
                metricas.sessoesAtivas(),
                metricas.usuariosAutenticadosUnicos());
    }
}
