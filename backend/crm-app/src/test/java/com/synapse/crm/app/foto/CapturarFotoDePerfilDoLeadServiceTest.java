package com.synapse.crm.app.foto;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import com.synapse.crm.atendimento.infrastructure.canal.CanalProperties;
import com.synapse.crm.core.application.lead.LeadRepositorio;
import com.synapse.crm.core.application.lead.foto.AtualizarFotoDoLeadUseCase;
import com.synapse.crm.sharedkernel.identidade.ContextoDeServico;

class CapturarFotoDePerfilDoLeadServiceTest {

    @Test
    void primeiraConsultaDeUmLeadNaoTentaSubstituirValorNuloNoCache() {
        var agora = Instant.parse("2026-09-17T12:00:00Z");
        var leads = mock(LeadRepositorio.class);
        var transacao = mock(PlatformTransactionManager.class);
        when(transacao.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        UUID leadId = UUID.randomUUID();
        var service = new CapturarFotoDePerfilDoLeadService(
                leads,
                mock(AtualizarFotoDoLeadUseCase.class),
                List.of(),
                new CanalProperties("meta-cloud", "", "", "", "", "", null, null, "", "", ""),
                new FotoDePerfilProperties(true, Duration.ofHours(1), 1, 1, 1024),
                transacao,
                Clock.fixed(agora, ZoneOffset.UTC));

        ContextoDeServico.instalarPonteDeAutoridade(nome -> () -> {});
        try {
            service.executar(leadId);
            service.executar(leadId);
        } finally {
            ContextoDeServico.instalarPonteDeAutoridade(ContextoDeServico.PonteDeAutoridade.NAO_INSTALADA);
        }

        verify(leads).porId(leadId);
    }
}
