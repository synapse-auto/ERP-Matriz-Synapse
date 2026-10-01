package com.synapse.crm.automacaoconfig.infrastructure.proativo;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.synapse.crm.atendimento.application.origem.TipoDeOrigem;
import com.synapse.crm.atendimento.application.proativo.PoliticaDeEnvioProativo.Politica;
import com.synapse.crm.automacaoconfig.application.ConfiguracaoAutomacaoRepositorio;
import com.synapse.crm.automacaoconfig.domain.ConfiguracaoAutomacao;
import com.synapse.crm.automacaoconfig.domain.TipoConfiguracaoAutomacao;

class PoliticaDeEnvioProativoPorConfiguracaoTest {

    private final Map<String, ConfiguracaoAutomacao> parametros = new HashMap<>();
    private final PoliticaDeEnvioProativoPorConfiguracao politica =
            new PoliticaDeEnvioProativoPorConfiguracao(new Repositorio());

    @Test
    @DisplayName("sem parametros vale o comportamento anterior: tudo ligado, sem cooldown nem teto")
    void ausentes_semRestricao() {
        assertThat(politica.vigente()).isEqualTo(Politica.semRestricao());
    }

    @Test
    @DisplayName("le chave geral, chave por tipo, cooldown e teto")
    void leParametros() {
        booleano("automacao_proativa.habilitada", "false");
        booleano("automacao_proativa.festiva.habilitada", "false");
        booleano("automacao_proativa.follow_up.habilitada", "true");
        inteiro("automacao_proativa.cooldown_horas", "24");
        inteiro("automacao_proativa.teto_diario_por_lead", "2");

        Politica vigente = politica.vigente();

        assertThat(vigente.habilitada()).isFalse();
        assertThat(vigente.tiposDesligados()).containsExactly(TipoDeOrigem.FESTIVA);
        assertThat(vigente.cooldownHoras()).isEqualTo(24);
        assertThat(vigente.tetoDiarioPorLead()).isEqualTo(2);
    }

    @Test
    @DisplayName("valor invalido ou fora da faixa volta ao padrao, sem calar nem liberar alem do anterior")
    void invalidos_voltamAoPadrao() {
        booleano("automacao_proativa.habilitada", "talvez");
        inteiro("automacao_proativa.cooldown_horas", "-1");
        inteiro("automacao_proativa.teto_diario_por_lead", "muitos");

        assertThat(politica.vigente()).isEqualTo(Politica.semRestricao());
    }

    @Test
    @DisplayName("toda chave por tipo proativo existe com o nome semeado na V85")
    void chavesPorTipo() {
        assertThat(List.of(TipoDeOrigem.values()).stream()
                        .filter(TipoDeOrigem::proativa)
                        .map(PoliticaDeEnvioProativoPorConfiguracao::chaveDoTipo))
                .containsExactly(
                        "automacao_proativa.follow_up.habilitada",
                        "automacao_proativa.fidelizacao.habilitada",
                        "automacao_proativa.festiva.habilitada",
                        "automacao_proativa.aniversario.habilitada",
                        "automacao_proativa.avaliacao.habilitada",
                        "automacao_proativa.lembrete.habilitada",
                        "automacao_proativa.outro.habilitada");
    }

    private void booleano(String chave, String valor) {
        parametros.put(chave, parametro(chave, valor, TipoConfiguracaoAutomacao.BOOLEAN));
    }

    private void inteiro(String chave, String valor) {
        parametros.put(chave, parametro(chave, valor, TipoConfiguracaoAutomacao.INT));
    }

    private static ConfiguracaoAutomacao parametro(String chave, String valor, TipoConfiguracaoAutomacao tipo) {
        return new ConfiguracaoAutomacao(chave, valor, null, tipo, null, null, null, null, Instant.EPOCH);
    }

    private final class Repositorio implements ConfiguracaoAutomacaoRepositorio {
        @Override
        public List<ConfiguracaoAutomacao> listarTodas() {
            return List.copyOf(parametros.values());
        }

        @Override
        public Optional<ConfiguracaoAutomacao> porChave(String chave) {
            return Optional.ofNullable(parametros.get(chave));
        }

        @Override
        public ConfiguracaoAutomacao salvar(ConfiguracaoAutomacao configuracao) {
            parametros.put(configuracao.chave(), configuracao);
            return configuracao;
        }
    }
}
