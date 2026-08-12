package com.synapse.crm.app.saude.infrastructure;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.synapse.crm.app.config.SynapseProperties;
import com.synapse.crm.app.saude.application.HorarioComercialDoCliente;
import com.synapse.crm.automacaoconfig.application.ConfiguracaoAutomacaoRepositorio;

/** Janela editável no CRUD já existente de {@code configuracao_automacao}. */
@Component
class HorarioComercialConfiguravel implements HorarioComercialDoCliente {

    static final String CHAVE_INICIO = "alerta.horario_cliente.inicio";
    static final String CHAVE_FIM = "alerta.horario_cliente.fim";

    private static final Logger log = LoggerFactory.getLogger(HorarioComercialConfiguravel.class);

    private final ConfiguracaoAutomacaoRepositorio configuracoes;
    private final ZoneId fusoHorario;
    private final AtomicReference<Janela> ultimaJanelaValida = new AtomicReference<>();

    HorarioComercialConfiguravel(
            ConfiguracaoAutomacaoRepositorio configuracoes, SynapseProperties propriedades) {
        this.configuracoes = configuracoes;
        this.fusoHorario = ZoneId.of(propriedades.tenant().timezone());
    }

    @Override
    public boolean permiteAvisarCliente(Instant instante) {
        atualizarCacheSePossivel();
        Janela janela = ultimaJanelaValida.get();
        return janela != null && janela.contem(instante.atZone(fusoHorario).toLocalTime());
    }

    private void atualizarCacheSePossivel() {
        try {
            Optional<String> inicio = configuracoes.porChave(CHAVE_INICIO).map(c -> c.valor());
            Optional<String> fim = configuracoes.porChave(CHAVE_FIM).map(c -> c.valor());
            if (inicio.isPresent() && fim.isPresent()) {
                ultimaJanelaValida.set(
                        new Janela(LocalTime.parse(inicio.get()), LocalTime.parse(fim.get())));
            }
        } catch (RuntimeException e) {
            log.warn(
                    "Nao foi possivel atualizar a janela de alerta do cliente; usando o ultimo valor valido.");
        }
    }

    private record Janela(LocalTime inicio, LocalTime fim) {

        private Janela {
            if (!fim.isAfter(inicio)) {
                throw new IllegalArgumentException("fim da janela deve ser posterior ao inicio");
            }
        }

        boolean contem(LocalTime horario) {
            return !horario.isBefore(inicio) && horario.isBefore(fim);
        }
    }
}
