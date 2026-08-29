package com.synapse.crm.atendimento.interfaces;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.UnknownContentTypeException;

import com.synapse.crm.atendimento.application.template.CanalRecusouTemplateException;
import com.synapse.crm.atendimento.application.template.CriarTemplateWhatsAppUseCase;
import com.synapse.crm.atendimento.application.template.ListarTemplatesWhatsAppUseCase;
import com.synapse.crm.atendimento.domain.canal.CanalIndisponivelException;
import com.synapse.crm.atendimento.domain.canal.TemplateDoCanal;

class TemplateWhatsAppControllerTest {

    private ListarTemplatesWhatsAppUseCase listar;
    private CriarTemplateWhatsAppUseCase criar;
    private MockMvc mvc;

    @BeforeEach
    void configurar() {
        listar = mock(ListarTemplatesWhatsAppUseCase.class);
        criar = mock(CriarTemplateWhatsAppUseCase.class);
        mvc = MockMvcBuilders.standaloneSetup(new TemplateWhatsAppController(listar, criar)).build();
    }

    @Test
    void getSemWabaDevolve503Rfc7807() throws Exception {
        when(listar.executar())
                .thenThrow(new CanalIndisponivelException(
                        "WHATSAPP_CONTA_NEGOCIO nao configurada; informe o WABA ID para administrar templates"));

        mvc.perform(get("/api/v1/whatsapp/templates"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.detail").value(containsString("WHATSAPP_CONTA_NEGOCIO")));
    }

    @Test
    void postSemWabaDevolve503Rfc7807() throws Exception {
        when(criar.executar(any(), any(), any(), any()))
                .thenThrow(new CanalIndisponivelException(
                        "WHATSAPP_CONTA_NEGOCIO nao configurada; informe o WABA ID para administrar templates"));

        mvc.perform(post("/api/v1/whatsapp/templates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"nome":"retorno_orcamento","idioma":"pt_BR","categoria":"UTILIDADE","corpo":"Ola {{1}}"}
                                """))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").value(containsString("WHATSAPP_CONTA_NEGOCIO")));
    }

    @Test
    void recusaDeCriacaoDevolve422() throws Exception {
        when(criar.executar(any(), any(), any(), any()))
                .thenThrow(new CanalRecusouTemplateException("O exemplo nao pode ser um placeholder."));

        mvc.perform(post("/api/v1/whatsapp/templates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"nome":"retorno_orcamento","idioma":"pt_BR","categoria":"UTILIDADE","corpo":"Ola {{1}}"}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422));
    }

    @Test
    void unknownContentTypeNaoVira500() throws Exception {
        when(criar.executar(any(), any(), any(), any()))
                .thenThrow(new UnknownContentTypeException(
                        String.class,
                        MediaType.TEXT_PLAIN,
                        200,
                        "OK",
                        HttpHeaders.EMPTY,
                        "{\"id\":\"1\"}".getBytes(StandardCharsets.UTF_8)));

        mvc.perform(post("/api/v1/whatsapp/templates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"nome":"retorno_orcamento","idioma":"pt_BR","categoria":"UTILIDADE","corpo":"Ola {{1}}"}
                                """))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.detail").value(containsString("UnknownContentTypeException")));
    }

    @Test
    void timeoutNaoTraduzidoNaoPodeVazarComo500Silencioso() throws Exception {
        when(listar.executar()).thenThrow(new ResourceAccessException("read timed out"));

        mvc.perform(get("/api/v1/whatsapp/templates")).andExpect(status().isServiceUnavailable());
    }

    @Test
    void listaValidaDevolve200() throws Exception {
        when(listar.executar())
                .thenReturn(List.of(new TemplateDoCanal(
                        "boas_vindas",
                        "pt_BR",
                        TemplateDoCanal.Categoria.UTILIDADE,
                        TemplateDoCanal.Status.APROVADO,
                        "Ola {{1}}",
                        1)));

        mvc.perform(get("/api/v1/whatsapp/templates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nome").value("boas_vindas"));
    }
}
