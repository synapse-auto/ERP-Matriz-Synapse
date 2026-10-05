package com.synapse.crm.atendimento.interfaces;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.synapse.crm.atendimento.application.ChaveIdempotenciaReutilizadaException;
import com.synapse.crm.atendimento.application.IdempotencyKeyInvalidaException;
import com.synapse.crm.atendimento.application.RecursoDeAtendimentoIndisponivelException;
import com.synapse.crm.atendimento.application.encaminhamentodochat.BuscarDestinosDoEncaminhamentoUseCase;
import com.synapse.crm.atendimento.application.encaminhamentodochat.ConteudoDoChatNaoEnviavelAoClienteException;
import com.synapse.crm.atendimento.application.encaminhamentodochat.DestinoDoEncaminhamento;
import com.synapse.crm.atendimento.application.encaminhamentodochat.EncaminhamentoDoChatParaCliente;
import com.synapse.crm.atendimento.application.encaminhamentodochat.EncaminhamentoDoChatRepositorio.EncaminhamentoComStatus;
import com.synapse.crm.atendimento.application.encaminhamentodochat.EncaminharDoChatInternoParaClienteUseCase;
import com.synapse.crm.atendimento.application.encaminhamentodochat.ListarEncaminhamentosDoChatUseCase;
import com.synapse.crm.atendimento.application.encaminhamentodochat.MotivoDeBloqueio;
import com.synapse.crm.atendimento.application.encaminhamentodochat.PreVisualizarEncaminhamentoDoChatUseCase;
import com.synapse.crm.atendimento.application.encaminhamentodochat.PreviaDoEncaminhamento;
import com.synapse.crm.atendimento.domain.atendimento.AtendimentoJaFinalizadoException;
import com.synapse.crm.atendimento.domain.canal.ForaDaJanelaException;
import com.synapse.crm.equipe.application.chat.ChatSemAcessoException;
import com.synapse.crm.equipe.application.chat.MensagemDoChatNaoEncaminhavelException;

