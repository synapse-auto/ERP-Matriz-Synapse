package com.synapse.crm.core.interfaces.lead;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.synapse.crm.core.application.lead.importacao.ImportarLeadsCsvUseCase;
import com.synapse.crm.core.application.lead.importacao.PrepararImportacaoLeadsCsv;

class ImportacaoLeadsControllerTest {

    private final ImportarLeadsCsvUseCase importar = mock(ImportarLeadsCsvUseCase.class);
    private MockMvc mvc;

    @BeforeEach
    void configurar() {
        mvc = MockMvcBuilders.standaloneSetup(new ImportacaoLeadsController(importar)).build();
    }

    @Test
    void previewDevolveContagensERecusas() throws Exception {
        when(importar.preview(any())).thenReturn(new ImportarLeadsCsvUseCase.Resultado(
                3,
                1,
                1,
                List.of(new PrepararImportacaoLeadsCsv.LinhaRecusada(4, "nome vazio"))));

        mvc.perform(multipart("/api/v1/leads/importacao/preview")
                        .file(new MockMultipartFile("arquivo", "leads.csv", "text/csv", "nome,telefone".getBytes())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalDeLinhas").value(3))
                .andExpect(jsonPath("$.validas").value(1))
                .andExpect(jsonPath("$.jaExistiam").value(1))
                .andExpect(jsonPath("$.recusadas[0].linha").value(4));
    }
}
