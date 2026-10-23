package com.synapse.crm.atendimento.application.template;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import com.synapse.crm.atendimento.domain.canal.CanalGateway;
import com.synapse.crm.atendimento.domain.canal.ResultadoDeTemplate;
import com.synapse.crm.atendimento.domain.canal.TemplateExcluido;

class EditarEExcluirTemplateWhatsAppUseCaseTest {

    @Test
    void edicaoReutilizaValidacaoDeVariaveisENaoAceitaNome() {
        CanalGateway canal = mock(CanalGateway.class);
        when(canal.editarTemplate(any())).thenReturn(new ResultadoDeTemplate.Aceito(null));

        new EditarTemplateWhatsAppUseCase(canal).executar("meta-1", "Ola {{1}}");

        verify(canal).editarTemplate(any());
        assertThatThrownBy(() -> new EditarTemplateWhatsAppUseCase(canal)
                        .executar("meta-1", "Ola {{2}}"))
                .isInstanceOf(PedidoDeTemplateInvalidoException.class);
    }

    @Test
    void exclusaoExigeIdENomeParaEvitarExcluirTodasAsLinguas() {
        CanalGateway canal = mock(CanalGateway.class);
        when(canal.excluirTemplate("meta-1", "boas_vindas"))
                .thenReturn(new ResultadoDeTemplate.Aceito(null));
        ApplicationEventPublisher eventos = mock(ApplicationEventPublisher.class);

        new ExcluirTemplateWhatsAppUseCase(canal, eventos).executar("meta-1", "boas_vindas");
        verify(canal).excluirTemplate("meta-1", "boas_vindas");
        verify(eventos).publishEvent(new TemplateExcluido("meta-1"));

        assertThatThrownBy(() -> new ExcluirTemplateWhatsAppUseCase(canal, eventos)
                        .executar("meta-1", ""))
                .isInstanceOf(PedidoDeTemplateInvalidoException.class);
    }
}
