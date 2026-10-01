package com.synapse.crm.atendimento.interfaces.internal;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
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
import com.synapse.crm.atendimento.application.origem.OrigemDeMensagemAutomaticaRepositorio.TotalPorOrigem;
import com.synapse.crm.atendimento.application.origem.TotaisPorOrigemUseCase;
import com.synapse.crm.atendimento.application.origem.TotaisPorOrigemUseCase.PeriodoInvalidoException;
import com.synapse.crm.atendimento.application.proativo.ReservaDeEnvioProativoRepositorio.ReservaProativa;
import com.synapse.crm.atendimento.application.proativo.ReservarEnvioProativoUseCase;
import com.synapse.crm.atendimento.application.proativo.ReservarEnvioProativoUseCase.ChaveProativaReutilizadaException;
import com.synapse.crm.atendimento.application.proativo.ReservarEnvioProativoUseCase.Decisao;
import com.synapse.crm.atendimento.application.proativo.ReservarEnvioProativoUseCase.PedidoDeEnvioProativoInvalidoException;
import com.synapse.crm.sharedkernel.identidade.ContextoDeServico;

/** Reserva com politica de frequencia para mensagens proativas e visao por origem (E219). */
@RestController
@RequestMapping("/internal/v1")
@Tag(name = "Envio proativo da Automação", description = "Reserva com política de frequência e origem das mensagens automáticas.")
@SecurityRequirement(name = "synapseToken")
class EnvioProativoInternalController {

    private final ReservarEnvioProativoUseCase reservar;
    private final TotaisPorOrigemUseCase totais;

    EnvioProativoInternalController(ReservarEnvioProativoUseCase reservar, TotaisPorOrigemUseCase totais) {
        this.reservar = reservar;
        this.totais = totais;
    }

    @Operation(
            summary = "Reservar um envio proativo antes de chamar o provedor",
            description = "Para follow-up, fidelização, festivas, aniversário, avaliação, lembrete e outras proativas. Chame ANTES de "
                    + "enviar; envie somente com podeEnviar=true e depois informe a mesma chave em chaveDeEnvio de "
                    + "POST /atendimentos/{id}/mensagens-enviadas, que fecha a reserva na mesma transação do registro. "
                    + "podeEnviar=false traz o motivo: CHAVE_JA_USADA, OCORRENCIA_JA_REGISTRADA, AUTOMACAO_PROATIVA_DESLIGADA, "
                    + "TIPO_DESLIGADO, COOLDOWN ou TETO_DIARIO. Resposta a mensagem do lead (RESPOSTA_IA) NÃO passa por aqui.",
            responses = {
                @ApiResponse(responseCode = "201", description = "Reserva concedida: pode enviar."),
                @ApiResponse(responseCode = "200", description = "NÃO envie; o motivo está no corpo."),
                @ApiResponse(responseCode = "400", description = "Campo ausente/longo demais ou tipo não proativo."),
                @ApiResponse(responseCode = "401", description = "X-Synapse-Token ausente ou inválido."),
                @ApiResponse(responseCode = "404", description = "Lead inexistente."),
                @ApiResponse(responseCode = "409", description = "Chave já usada com outro lead, tipo, regra ou ocorrência.")
            })
    @PostMapping("/leads/{leadId}/envios-proativos/reservas")
    ResponseEntity<DecisaoResposta> reservar(
            @Parameter(description = "Identificador do lead.", required = true) @PathVariable UUID leadId,
            @RequestBody ReservaProativaRequisicao requisicao) {
        var pedido = new ReservarEnvioProativoUseCase.Pedido(
                requisicao.tipo(), requisicao.regraId(), requisicao.ocorrencia(), requisicao.chave(), requisicao.execucaoId());
        Decisao decisao =
                ContextoDeServico.buscarComo("reservar-envio-proativo", () -> reservar.reservar(leadId, pedido));
        return ResponseEntity.status(decisao.podeEnviar() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(DecisaoResposta.de(decisao));
    }

    @Operation(
            summary = "Reservas proativas sem resultado (conferência)",
            description = "Reservas ainda RESERVADO feitas antes do instante: o envio foi autorizado e o registro não voltou. "
                    + "O provedor pode ter entregue. Confira no provedor/execução; nunca reenvie automaticamente a partir "
                    + "desta lista. Até 100 itens, mais antigos primeiro.",
            responses = {
                @ApiResponse(responseCode = "200", description = "Lista, possivelmente vazia."),
                @ApiResponse(responseCode = "400", description = "Instante ausente ou inválido."),
                @ApiResponse(responseCode = "401", description = "X-Synapse-Token ausente ou inválido.")
            })
    @GetMapping("/envios-proativos/pendentes")
    List<ReservaProativaResposta> pendentes(
            @Parameter(description = "Lista só reservas feitas antes deste instante (UTC).", required = true)
                    @RequestParam Instant reservadosAntesDe) {
        return ContextoDeServico.buscarComo(
                        "listar-envios-proativos-pendentes", () -> reservar.pendentesAntesDe(reservadosAntesDe))
                .stream()
                .map(ReservaProativaResposta::de)
                .toList();
    }

    @Operation(
            summary = "Mensagens automáticas por origem e dia",
            description = "Somente leitura. Conta mensagens e leads distintos por dia (fuso da instância) e origem. "
                    + "SEM_ORIGEM_REGISTRADA = mensagens da IA sem registro de origem (anteriores à V85 ou caminho não "
                    + "coberto); NAO_INFORMADA = gravadas depois da V85 por um fluxo que não declarou a origem. Até 93 dias.",
            responses = {
                @ApiResponse(responseCode = "200", description = "Totais, possivelmente vazios."),
                @ApiResponse(responseCode = "400", description = "Período inválido ou maior que 93 dias."),
                @ApiResponse(responseCode = "401", description = "X-Synapse-Token ausente ou inválido.")
            })
    @GetMapping("/envios-automacao/resumo-por-origem")
    ResumoPorOrigemResposta resumoPorOrigem(
            @Parameter(description = "Primeiro dia (inclusive), AAAA-MM-DD.", required = true)
                    @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
            @Parameter(description = "Último dia (inclusive), AAAA-MM-DD.", required = true)
                    @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate) {
        var resultado = ContextoDeServico.buscarComo("resumo-mensagens-por-origem", () -> totais.calcular(de, ate));
        return new ResumoPorOrigemResposta(resultado.fuso(), resultado.linhas());
    }

    @ExceptionHandler({PedidoDeEnvioProativoInvalidoException.class, PeriodoInvalidoException.class})
    ProblemDetail aoReceberPedidoInvalido(RuntimeException erro) {
        return problema(HttpStatus.BAD_REQUEST, "Pedido invalido", erro.getMessage());
    }

    @ExceptionHandler(RecursoDeAtendimentoIndisponivelException.class)
    ProblemDetail aoNaoEncontrar(RecursoDeAtendimentoIndisponivelException erro) {
        return problema(HttpStatus.NOT_FOUND, "Lead inexistente", erro.getMessage());
    }

    @ExceptionHandler(ChaveProativaReutilizadaException.class)
    ProblemDetail aoReusarChave(ChaveProativaReutilizadaException erro) {
        return problema(HttpStatus.CONFLICT, "Chave reutilizada com outro conteudo", erro.getMessage());
    }

    private static ProblemDetail problema(HttpStatus status, String titulo, String detalhe) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(status, detalhe);
        problema.setTitle(titulo);
        return problema;
    }

