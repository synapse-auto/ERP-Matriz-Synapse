package com.synapse.crm.automacaoconfig.infrastructure.reacao;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.synapse.crm.automacaoconfig.domain.evento.ConfiguracaoAutomacaoAtualizada;
import com.synapse.crm.automacaoconfig.infrastructure.ChavesDeCacheConfiguracaoAutomacao;

/**
 * Invalida o cache Redis quando um parametro muda (E07 §3): {@code automation.config.updated}.
 *
 * <p>{@code AFTER_COMMIT}: invalidar antes do commit e invalidar por nada, se a transacao reverter —
 * a proxima leitura encontraria cache vazio, repovoaria do banco com o valor ANTIGO (ainda vigente,
 * porque o rollback desfez a escrita) e o efeito seria identico a nunca ter invalidado. Depois do
 * commit e o unico momento em que "o banco mudou" e verdade.
 */
@Component
class CacheDeConfiguracaoAutomacaoListener {

    private static final Logger log = LoggerFactory.getLogger(CacheDeConfiguracaoAutomacaoListener.class);

    private final StringRedisTemplate redis;

    CacheDeConfiguracaoAutomacaoListener(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void aoAtualizar(ConfiguracaoAutomacaoAtualizada evento) {
        try {
            redis.delete(ChavesDeCacheConfiguracaoAutomacao.porChave(evento.chave()));
            redis.delete(ChavesDeCacheConfiguracaoAutomacao.TODAS);
        } catch (RuntimeException e) {
            // Redis fora do ar aqui significa cache desatualizado ate expirar/ser
            // reescrito na proxima leitura — nunca motivo para reprovar uma alteracao
            // que ja esta gravada e confirmada no banco.
            log.warn("Falha ao invalidar cache de configuracao de automacao apos atualizar '{}'.",
                    evento.chave(), e);
        }
    }
}
