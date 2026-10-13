package com.synapse.crm.app.saude.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.synapse.crm.app.config.SynapseProperties;
import com.synapse.crm.automacaoconfig.application.ConfiguracaoAutomacaoRepositorio;
import com.synapse.crm.automacaoconfig.domain.ConfiguracaoAutomacao;
import com.synapse.crm.automacaoconfig.domain.TipoConfiguracaoAutomacao;

/**
 * Janela de aviso ao cliente por dia da semana (D4 da Disbrel). Datas de referência, fuso America/Sao_Paulo
 * (UTC-3, sem horário de verão em 2026): 10/10/2026 é sábado; 11/10, domingo; 12/10, segunda.
 */
class HorarioComercialConfiguravelTest {

    private static final Instant SABADO_10H = Instant.parse("2026-10-10T13:00:00Z");
    private static final Instant SABADO_13H = Instant.parse("2026-10-10T16:00:00Z");
    private static final Instant DOMINGO_10H = Instant.parse("2026-10-11T13:00:00Z");
    private static final Instant SEGUNDA_10H = Instant.parse("2026-10-12T13:00:00Z");

    private final Map<String, String> banco = new HashMap<>();
    private boolean bancoCaiu;
    private HorarioComercialConfiguravel horario;

    @BeforeEach
    void preparar() {
        banco.clear();
        bancoCaiu = false;
        banco.put("alerta.horario_cliente.inicio", "08:00");
        banco.put("alerta.horario_cliente.fim", "18:30");
        horario = novo("America/Sao_Paulo");
    }

    @Nested
    @DisplayName("sem chave por dia (configuração de hoje)")
    class SemChavePorDia {

        @Test
        @DisplayName("comporta-se exatamente como a janela única: sábado 13h e domingo 10h entram")
        void igualAHoje() {
            assertThat(horario.permiteAvisarCliente(SABADO_10H)).isTrue();
            assertThat(horario.permiteAvisarCliente(SABADO_13H)).isTrue();
            assertThat(horario.permiteAvisarCliente(DOMINGO_10H)).isTrue();
            assertThat(horario.permiteAvisarCliente(SEGUNDA_10H)).isTrue();
            assertThat(horario.permiteAvisarCliente(Instant.parse("2026-10-12T21:30:00Z"))).isFalse(); // 18:30 local (fim exclusivo)
            assertThat(horario.permiteAvisarCliente(Instant.parse("2026-10-12T10:59:00Z"))).isFalse(); // 07:59 local
            assertThat(horario.permiteAvisarCliente(Instant.parse("2026-10-12T11:00:00Z"))).isTrue(); // 08:00 local (início inclusivo)
        }

        @Test
        @DisplayName("sem nem a janela padrão, nunca avisa o cliente (como antes)")
        void semJanelaPadraoNaoAvisa() {
            banco.clear();

            assertThat(novo("America/Sao_Paulo").permiteAvisarCliente(SEGUNDA_10H)).isFalse();
        }
    }

    @Nested
    @DisplayName("com chave por dia")
    class ComChavePorDia {

        @BeforeEach
        void sabadoMeioDiaEDomingoFechado() {
            banco.put("alerta.horario_cliente.sab", "08:00-12:00");
            banco.put("alerta.horario_cliente.dom", "fechado");
        }

        @Test
        @DisplayName("sábado 10h entra; sábado 13h e domingo saem; segunda segue a janela padrão")
        void sabadoDentroEForaEDomingoFora() {
            assertThat(horario.permiteAvisarCliente(SABADO_10H)).isTrue();
            assertThat(horario.permiteAvisarCliente(SABADO_13H)).isFalse();
            assertThat(horario.permiteAvisarCliente(DOMINGO_10H)).isFalse();
            assertThat(horario.permiteAvisarCliente(SEGUNDA_10H)).isTrue();
        }

        @Test
        @DisplayName("o fim é exclusivo e o início, inclusivo")
        void bordas() {
            assertThat(horario.permiteAvisarCliente(Instant.parse("2026-10-10T11:00:00Z"))).isTrue(); // 08:00
            assertThat(horario.permiteAvisarCliente(Instant.parse("2026-10-10T10:59:00Z"))).isFalse(); // 07:59
            assertThat(horario.permiteAvisarCliente(Instant.parse("2026-10-10T14:59:00Z"))).isTrue(); // 11:59
            assertThat(horario.permiteAvisarCliente(Instant.parse("2026-10-10T15:00:00Z"))).isFalse(); // 12:00
        }

        @Test
        @DisplayName("valor com espaços e caixa diferente é aceito")
        void toleraEspacosECaixa() {
            banco.put("alerta.horario_cliente.dom", "  FeChado ");

            assertThat(horario.permiteAvisarCliente(DOMINGO_10H)).isFalse();
        }

