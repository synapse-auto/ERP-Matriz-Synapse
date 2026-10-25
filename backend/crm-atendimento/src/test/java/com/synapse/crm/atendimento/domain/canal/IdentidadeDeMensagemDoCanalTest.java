package com.synapse.crm.atendimento.domain.canal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class IdentidadeDeMensagemDoCanalTest {
    @Test void chaveInternaIsolaTodosOsComponentesSemAlterarIdBruto() {
        var original = new IdentidadeDeMensagemDoCanal("z-api", "A", "WHATSAPP", "id-bruto");
        assertThat(original.valor()).isEqualTo("id-bruto");
        assertThat(Set.of(original.chaveInterna(),
                new IdentidadeDeMensagemDoCanal("outro", "A", "WHATSAPP", "id-bruto").chaveInterna(),
                new IdentidadeDeMensagemDoCanal("z-api", "B", "WHATSAPP", "id-bruto").chaveInterna(),
                new IdentidadeDeMensagemDoCanal("z-api", "A", "PROCESSAMENTO", "id-bruto").chaveInterna())).hasSize(4);
        assertThat(original.chaveInterna()).isEqualTo(new IdentidadeDeMensagemDoCanal("z-api", "A", "WHATSAPP", "id-bruto").chaveInterna());
    }

    @ParameterizedTest @NullAndEmptySource @ValueSource(strings = {" ", "id\n", "id\u0000"})
    void identidadeInvalidaNaoAtravessaAFronteira(String valor) {
        assertThatThrownBy(() -> new IdentidadeDeMensagemDoCanal("z-api", "A", "WHATSAPP", valor)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void namespaceDesconhecidoNaoEVinculadoImplicitamente() {
        assertThatThrownBy(() -> new IdentidadeDeMensagemDoCanal("z-api", "A", "DESCONHECIDO", "id")).isInstanceOf(IllegalArgumentException.class);
    }
}
