package com.synapse.crm.core.interfaces.internal;

import java.io.IOException;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.synapse.crm.core.application.lead.foto.AtualizarFotoDoLeadUseCase;
import com.synapse.crm.core.application.lead.foto.FotoDeLeadExcedeuLimiteException;
import com.synapse.crm.core.application.lead.foto.FotoDeLeadInvalidaException;
import com.synapse.crm.core.application.lead.foto.ResultadoDaFotoDoLead;
import com.synapse.crm.core.application.tag.LeadDaAutomacaoNaoEncontradoException;
import com.synapse.crm.sharedkernel.identidade.ContextoDeServico;

/**
 * Foto de perfil do lead entregue pela integracao externa (E97).
 *
 * <p>A Meta nao entrega a foto do contato; quem coleta e envia e a integracao (n8n + UAZAPI). O CRM
 * so recebe — RN-CRM-07: ele configura a automacao, nao a executa. Nao ha busca de foto, nem
 * agendamento, nem webhook de volta.
 *
 * <p>A chave e o UUID do lead: o lead existe ou nao existe. Nao ha {@code Idempotency-Key} nem
 * {@code sourceUpdatedAt} porque nao ha o que eles resolveriam — o hash do arquivo ja da
 * idempotencia, e ha uma unica fonte escrevendo, entao "chegou fora de ordem" nao e caso real aqui.
 */
@RestController
@RequestMapping("/internal/v1")
@Tag(
        name = "Foto do lead",
        description = "Recebimento da foto de perfil coletada pela integração externa; o CRM reprocessa e guarda.")
@SecurityRequirement(name = "synapseToken")
class FotoDoLeadInternalController {

    private final AtualizarFotoDoLeadUseCase foto;

    FotoDoLeadInternalController(AtualizarFotoDoLeadUseCase foto) {
        this.foto = foto;
    }

    @Operation(
            summary = "Enviar foto de perfil do lead",
            description = "Recebe os bytes ORIGINAIS (JPEG, PNG ou WebP). O CRM valida, corta, redimensiona e reencoda em PNG — não converta, não redimensione e não calcule hash do seu lado. Reenvio do mesmo arquivo responde INALTERADA sem escrever nada.",
            responses = {
                @ApiResponse(responseCode = "200", description = "Foto gravada (ATUALIZADA) ou reconhecida como igual à atual (INALTERADA)."),
                @ApiResponse(responseCode = "401", description = "X-Synapse-Token ausente ou inválido."),
                @ApiResponse(responseCode = "404", description = "Lead inexistente."),
                @ApiResponse(responseCode = "413", description = "Arquivo acima do limite configurado."),
                @ApiResponse(responseCode = "422", description = "Não é JPEG/PNG/WebP, ou conteúdo de imagem inválido.")
            })
    @PostMapping(value = "/leads/{id}/foto", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    FotoDoLeadResposta enviar(
            @Parameter(description = "Identificador do lead.", required = true) @PathVariable UUID id,
            @RequestPart("arquivo") MultipartFile arquivo) {
        return ContextoDeServico.buscarComo(
                "enviar-foto-do-lead",
                () -> new FotoDoLeadResposta(id, foto.executar(id, lerBytes(arquivo))));
    }

    @Operation(
            summary = "Remover foto de perfil do lead",
            description = "Idempotente: um lead que já está sem foto também responde REMOVIDA. Não repita esta chamada em 404.",
            responses = {
                @ApiResponse(responseCode = "200", description = "Foto removida, ou já não existia."),
                @ApiResponse(responseCode = "401", description = "X-Synapse-Token ausente ou inválido."),
                @ApiResponse(responseCode = "404", description = "Lead inexistente.")
            })
    @DeleteMapping("/leads/{id}/foto")
    FotoDoLeadResposta remover(
            @Parameter(description = "Identificador do lead.", required = true) @PathVariable UUID id) {
        return new FotoDoLeadResposta(
                id, ContextoDeServico.buscarComo("remover-foto-do-lead", () -> foto.remover(id)));
    }

    /** Falha de leitura do multipart e problema de transporte, nao de conteudo: 400, nao 422. */
    private static byte[] lerBytes(MultipartFile arquivo) {
        try {
            return arquivo.getBytes();
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "falha ao ler a parte 'arquivo'");
        }
    }

    @ExceptionHandler(LeadDaAutomacaoNaoEncontradoException.class)
    ProblemDetail leadNaoEncontrado(LeadDaAutomacaoNaoEncontradoException erro) {
        return problema(HttpStatus.NOT_FOUND, "Lead nao encontrado", erro.getMessage());
    }

    @ExceptionHandler(FotoDeLeadExcedeuLimiteException.class)
    ProblemDetail fotoGrande(FotoDeLeadExcedeuLimiteException erro) {
        return problema(HttpStatus.PAYLOAD_TOO_LARGE, "Foto excede o limite", erro.getMessage());
    }

    @ExceptionHandler(FotoDeLeadInvalidaException.class)
    ProblemDetail fotoInvalida(FotoDeLeadInvalidaException erro) {
        return problema(HttpStatus.UNPROCESSABLE_ENTITY, "Foto invalida", erro.getMessage());
    }

    private static ProblemDetail problema(HttpStatus status, String titulo, String detalhe) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(status, detalhe);
        problema.setTitle(titulo);
        return problema;
    }

    record FotoDoLeadResposta(
            @Schema(description = "Identificador do lead que recebeu a operação.") UUID leadId,
            @Schema(description = "ATUALIZADA quando a foto mudou; INALTERADA quando o arquivo é igual ao já guardado; REMOVIDA no DELETE.")
                    ResultadoDaFotoDoLead status) {}
}
