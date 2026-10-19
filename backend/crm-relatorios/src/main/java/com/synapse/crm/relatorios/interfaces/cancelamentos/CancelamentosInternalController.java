package com.synapse.crm.relatorios.interfaces.cancelamentos;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.synapse.crm.relatorios.application.cancelamentos.*;
import com.synapse.crm.relatorios.domain.cancelamentos.*;
import com.synapse.crm.sharedkernel.identidade.ContextoDeServico;

@RestController
@RequestMapping("/internal/v1/cancelamentos")
@Tag(name="Cancelamentos internos",description="Ingestao generica de cancelamentos de agendamento.")
@SecurityRequirement(name="synapseToken")
class CancelamentosInternalController {
    private final RegistrarCancelamentoUseCase registrar;
    CancelamentosInternalController(RegistrarCancelamentoUseCase registrar) { this.registrar=registrar; }

    @PostMapping
    @Operation(summary="Registrar evento de cancelamento",
        description="Exclusivo de servico via X-Synapse-Token. Idempotency-Key estavel por origem. "
            + "Nao cancela na origem, nao envia mensagens e nao altera o atendimento. Datas ISO 8601 com offset; "
            + "campos desconhecidos recusados. Vinculo exige par canonico leadId/atendimentoId ou ambos ausentes.",
        responses={@ApiResponse(responseCode="201",description="Registro persistido."),
            @ApiResponse(responseCode="200",description="Replay identico; retorna o registro original."),
            @ApiResponse(responseCode="400",description="Contrato, datas, limites ou vinculo invalido."),
            @ApiResponse(responseCode="401",description="Token de servico ausente/invalido; JWT nao substitui o token."),
            @ApiResponse(responseCode="409",description="Evento/chave reutilizados com conteudo incompatível.")})
    ResponseEntity<GravacaoDeCancelamentos.Resultado> registrar(@RequestHeader("Idempotency-Key") String chave,
            @RequestBody Requisicao r) {
        var evento = new NovoCancelamento(r.origem(),r.eventoExternoId(),r.agendamentoExternoId(),r.leadId(),
            r.atendimentoId(),r.agendadoPara()==null?null:r.agendadoPara().toInstant(),
            r.canceladoEm()==null?null:r.canceladoEm().toInstant(),r.contatoNome(),r.profissionalNome(),r.tipoAgendamento(),r.descricao());
        var resultado = ContextoDeServico.buscarComo("registrar-cancelamento-integracao",() -> registrar.executar(evento,chave));
        return ResponseEntity.status(resultado.repetido()?HttpStatus.OK:HttpStatus.CREATED).body(resultado);
    }
    record Requisicao(String origem, String eventoExternoId, String agendamentoExternoId,
            UUID leadId, UUID atendimentoId, OffsetDateTime agendadoPara, OffsetDateTime canceladoEm,
            String contatoNome, String profissionalNome, String tipoAgendamento, String descricao) {
        @JsonAnySetter public void campoDesconhecido(String chave, Object valor) {
            throw new RegistroCancelamentoInvalidoException();
        }
    }
    @ExceptionHandler({RegistroCancelamentoInvalidoException.class,
            org.springframework.http.converter.HttpMessageNotReadableException.class,
            org.springframework.web.bind.MissingRequestHeaderException.class})
    ProblemDetail invalido() {
        var erro=ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,"Confira os campos, limites e vinculos do registro.");
        erro.setProperty("codigo","REGISTRO_CANCELAMENTO_INVALIDO"); return erro;
    }
    @ExceptionHandler(ConflitoDeCancelamentoException.class)
    ProblemDetail conflito() {
        var erro=ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,"Evento ou chave ja recebido com dados diferentes.");
        erro.setProperty("codigo","CONFLITO_CANCELAMENTO"); return erro;
    }
}
