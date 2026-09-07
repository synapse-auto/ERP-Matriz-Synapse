package com.synapse.crm.core.interfaces.lead;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import com.synapse.crm.core.application.campocustomizado.CampoCustomizadoRepositorio;
import com.synapse.crm.core.application.lead.exportacao.ExportarLeadsCsvUseCase;

class ExportacaoLeadsControllerTest {

    private final ExportarLeadsCsvUseCase exportar = mock(ExportarLeadsCsvUseCase.class);
    private final CampoCustomizadoRepositorio campos = mock(CampoCustomizadoRepositorio.class);
    private final ExportacaoLeadsController controller =
            new ExportacaoLeadsController(exportar, campos, new ObjectMapper(), "teste");

    @Test
    void criterioDaAgendaEConvertidoAntesDaExportacao() {
        when(exportar.executar(any())).thenReturn("nome,telefone\n".getBytes(StandardCharsets.UTF_8));

        var resposta = controller.exportar("{\"tipo\":\"SIMPLES\",\"campo\":\"nome\","
                + "\"operador\":\"CONTEM\",\"valor\":\"Maria\"}");

        assertThat(resposta.getStatusCode().value()).isEqualTo(200);
        assertThat(resposta.getBody()).isEqualTo("nome,telefone\n".getBytes(StandardCharsets.UTF_8));
        verify(exportar).executar(any());
    }
}
