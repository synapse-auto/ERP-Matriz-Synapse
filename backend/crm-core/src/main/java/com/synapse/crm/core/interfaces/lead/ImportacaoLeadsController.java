package com.synapse.crm.core.interfaces.lead;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import jakarta.validation.constraints.NotNull;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.synapse.crm.core.application.lead.importacao.ImportarLeadsCsvUseCase;
/** Preview e confirmacao da importacao de leads, somente para gestao. */
@RestController
@RequestMapping("/api/v1/leads/importacao")
@Tag(name = "Importacao de leads", description = "Importacao em lote com preview e confirmacao.")
@SecurityRequirement(name = "bearerAuth")
class ImportacaoLeadsController {

    private final ImportarLeadsCsvUseCase importacao;

    ImportacaoLeadsController(ImportarLeadsCsvUseCase importacao) {
        this.importacao = importacao;
    }

    @Operation(
            summary = "Visualizar importacao de leads",
            description = "Valida o CSV e devolve contagens sem gravar nada.",
            responses = {
                @ApiResponse(responseCode = "200", description = "Preview calculado."),
                @ApiResponse(responseCode = "400", description = "CSV invalido."),
                @ApiResponse(responseCode = "403", description = "Somente gestao pode importar.")
            })
    @PostMapping(value = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    PreviewResposta preview(
            @Parameter(description = "Arquivo CSV com nome e telefone obrigatorios.", required = true)
                    @RequestPart("arquivo") @NotNull MultipartFile arquivo) {
        try {
            return PreviewResposta.de(importacao.preview(
                    new InputStreamReader(arquivo.getInputStream(), StandardCharsets.UTF_8)));
        } catch (IOException e) {
            throw new CsvInvalidoException("nao foi possivel ler o arquivo CSV");
        }
    }

    @Operation(
            summary = "Confirmar importacao de leads",
            description = "Valida novamente e insere os novos telefones; existentes sao preservados.",
            responses = {
                @ApiResponse(responseCode = "200", description = "Importacao aplicada."),
                @ApiResponse(responseCode = "400", description = "CSV invalido."),
                @ApiResponse(responseCode = "403", description = "Somente gestao pode importar.")
            })
    @PostMapping(value = "/confirmar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    PreviewResposta confirmar(
            @Parameter(description = "Mesmo arquivo apresentado no preview.", required = true)
                    @RequestPart("arquivo") @NotNull MultipartFile arquivo) {
        try {
            return PreviewResposta.de(importacao.confirmar(
                    new InputStreamReader(arquivo.getInputStream(), StandardCharsets.UTF_8)));
        } catch (IOException e) {
            throw new CsvInvalidoException("nao foi possivel ler o arquivo CSV");
        }
    }

    @ExceptionHandler({CsvInvalidoException.class, IllegalArgumentException.class})
    ProblemDetail csvInvalido(RuntimeException erro) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, erro.getMessage());
        problema.setTitle("CSV invalido");
        return problema;
    }

    record PreviewResposta(
            @Schema(description = "Linhas de dados encontradas.") int totalDeLinhas,
            @Schema(description = "Linhas validas e novas, prontas para inserir.") int validas,
            @Schema(description = "Linhas cujo telefone ja existia e foi preservado.") int jaExistiam,
            @Schema(description = "Linhas recusadas com linha e motivo.") List<LinhaRecusadaResposta> recusadas) {

        static PreviewResposta de(ImportarLeadsCsvUseCase.Resultado resultado) {
            return new PreviewResposta(
                    resultado.totalDeLinhas(),
                    resultado.validas(),
                    resultado.jaExistiam(),
                    resultado.recusados().stream()
                            .map(linha -> new LinhaRecusadaResposta(linha.linha(), linha.motivo()))
                            .toList());
        }
    }

    record LinhaRecusadaResposta(int linha, String motivo) {}

    static final class CsvInvalidoException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        CsvInvalidoException(String mensagem) {
            super(mensagem);
        }
    }
}
