package com.synapse.crm.atendimento.interfaces;

import java.io.IOException;
import java.time.Instant;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.synapse.crm.atendimento.application.EnviarMensagemUseCase;
import com.synapse.crm.atendimento.application.FinalizarAtendimentoUseCase;
import com.synapse.crm.atendimento.application.RecursoDeAtendimentoIndisponivelException;
import com.synapse.crm.atendimento.application.TransferirAtendimentoUseCase;
import com.synapse.crm.atendimento.application.midia.AnexoExcedeuLimiteException;
import com.synapse.crm.atendimento.application.midia.EnviarMidiaUseCase;
import com.synapse.crm.atendimento.application.midia.ResolverLeadDoAtendimentoUseCase;
import com.synapse.crm.atendimento.application.midia.TipoDeMidiaNaoPermitidoException;
import com.synapse.crm.atendimento.domain.atendimento.Atendimento;
import com.synapse.crm.atendimento.domain.atendimento.AtendimentoJaFinalizadoException;
import com.synapse.crm.atendimento.domain.canal.ForaDaJanelaException;
import com.synapse.crm.sharedkernel.identidade.UsuarioContext;

/**
 * Enviar, transferir e finalizar — as tres acoes que faltavam ter controller. Os casos de uso ja
 * existiam prontos em {@code application/}; este controller so os expoe.
 *
 * <p>Primeiro controller do modulo a injetar {@link UsuarioContext} diretamente: {@code
 * EnviarMensagemUseCase} resolve o remetente sozinho, mas {@code TransferirAtendimentoUseCase} e
 * {@code FinalizarAtendimentoUseCase} recebem "quem pediu" como parametro explicito — nao ha como
 * fugir disso aqui.
 */
@RestController
@RequestMapping("/api/v1/atendimentos")
@Tag(name = "Ações de atendimento", description = "Envio, transferência e finalização de conversas visíveis.")
@SecurityRequirement(name = "bearerAuth")
class AtendimentoAcoesController {

    private final EnviarMensagemUseCase enviar;
    private final EnviarMidiaUseCase enviarMidia;
    private final ResolverLeadDoAtendimentoUseCase resolverLead;
    private final TransferirAtendimentoUseCase transferir;
    private final FinalizarAtendimentoUseCase finalizar;
    private final UsuarioContext usuarioContext;

    AtendimentoAcoesController(
            EnviarMensagemUseCase enviar,
            EnviarMidiaUseCase enviarMidia,
            ResolverLeadDoAtendimentoUseCase resolverLead,
            TransferirAtendimentoUseCase transferir,
            FinalizarAtendimentoUseCase finalizar,
            UsuarioContext usuarioContext) {
        this.enviar = enviar;
        this.enviarMidia = enviarMidia;
        this.resolverLead = resolverLead;
        this.transferir = transferir;
        this.finalizar = finalizar;
        this.usuarioContext = usuarioContext;
    }

    @Operation(
            summary = "Enviar mensagem de texto",
            description = "Persiste a mensagem e a outbox sem bloquear no provedor; enviar manualmente transfere o lead para quem enviou.",
            responses = {
                @ApiResponse(responseCode = "200", description = "Mensagem aceita para entrega."),
                @ApiResponse(responseCode = "404", description = "Lead ou atendimento inexistente ou não visível."),
                @ApiResponse(responseCode = "422", description = "Canal fora da janela de texto livre.")
            })
    @PostMapping("/mensagens")
    EnvioResposta enviar(@Valid @RequestBody EnviarMensagemRequisicao requisicao) {
        EnviarMensagemUseCase.Resultado resultado =
                enviar.executar(requisicao.leadId(), requisicao.conteudo());
        return EnvioResposta.de(resultado);
    }

