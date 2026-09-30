package com.synapse.crm.atendimento.interfaces.internal;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.synapse.crm.atendimento.application.RecursoDeAtendimentoIndisponivelException;
import com.synapse.crm.atendimento.application.ReservaDeEnvioDaAutomacaoRepositorio.Reserva;
import com.synapse.crm.atendimento.application.internal.ReservarEnvioDaAutomacaoUseCase;
import com.synapse.crm.atendimento.application.internal.ReservarEnvioDaAutomacaoUseCase.AtendimentoFinalizadoException;
import com.synapse.crm.atendimento.application.internal.ReservarEnvioDaAutomacaoUseCase.ChaveDeEnvioInvalidaException;
import com.synapse.crm.atendimento.application.internal.ReservarEnvioDaAutomacaoUseCase.ChaveDeOutroAtendimentoException;
import com.synapse.crm.sharedkernel.identidade.ContextoDeServico;

/** Reserva de envio antes do provedor e conciliacao da janela ambigua (docs/50). */
@RestController
@RequestMapping("/internal/v1")
@Tag(name = "Envio único da Automação", description = "Reserva persistente antes de enviar ao provedor.")
@SecurityRequirement(name = "synapseToken")
class EnvioDaAutomacaoInternalController {

    private final ReservarEnvioDaAutomacaoUseCase reservar;

    EnvioDaAutomacaoInternalController(ReservarEnvioDaAutomacaoUseCase reservar) {
        this.reservar = reservar;
    }

    @Operation(
            summary = "Reservar um envio antes de chamar o provedor",
            description = "Chame ANTES de enviar ao provedor, com uma chave estável do evento (ex.: X-Synapse-Evento-Id + passo). "
                    + "Envie somente se novaReserva=true. Com a mesma chave, reexecuções e entregas simultâneas recebem "
                    + "novaReserva=false e NÃO devem enviar. Depois do envio, informe a mesma chave em chaveDeEnvio de "
                    + "POST /mensagens-enviadas. Uma reserva RESERVADO sem conclusão é resultado incerto: concilie, não reenvie.",
            responses = {
                @ApiResponse(responseCode = "201", description = "Reserva nova: pode enviar."),
                @ApiResponse(responseCode = "200", description = "Chave já reservada para este atendimento: NÃO envie."),
                @ApiResponse(responseCode = "400", description = "Chave ausente ou maior que 200 caracteres."),
                @ApiResponse(responseCode = "401", description = "X-Synapse-Token ausente ou inválido."),
                @ApiResponse(responseCode = "404", description = "Atendimento inexistente."),
                @ApiResponse(responseCode = "409", description = "Atendimento finalizado ou chave de outro atendimento.")
            })
    @PostMapping("/atendimentos/{id}/envios-automacao/reservas")
    ResponseEntity<ReservaResposta> reservar(
            @Parameter(description = "Identificador do atendimento.", required = true) @PathVariable UUID id,
            @RequestBody ReservaRequisicao requisicao) {
        var resultado = ContextoDeServico.buscarComo(
                "reservar-envio-automacao", () -> reservar.reservar(id, requisicao.chave()));
        return ResponseEntity.status(resultado.novaReserva() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(ReservaResposta.de(resultado.reserva(), resultado.novaReserva()));
    }

    @Operation(
            summary = "Reservas sem resultado (conciliação)",
            description = "Reservas ainda RESERVADO feitas antes do instante informado: o envio foi autorizado e o resultado "
                    + "não voltou ao CRM. O provedor pode ter entregue. Concilie consultando o provedor/execução; nunca reenvie "
                    + "automaticamente a partir desta lista. Até 100 itens, mais antigos primeiro.",
            responses = {
                @ApiResponse(responseCode = "200", description = "Lista, possivelmente vazia."),
                @ApiResponse(responseCode = "400", description = "Instante ausente ou inválido."),
                @ApiResponse(responseCode = "401", description = "X-Synapse-Token ausente ou inválido.")
            })
    @GetMapping("/envios-automacao/pendentes")
    List<ReservaResposta> pendentes(
            @Parameter(description = "Lista só reservas feitas antes deste instante (UTC).", required = true)
                    @RequestParam Instant reservadosAntesDe) {
        return ContextoDeServico.buscarComo(
                        "listar-envios-automacao-pendentes", () -> reservar.pendentesAntesDe(reservadosAntesDe))
                .stream()
                .map(reserva -> ReservaResposta.de(reserva, false))
                .toList();
    }

    @ExceptionHandler(ChaveDeEnvioInvalidaException.class)
    ProblemDetail aoReceberChaveInvalida(ChaveDeEnvioInvalidaException erro) {
        return problema(HttpStatus.BAD_REQUEST, "Chave de envio invalida", erro.getMessage());
    }

    @ExceptionHandler(RecursoDeAtendimentoIndisponivelException.class)
    ProblemDetail aoNaoEncontrar(RecursoDeAtendimentoIndisponivelException erro) {
        return problema(HttpStatus.NOT_FOUND, "Atendimento nao encontrado", erro.getMessage());
    }

    @ExceptionHandler(AtendimentoFinalizadoException.class)
    ProblemDetail aoEncontrarFinalizado(AtendimentoFinalizadoException erro) {
        return problema(HttpStatus.CONFLICT, "Atendimento finalizado", erro.getMessage());
    }

    @ExceptionHandler(ChaveDeOutroAtendimentoException.class)
    ProblemDetail aoReusarChave(ChaveDeOutroAtendimentoException erro) {
        return problema(HttpStatus.CONFLICT, "Chave de envio de outro atendimento", erro.getMessage());
    }

    private static ProblemDetail problema(HttpStatus status, String titulo, String detalhe) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(status, detalhe);
        problema.setTitle(titulo);
        return problema;
    }

    record ReservaRequisicao(
            @Schema(description = "Chave estável do envio, até 200 caracteres.", requiredMode = Schema.RequiredMode.REQUIRED)
                    String chave) {}

    record ReservaResposta(
            String chave,
            UUID atendimentoId,
            String estado,
            boolean novaReserva,
            String wamidSaida,
            Instant reservadoEm,
            Instant enviadoEm) {

        static ReservaResposta de(Reserva reserva, boolean novaReserva) {
            return new ReservaResposta(
                    reserva.chave(),
                    reserva.atendimentoId(),
                    reserva.estado().name(),
                    novaReserva,
                    reserva.wamidSaida(),
                    reserva.reservadoEm(),
                    reserva.enviadoEm());
        }
    }
}
