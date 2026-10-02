package com.synapse.crm.campanhas.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.campanhas.domain.Campanha;
import com.synapse.crm.campanhas.domain.StatusDaCampanha;
import com.synapse.crm.sharedkernel.persistencia.Pools;

/**
 * Para a campanha sozinha: falha em excesso, erro de limite ou qualidade da Meta, template invalido. Le com
 * {@code FOR UPDATE}, entao serializa com o ciclo de envio e com a pausa manual. Nao faz nada se a campanha ja
 * nao esta em andamento.
 */
@Component
public class PausaAutomaticaDaCampanha {

    /** Marcador fixo para o agregador de logs e o alarme. */
    public static final String MARCADOR_ALARME = "[ALERTA_CAMPANHA_PAUSADA]";

    private static final Logger log = LoggerFactory.getLogger(PausaAutomaticaDaCampanha.class);

    private final CampanhaRepositorio campanhas;
    private final Clock relogio;

    public PausaAutomaticaDaCampanha(CampanhaRepositorio campanhas, Clock relogio) {
        this.campanhas = campanhas;
        this.relogio = relogio;
    }

    /** @return se esta chamada foi quem pausou */
    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER)
    public boolean pausar(UUID campanhaId, String motivo) {
        Optional<Campanha> campanha = campanhas.bloquearPorId(campanhaId);
        if (campanha.isEmpty() || campanha.get().status() != StatusDaCampanha.EM_ANDAMENTO) {
            return false;
        }
        Instant agora = Instant.now(relogio);
        campanhas.atualizar(campanha.get().pausarAutomaticamente(agora, motivo));
        log.warn(
                "{} campanha {} ({}) pausada automaticamente: {}. Ela nao envia mais ate um administrador retomar.",
                MARCADOR_ALARME,
                campanhaId,
                campanha.get().nome(),
                motivo);
        return true;
    }
}