        @Test
        @DisplayName("valor ilegível cai na janela padrão (erro de digitação não silencia o alerta)")
        void valorIlegivelCaiNoPadrao() {
            for (String ruim : List.of("08:00", "8-12", "12:00-08:00", "08:00-08:00", "25:00-26:00", "fechada", "")) {
                banco.put("alerta.horario_cliente.sab", ruim);

                assertThat(novo("America/Sao_Paulo").permiteAvisarCliente(SABADO_13H))
                        .as("valor '%s' deve cair no padrão (13h está dentro de 08:00–18:30)", ruim)
                        .isTrue();
            }
        }

        @Test
        @DisplayName("apagar a chave devolve o dia à janela padrão")
        void removerAChaveVoltaAoPadrao() {
            assertThat(horario.permiteAvisarCliente(SABADO_13H)).isFalse();

            banco.remove("alerta.horario_cliente.sab");

            assertThat(horario.permiteAvisarCliente(SABADO_13H)).isTrue();
        }
    }

    @Nested
    @DisplayName("fuso da instância")
    class Fuso {

        @Test
        @DisplayName("hora e dia da semana são os do fuso da instância, não os do UTC")
        void usaOFusoDaInstancia() {
            banco.put("alerta.horario_cliente.sab", "08:00-12:00");
            banco.put("alerta.horario_cliente.dom", "fechado");
            // 12:30Z = 09:30 em São Paulo (dentro de sáb 08–12); em UTC já seria fora.
            Instant sabado0930Local = Instant.parse("2026-10-10T12:30:00Z");
            // 01:00Z de domingo = sábado 22:00 em São Paulo: ainda sábado, nunca "domingo fechado".
            Instant sabado2200Local = Instant.parse("2026-10-11T01:00:00Z");

            assertThat(horario.permiteAvisarCliente(sabado0930Local)).isTrue();
            // Fora de 08–12 da chave de sábado: se o dia fosse o do UTC (domingo, fechado) também daria false,
            // então o discriminante é a chave de sábado alargada abaixo.
            banco.put("alerta.horario_cliente.sab", "08:00-23:00");
            assertThat(horario.permiteAvisarCliente(sabado2200Local))
                    .as("22:00 de sábado local usa a chave de sábado (08–23), embora em UTC seja domingo fechado")
                    .isTrue();
        }

        @Test
        @DisplayName("em outro fuso o mesmo instante cai em outro dia")
        void outroFuso() {
            banco.put("alerta.horario_cliente.sab", "08:00-12:00");
            banco.put("alerta.horario_cliente.dom", "fechado");
            // Em Tóquio (UTC+9), 2026-10-10T23:30Z já é domingo 08:30: fechado.
            assertThat(novo("Asia/Tokyo").permiteAvisarCliente(Instant.parse("2026-10-10T23:30:00Z"))).isFalse();
            // E 2026-10-10T01:30Z é sábado 10:30 em Tóquio: dentro.
            assertThat(novo("Asia/Tokyo").permiteAvisarCliente(Instant.parse("2026-10-10T01:30:00Z"))).isTrue();
        }
    }

    @Nested
    @DisplayName("banco indisponível")
    class BancoIndisponivel {

        @Test
        @DisplayName("mantém o último valor conhecido, inclusive o do dia seguinte (virada de dia durante a queda)")
        void mantemOUltimoValorValido() {
            banco.put("alerta.horario_cliente.sab", "08:00-12:00");
            banco.put("alerta.horario_cliente.dom", "fechado");
            assertThat(horario.permiteAvisarCliente(SABADO_10H)).isTrue(); // sábado: aprende sábado e domingo

            bancoCaiu = true;

            assertThat(horario.permiteAvisarCliente(SABADO_13H)).isFalse();
            assertThat(horario.permiteAvisarCliente(DOMINGO_10H)).as("domingo aprendido antes da queda").isFalse();
        }
    }

    private HorarioComercialConfiguravel novo(String fuso) {
        SynapseProperties propriedades =
                new SynapseProperties(new SynapseProperties.Tenant("t", "Teste", fuso), null, null, null, Map.of());
        return new HorarioComercialConfiguravel(new RepositorioEmMemoria(), propriedades);
    }

    private final class RepositorioEmMemoria implements ConfiguracaoAutomacaoRepositorio {

        @Override
        public List<ConfiguracaoAutomacao> listarTodas() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<ConfiguracaoAutomacao> porChave(String chave) {
            if (bancoCaiu) {
                throw new IllegalStateException("banco fora do ar");
            }
            return Optional.ofNullable(banco.get(chave))
                    .map(valor -> new ConfiguracaoAutomacao(
                            chave, valor, "HH:mm", TipoConfiguracaoAutomacao.TEXT, null, null, null, null, Instant.EPOCH));
        }

        @Override
        public ConfiguracaoAutomacao salvar(ConfiguracaoAutomacao configuracao) {
            throw new UnsupportedOperationException();
        }
    }
}