    record ReservaProativaRequisicao(
            @Schema(description = "FOLLOW_UP, FIDELIZACAO, FESTIVA, ANIVERSARIO, AVALIACAO, LEMBRETE ou OUTRO.",
                            example = "FOLLOW_UP",
                            requiredMode = Schema.RequiredMode.REQUIRED)
                    String tipo,
            @Schema(description = "Id da regra que gerou o envio (regra_follow_up, mensagem_festiva...). Opcional, até 100.")
                    String regraId,
            @Schema(description = "Identifica a ocorrência: o que torna este envio único para o lead naquela regra "
                            + "(ex.: data de referência do follow-up, '2026-12-25' na festiva, '2026' no aniversário). Até 200.",
                            example = "2026-10-01",
                            requiredMode = Schema.RequiredMode.REQUIRED)
                    String ocorrencia,
            @Schema(description = "Chave estável do envio, até 200 caracteres. A mesma chave com outro lead/tipo/regra/"
                            + "ocorrência dá 409.",
                            requiredMode = Schema.RequiredMode.REQUIRED)
                    String chave,
            @Schema(description = "Id da execução do n8n. Opcional, até 200; não entra na comparação de payload.")
                    String execucaoId) {}

    record DecisaoResposta(
            @Schema(description = "true: pode enviar agora. false: NÃO envie.") boolean podeEnviar,
            @Schema(description = "Motivo quando podeEnviar=false.") String motivo,
            @Schema(description = "Reserva concedida, ou a existente em CHAVE_JA_USADA/OCORRENCIA_JA_REGISTRADA.")
                    ReservaProativaResposta reserva,
            @Schema(description = "Quando o bloqueio de COOLDOWN/TETO_DIARIO deixa de valer.") Instant liberadoApos) {

        static DecisaoResposta de(Decisao decisao) {
            return new DecisaoResposta(
                    decisao.podeEnviar(),
                    decisao.motivo() == null ? null : decisao.motivo().name(),
                    decisao.reserva() == null ? null : ReservaProativaResposta.de(decisao.reserva()),
                    decisao.liberadoApos());
        }
    }

    record ReservaProativaResposta(
            String chave,
            UUID leadId,
            String tipo,
            String regraId,
            String ocorrencia,
            String execucaoId,
            String estado,
            UUID mensagemId,
            String wamidSaida,
            Instant reservadoEm,
            Instant enviadoEm) {

        static ReservaProativaResposta de(ReservaProativa reserva) {
            return new ReservaProativaResposta(
                    reserva.chave(),
                    reserva.leadId(),
                    reserva.tipo().name(),
                    reserva.regraId(),
                    reserva.ocorrencia(),
                    reserva.execucaoId(),
                    reserva.estado().name(),
                    reserva.mensagemId(),
                    reserva.wamidSaida(),
                    reserva.reservadoEm(),
                    reserva.enviadoEm());
        }
    }

    record ResumoPorOrigemResposta(
            @Schema(description = "Fuso em que os dias foram contados.") String fuso, List<TotalPorOrigem> linhas) {}
}