/**
 * Encaminhar do Chat Interno para o cliente de um atendimento (docs/61).
 *
 * <p>O navegador diz <em>qual mensagem</em> e <em>qual atendimento</em>. Cliente, telefone, lead, canal,
 * instância e conteúdo saem do backend, e as duas autorizações (participar da conversa interna;
 * alcançar e poder responder no atendimento) são conferidas lá. O envio devolve {@code 202}: a mensagem
 * já está gravada e na outbox, e a entrega ao provedor é assíncrona.
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Encaminhar do Chat Interno ao cliente", description = "Prévia, envio e acompanhamento do encaminhamento.")
@SecurityRequirement(name = "bearerAuth")
class EncaminhamentoDoChatInternoController {

    private final PreVisualizarEncaminhamentoDoChatUseCase previa;
    private final EncaminharDoChatInternoParaClienteUseCase encaminhar;
    private final ListarEncaminhamentosDoChatUseCase listar;
    private final BuscarDestinosDoEncaminhamentoUseCase destinos;

    EncaminhamentoDoChatInternoController(
            PreVisualizarEncaminhamentoDoChatUseCase previa,
            EncaminharDoChatInternoParaClienteUseCase encaminhar,
            ListarEncaminhamentosDoChatUseCase listar,
            BuscarDestinosDoEncaminhamentoUseCase destinos) {
        this.previa = previa;
        this.encaminhar = encaminhar;
        this.listar = listar;
        this.destinos = destinos;
    }

    @Operation(
            summary = "Buscar o atendimento de destino do encaminhamento",
            description = "Atendimentos abertos que o usuário alcança (RN-CRM-01), do mais recente ao mais antigo, filtrados por nome ou por dígitos do telefone. Telefone sempre mascarado; no máximo 20 resultados.",
            responses = {
                @ApiResponse(responseCode = "200", description = "Destinos possíveis; lista vazia quando nada corresponde."),
                @ApiResponse(responseCode = "403", description = "O usuário não pode responder em atendimentos.")
            })
    @GetMapping("/atendimentos/encaminhamento-do-chat-interno/destinos")
    List<DestinoDoEncaminhamento> destinos(@RequestParam(required = false) String busca) {
        return destinos.executar(busca);
    }

    @Operation(
            summary = "Prévia do encaminhamento ao cliente",
            description = "Mostra cliente, telefone mascarado, atendimento, conteúdo e o efeito sobre a responsabilidade (assume o lead, mantém o responsável e convida, ou nada muda). Não grava nada.",
            responses = {
                @ApiResponse(responseCode = "200", description = "Prévia; podeEnviar=false traz o motivo em bloqueio."),
                @ApiResponse(responseCode = "403", description = "O usuário não participa da conversa interna."),
                @ApiResponse(responseCode = "404", description = "Atendimento inexistente ou fora do alcance do usuário."),
                @ApiResponse(responseCode = "422", description = "A mensagem interna não é conteúdo para cliente.")
            })
    @GetMapping("/atendimentos/{atendimentoId}/encaminhamento-do-chat-interno/previa")
    PreviaDoEncaminhamento previa(
            @PathVariable UUID atendimentoId, @RequestParam UUID conversaId, @RequestParam UUID mensagemId) {
        return previa.executar(conversaId, mensagemId, atendimentoId);
    }

    @Operation(
            summary = "Encaminhar mensagem do Chat Interno ao cliente",
            description = "Envia pelo fluxo oficial (outbox, janela de 24h, adaptador do provedor ativo). Sem responsável, quem encaminha assume o lead (RN-CRM-06); com responsável, ele continua e quem encaminhou recebe convite para participar, sem convite repetido. Idempotency-Key obrigatório: repetir devolve o mesmo encaminhamento.",
            responses = {
                @ApiResponse(responseCode = "202", description = "Mensagem aceita para entrega."),
                @ApiResponse(responseCode = "400", description = "Idempotency-Key ausente ou inválido."),
                @ApiResponse(responseCode = "403", description = "O usuário não participa da conversa interna ou não pode responder."),
                @ApiResponse(responseCode = "404", description = "Atendimento inexistente ou fora do alcance do usuário."),
                @ApiResponse(responseCode = "409", description = "Atendimento finalizado ou chave usada em outra operação."),
                @ApiResponse(responseCode = "422", description = "Conteúdo não encaminhável, arquivo inválido ou fora da janela de 24h.")
            })
    @PostMapping("/atendimentos/{atendimentoId}/encaminhamento-do-chat-interno")
    @ResponseStatus(HttpStatus.ACCEPTED)
    EncaminhamentoResposta encaminhar(
            @PathVariable UUID atendimentoId,
            @Valid @RequestBody EncaminhamentoRequisicao requisicao,
            @Parameter(description = "Gerada por clique; repetir a mesma chave não reenvia.")
                    @RequestHeader(name = "Idempotency-Key", required = false) String chaveIdempotencia) {
        return EncaminhamentoResposta.de(encaminhar.executar(
                requisicao.conversaId(), requisicao.mensagemId(), atendimentoId, chaveIdempotencia));
    }

    @Operation(
            summary = "Encaminhamentos feitos pelo usuário a partir de uma mensagem",
            description = "Com o estado atual da entrega da mensagem externa, para a tela acompanhar sem recarregar.")
    @GetMapping("/chat-interno/conversas/{conversaId}/mensagens/{mensagemId}/encaminhamentos-ao-cliente")
    List<EncaminhamentoResposta> encaminhamentos(@PathVariable UUID conversaId, @PathVariable UUID mensagemId) {
        return listar.executar(mensagemId).stream()
                .filter(item -> item.encaminhamento().conversaId().equals(conversaId))
                .map(EncaminhamentoResposta::de)
                .toList();
    }

    record EncaminhamentoRequisicao(
            @NotNull @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID conversaId,
            @NotNull @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID mensagemId) {}

    record EncaminhamentoResposta(
            UUID id,
            UUID atendimentoId,
            UUID mensagemInternaId,
            UUID mensagemExternaId,
            Instant mensagemExternaEnviadaEm,
            String tipo,
            String statusEntrega,
            String erroEntrega,
            boolean transferiuOLead,
            boolean conviteCriado,
            boolean reutilizado,
            Instant criadoEm) {

        static EncaminhamentoResposta de(EncaminhamentoDoChatParaCliente resultado) {
            return new EncaminhamentoResposta(
                    resultado.id(),
                    resultado.atendimentoId(),
                    resultado.mensagemInternaId(),
                    resultado.mensagemExternaId(),
                    resultado.mensagemExternaEnviadaEm(),
                    resultado.tipo(),
                    resultado.statusEntrega(),
                    null,
                    resultado.transferiuOLead(),
                    resultado.conviteCriado(),
                    resultado.reutilizado(),
                    null);
        }

        static EncaminhamentoResposta de(EncaminhamentoComStatus item) {
            var registro = item.encaminhamento();
            return new EncaminhamentoResposta(
                    registro.id(),
                    registro.atendimentoId(),
                    registro.mensagemInternaId(),
                    registro.mensagemExternaId(),
                    registro.mensagemExternaEnviadaEm(),
                    registro.tipo(),
                    item.status().name(),
                    item.erroEntrega(),
                    registro.transferiuOLead(),
                    registro.conviteCriado(),
                    false,
                    registro.criadoEm());
        }
    }

    @ExceptionHandler(ChatSemAcessoException.class)
    ProblemDetail aoNaoParticiparDaConversa(ChatSemAcessoException e) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, e.getMessage());
        problema.setTitle("Sem acesso a conversa");
        return problema;
    }

    @ExceptionHandler(RecursoDeAtendimentoIndisponivelException.class)
    ProblemDetail aoNaoEncontrar(RecursoDeAtendimentoIndisponivelException e) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
        problema.setTitle("Nao encontrado");
        return problema;
    }

    @ExceptionHandler(MensagemDoChatNaoEncaminhavelException.class)
    ProblemDetail aoRecusarMensagem(MensagemDoChatNaoEncaminhavelException e) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
        problema.setTitle("Mensagem nao encaminhavel");
        problema.setProperty("motivo", e.motivo().name());
        return problema;
    }

    @ExceptionHandler(ConteudoDoChatNaoEnviavelAoClienteException.class)
    ProblemDetail aoRecusarConteudo(ConteudoDoChatNaoEnviavelAoClienteException e) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
        problema.setTitle("Conteudo nao enviavel ao cliente");
        problema.setProperty("motivo", e.motivo().name());
        return problema;
    }

    @ExceptionHandler(ForaDaJanelaException.class)
    ProblemDetail aoEstarForaDaJanela(ForaDaJanelaException e) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
        problema.setTitle("Fora da janela de 24 horas");
        problema.setProperty("motivo", MotivoDeBloqueio.FORA_DA_JANELA.name());
        return problema;
    }

    @ExceptionHandler(AtendimentoJaFinalizadoException.class)
    ProblemDetail aoJaEstarFinalizado(AtendimentoJaFinalizadoException e) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
        problema.setTitle("Atendimento ja finalizado");
        problema.setProperty("motivo", MotivoDeBloqueio.ATENDIMENTO_FINALIZADO.name());
        return problema;
    }

    @ExceptionHandler(ChaveIdempotenciaReutilizadaException.class)
    ProblemDetail aoRecusarChaveReutilizada(ChaveIdempotenciaReutilizadaException e) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
        problema.setTitle("Idempotency-Key reutilizada");
        return problema;
    }

    @ExceptionHandler({IdempotencyKeyInvalidaException.class, IllegalArgumentException.class})
    ProblemDetail aoRecusarChave(RuntimeException e) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
        problema.setTitle("Idempotency-Key invalida");
        return problema;
    }
}
