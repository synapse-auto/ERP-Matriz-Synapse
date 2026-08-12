package com.synapse.crm.app.saude;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.synapse.crm.app.saude.application.AlertaDeSaude;
import com.synapse.crm.app.saude.application.ComponenteDaSaude;
import com.synapse.crm.app.saude.application.DependenciaDoBancoChat;
import com.synapse.crm.app.saude.application.DestinoDoAlerta;
import com.synapse.crm.app.saude.application.MonitorarSaudeCriticaUseCase;
import com.synapse.crm.app.saude.application.PoliticaDeMonitoramento;
import com.synapse.crm.app.saude.application.SeveridadeSaude;
import com.synapse.crm.app.saude.application.VerificadorDeComponente;
import com.synapse.crm.app.saude.application.VerificarSaudeCriticaUseCase;

class MonitorarSaudeCriticaUseCaseTest {

    private static final Instant AGORA = Instant.parse("2026-08-12T14:00:00Z");

    @Test
    void degradadoAposDuasFalhas_avisaSomenteSynapse() {
        Cenario cenario = cenario(SeveridadeSaude.DEGRADADO, true);

        cenario.monitorar().executar();
        assertThat(cenario.alertas()).isEmpty();
        cenario.monitorar().executar();

        assertThat(cenario.alertas()).singleElement().satisfies(alerta -> {
            assertThat(alerta.severidade()).isEqualTo(SeveridadeSaude.DEGRADADO);
            assertThat(alerta.destinos()).containsExactly(DestinoDoAlerta.SYNAPSE);
        });
    }

    @Test
    void criticoForaDoHorario_avisaSomenteSynapse() {
        Cenario cenario = cenario(SeveridadeSaude.CRITICO, false);

        cenario.monitorar().executar();
        cenario.monitorar().executar();

        assertThat(cenario.alertas())
                .singleElement()
                .extracting(AlertaDeSaude::destinos)
                .satisfies(destinos ->
                        assertThat(destinos).containsExactly(DestinoDoAlerta.SYNAPSE));
    }

    @Test
    void criticoNoHorario_avisaClienteESynapse() {
        Cenario cenario = cenario(SeveridadeSaude.CRITICO, true);

        cenario.monitorar().executar();
        cenario.monitorar().executar();

        assertThat(cenario.alertas())
                .singleElement()
                .extracting(AlertaDeSaude::destinos)
                .satisfies(destinos -> assertThat(destinos)
                        .containsExactlyInAnyOrder(
                                DestinoDoAlerta.SYNAPSE, DestinoDoAlerta.CLIENTE));
    }

    private static Cenario cenario(SeveridadeSaude severidade, boolean horarioCliente) {
        Clock relogio = Clock.fixed(AGORA, ZoneOffset.UTC);
        VerificadorDeComponente verificador = new VerificadorFixo(severidade);
        VerificarSaudeCriticaUseCase verificar =
                new VerificarSaudeCriticaUseCase(List.of(verificador), relogio);
        List<AlertaDeSaude> alertas = new ArrayList<>();
        MonitorarSaudeCriticaUseCase monitorar = new MonitorarSaudeCriticaUseCase(
                verificar, alertas::add, instante -> horarioCliente, new PoliticaDeMonitoramento(2));
        return new Cenario(monitorar, alertas);
    }

    private record Cenario(
            MonitorarSaudeCriticaUseCase monitorar, List<AlertaDeSaude> alertas) {}

    private record VerificadorFixo(SeveridadeSaude severidade) implements VerificadorDeComponente {

        @Override
        public String nome() {
            return "componente-e22";
        }

        @Override
        public DependenciaDoBancoChat dependenciaDoBancoChat() {
            return DependenciaDoBancoChat.INDEPENDENTE;
        }

        @Override
        public SeveridadeSaude severidadeDaFalha() {
            return severidade;
        }

        @Override
        public ComponenteDaSaude verificar() {
            return ComponenteDaSaude.down(nome(), severidade, "falha de teste");
        }
    }
}