    /**
     * Anexo do atendente (E11b). {@code id} e o atendimento, igual as rotas irmas
     * {@code /transferir} e {@code /finalizar} abaixo — resolvido para o lead aqui, porque
     * {@link EnviarMidiaUseCase} (como {@link EnviarMensagemUseCase}) trabalha em cima de lead.
     */
    @Operation(
            summary = "Enviar mensagem com mídia",
            description = "Valida e armazena o arquivo, persiste a mensagem e agenda a entrega assíncrona pelo canal.",
            responses = {
                @ApiResponse(responseCode = "200", description = "Mídia aceita para entrega."),
                @ApiResponse(responseCode = "400", description = "Arquivo não pôde ser lido."),
                @ApiResponse(responseCode = "404", description = "Atendimento inexistente ou não visível."),
                @ApiResponse(responseCode = "413", description = "Arquivo excede o limite configurado."),
                @ApiResponse(responseCode = "422", description = "Tipo de mídia não permitido ou canal fora da janela.")
            })
    @PostMapping(value = "/{id}/mensagens/midia", consumes = "multipart/form-data")
    EnvioResposta enviarMidia(
            @Parameter(description = "Identificador do atendimento.", required = true) @PathVariable UUID id,
            @Parameter(
                            description = "Arquivo binário. Tipo e limite são validados pela configuração da instância.",
                            required = true,
                            content = @Content(schema = @Schema(type = "string", format = "binary")))
                    @RequestPart("arquivo") MultipartFile arquivo,
            @Parameter(description = "Legenda opcional da mídia.")
                    @RequestParam(required = false) String legenda) {
        UUID leadId = resolverLead.executar(id);
        byte[] conteudo;
        try {
            conteudo = arquivo.getBytes();
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "falha ao ler o arquivo enviado");
        }
        EnviarMensagemUseCase.Resultado resultado =
                enviarMidia.executar(leadId, conteudo, arquivo.getOriginalFilename(), legenda);
        return EnvioResposta.de(resultado);
    }

    /** {@code paraAtendenteId} ausente devolve o atendimento para a IA. */
    @Operation(
            summary = "Transferir atendimento",
            description = "Transfere para o atendente informado; corpo ausente ou paraAtendenteId nulo devolve para a IA. Atendente comum só pode assumir para si ou devolver.",
            responses = {
                @ApiResponse(responseCode = "200", description = "Atendimento transferido."),
                @ApiResponse(responseCode = "403", description = "Destino não permitido para atendente comum."),
                @ApiResponse(responseCode = "404", description = "Atendimento inexistente ou não visível."),
                @ApiResponse(responseCode = "409", description = "Atendimento já finalizado.")
            })
    @PostMapping("/{id}/transferir")
    AtendimentoResumo transferir(
            @Parameter(description = "Identificador do atendimento.", required = true) @PathVariable UUID id,
            @RequestBody(required = false) TransferenciaRequisicao requisicao) {
        UUID paraAtendenteId = requisicao == null ? null : requisicao.paraAtendenteId();
        UUID quemPediu = usuarioContext.atual().id();
        exigirTransferenciaPermitida(paraAtendenteId, quemPediu);
        Atendimento atualizado = transferir.executar(id, paraAtendenteId, quemPediu);
        return AtendimentoResumo.de(atualizado);
    }

    /**
     * So gestor/subgestor/administrador transfere livremente. Um ATENDENTE alcanca atendimentos
     * {@code EM_IA} (grupo "Potenciais", sem dono) pela mesma RLS que autoriza a leitura — sem esta
     * trava, ele poderia entregar um potencial a um colega escolhido a dedo, contornando a RN-CRM-06
     * ("quem responde primeiro assume"). Devolver para a IA ({@code null}) ou assumir para si mesmo
     * continuam liberados: sao os dois unicos efeitos que um atendente ja conseguiria produzir de
     * outra forma (mandando mensagem).
     */
    private void exigirTransferenciaPermitida(UUID paraAtendenteId, UUID quemPediu) {
        if (usuarioContext.atual().enxergaTodosOsLeads()) {
            return;
        }
        boolean paraSiMesmoOuParaIa = paraAtendenteId == null || paraAtendenteId.equals(quemPediu);
        if (!paraSiMesmoOuParaIa) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "atendente so pode devolver para a IA ou assumir para si");
        }
    }

    @Operation(
            summary = "Finalizar atendimento",
            description = "Finaliza uma conversa visível em nome do usuário autenticado.",
            responses = {
                @ApiResponse(responseCode = "200", description = "Atendimento finalizado."),
                @ApiResponse(responseCode = "404", description = "Atendimento inexistente ou não visível."),
                @ApiResponse(responseCode = "409", description = "Atendimento já estava finalizado.")
            })
    @PostMapping("/{id}/finalizar")
    AtendimentoResumo finalizar(
            @Parameter(description = "Identificador do atendimento.", required = true) @PathVariable UUID id) {
        Atendimento atualizado = finalizar.executar(id, usuarioContext.atual().id());
        return AtendimentoResumo.de(atualizado);
    }

    @ExceptionHandler(RecursoDeAtendimentoIndisponivelException.class)
    ProblemDetail aoNaoEncontrar(RecursoDeAtendimentoIndisponivelException e) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
        problema.setTitle("Nao encontrado");
        return problema;
    }

    @ExceptionHandler(AtendimentoJaFinalizadoException.class)
    ProblemDetail aoJaEstarFinalizado(AtendimentoJaFinalizadoException e) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
        problema.setTitle("Atendimento ja finalizado");
        return problema;
    }

    @ExceptionHandler(ForaDaJanelaException.class)
    ProblemDetail aoEstarForaDaJanela(ForaDaJanelaException e) {
        ProblemDetail problema =
                ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
        problema.setTitle("Fora da janela de 24 horas");
        return problema;
    }

    @ExceptionHandler(TipoDeMidiaNaoPermitidoException.class)
    ProblemDetail aoRecusarTipoDeMidia(TipoDeMidiaNaoPermitidoException e) {
        ProblemDetail problema =
                ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
        problema.setTitle("Tipo de arquivo nao permitido");
        return problema;
    }

    @ExceptionHandler(AnexoExcedeuLimiteException.class)
    ProblemDetail aoExcederLimiteDeAnexo(AnexoExcedeuLimiteException e) {
        ProblemDetail problema =
                ProblemDetail.forStatusAndDetail(HttpStatus.PAYLOAD_TOO_LARGE, e.getMessage());
        problema.setTitle("Anexo excede o tamanho maximo");
        return problema;
    }

    record EnviarMensagemRequisicao(
            @Schema(description = "Lead visível que receberá a mensagem.", requiredMode = Schema.RequiredMode.REQUIRED)
                    @NotNull UUID leadId,
            @Schema(description = "Conteúdo textual.", example = "Olá! Posso ajudar?", requiredMode = Schema.RequiredMode.REQUIRED)
                    @NotBlank String conteudo) {}

    record TransferenciaRequisicao(
            @Schema(description = "Destino; nulo devolve o atendimento para a IA.", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                    UUID paraAtendenteId) {}

    record EnvioResposta(
            UUID atendimentoId,
            UUID mensagemId,
            String statusEntrega,
            Instant enviadoEm,
            boolean transferiuOLead) {

        static EnvioResposta de(EnviarMensagemUseCase.Resultado resultado) {
            return new EnvioResposta(
                    resultado.atendimento().id(),
                    resultado.mensagem().id(),
                    resultado.mensagem().statusEntrega().name(),
                    resultado.mensagem().enviadoEm(),
                    resultado.transferiuOLead());
        }
    }

    record AtendimentoResumo(UUID id, String status, UUID atendenteId) {
        static AtendimentoResumo de(Atendimento atendimento) {
            return new AtendimentoResumo(
                    atendimento.id(), atendimento.status().name(), atendimento.atendenteId());
        }
    }
}
