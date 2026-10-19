package com.synapse.crm.app.cancelamentos;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Qualifier;

import com.synapse.crm.core.application.lead.LeadRepositorio;
import com.synapse.crm.sharedkernel.persistencia.Pools;

class ValidacaoDoVinculoCancelamentoAdapterTest {
    @Test void validacaoNaoPodeConsumirRepositorioOuPoolReservadoDoChat() throws Exception {
        // Falha na implementacao anterior, que recebia AtendimentoRepositorio ligado ao pool chat.
        var construtor=ValidacaoDoVinculoCancelamentoAdapter.class
                .getDeclaredConstructor(LeadRepositorio.class,DataSource.class);
        var qualificador=construtor.getParameters()[1].getAnnotation(Qualifier.class);
        assertThat(qualificador).isNotNull();
        assertThat(qualificador.value()).isEqualTo(Pools.GENERAL_DATA_SOURCE);
    }
}
