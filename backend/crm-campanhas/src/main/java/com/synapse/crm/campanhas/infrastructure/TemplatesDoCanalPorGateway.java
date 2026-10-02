package com.synapse.crm.campanhas.infrastructure;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.synapse.crm.atendimento.domain.canal.CanalGateway;
import com.synapse.crm.atendimento.domain.canal.TemplateDoCanal;
import com.synapse.crm.campanhas.application.TemplatesDoCanal;

/**
 * Templates da conta pelo canal ativo, com cache curto. O ciclo de envio consulta o status a cada passada; sem
 * cache seriam dezenas de chamadas por minuto a Meta (que tem limite de taxa). Se o provedor falha e ha um cache
 * recente, usa-o: indisponibilidade passageira nao pode virar "template removido".
 *
 * <p>O adaptador do canal ja protege a chamada com circuit breaker; aqui so se traduz a falha.
 */
@Component
class TemplatesDoCanalPorGateway implements TemplatesDoCanal {

    static final Duration VALIDADE = Duration.ofSeconds(60);
    static final Duration TOLERANCIA_EM_FALHA = Duration.ofMinutes(5);

    private static final Logger log = LoggerFactory.getLogger(TemplatesDoCanalPorGateway.class);

    private record Cache(List<TemplateDoCanal> templates, Instant carregadoEm) {}

    private final CanalGateway canal;
    private final Clock relogio;
    private volatile Cache cache;

    TemplatesDoCanalPorGateway(CanalGateway canal, Clock relogio) {
        this.canal = canal;
        this.relogio = relogio;
    }

    @Override
    public List<TemplateDoCanal> listar() {
        Instant agora = Instant.now(relogio);
        Cache atual = cache;
        if (atual != null && Duration.between(atual.carregadoEm(), agora).compareTo(VALIDADE) < 0) {
            return atual.templates();
        }
        try {
            List<TemplateDoCanal> carregados = List.copyOf(canal.listarTemplates());
            cache = new Cache(carregados, agora);
            return carregados;
        } catch (RuntimeException erro) {
            if (atual != null && Duration.between(atual.carregadoEm(), agora).compareTo(TOLERANCIA_EM_FALHA) < 0) {
                log.warn("Provedor de templates indisponivel; usando a lista de {}.", atual.carregadoEm(), erro);
                return atual.templates();
            }
            throw new TemplatesIndisponiveisException("nao foi possivel listar os templates do provedor", erro);
        }
    }

    @Override
    public void descartarCache() {
        cache = null;
    }

    @Override
    public Optional<TemplateDoCanal> buscar(String nome, String idioma) {
        return listar().stream()
                .filter(template -> template.nome().equals(nome) && template.idioma().equals(idioma))
                .findFirst();
    }
}
