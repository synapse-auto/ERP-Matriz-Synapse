package com.synapse.crm.core.interfaces.lead;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.synapse.crm.core.application.campocustomizado.CampoCustomizadoRepositorio;
import com.synapse.crm.core.application.lead.exportacao.ExportarLeadsCsvUseCase;
import com.synapse.crm.core.domain.filtro.CriterioSimples;
import com.synapse.crm.core.domain.filtro.FiltroDeLeads;
import com.synapse.crm.sharedkernel.identidade.ContextoDeAgenda;

/** Exportacao da Agenda com o filtro modular serializado na query string. */
@RestController
@RequestMapping("/api/v1/leads")
@Tag(name = "Exportacao de leads", description = "Download CSV da Agenda.")
@SecurityRequirement(name = "bearerAuth")
class ExportacaoLeadsController {

    private final ExportarLeadsCsvUseCase exportar;
    private final CampoCustomizadoRepositorio camposCustomizados;
    private final ObjectMapper json;
    private final String codigoDaInstancia;

    ExportacaoLeadsController(
            ExportarLeadsCsvUseCase exportar,
            CampoCustomizadoRepositorio camposCustomizados,
            ObjectMapper json,
            @Value("${synapse.tenant.codigo}") String codigoDaInstancia) {
        this.exportar = exportar;
        this.camposCustomizados = camposCustomizados;
        this.json = json;
        this.codigoDaInstancia = codigoDaInstancia;
    }

    @Operation(
            summary = "Exportar leads em CSV",
            description = "Baixa os leads visiveis que correspondem ao criterio modular informado.",
            responses = {
                @ApiResponse(responseCode = "200", description = "Arquivo CSV gerado."),
                @ApiResponse(responseCode = "400", description = "Criterio invalido."),
                @ApiResponse(responseCode = "403", description = "Somente gestao pode exportar.")
            })
    @GetMapping(value = "/exportar", produces = "text/csv")
    ResponseEntity<byte[]> exportar(
            @Parameter(description = "No de criterio JSON da Agenda, codificado na URL.")
                    @RequestParam(required = false) String criterio) {
        FiltroDeLeads filtro = converter(criterio);
        byte[] csv = ContextoDeAgenda.buscarComo(() -> exportar.executar(filtro));
        String nome = "leads-" + codigoDaInstancia.replaceAll("[^A-Za-z0-9_-]", "-") + "-"
                + LocalDate.now() + ".csv";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType("text", "csv", StandardCharsets.UTF_8));
        headers.setContentDisposition(ContentDisposition.attachment().filename(nome, StandardCharsets.UTF_8).build());
        return ResponseEntity.ok().headers(headers).body(csv);
    }

    private FiltroDeLeads converter(String criterio) {
        if (criterio == null || criterio.isBlank()) {
            return new FiltroDeLeads(CriterioSimples.deTextos("criadoEm", "PREENCHIDO", java.util.List.of()));
        }
        try {
            FiltroDeLeadsController.CriterioRequisicao no =
                    json.readValue(criterio, FiltroDeLeadsController.CriterioRequisicao.class);
            return new FiltroDeLeads(no.paraDominio(1, camposCustomizados));
        } catch (JsonProcessingException | RuntimeException e) {
            throw new IllegalArgumentException("criterio de exportacao invalido", e);
        }
    }
}
