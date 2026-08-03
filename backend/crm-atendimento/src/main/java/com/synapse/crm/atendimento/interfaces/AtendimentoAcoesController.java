package com.synapse.crm.atendimento.interfaces;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.synapse.crm.atendimento.application.EnviarMensagemUseCase;
import com.synapse.crm.atendimento.application.FinalizarAtendimentoUseCase;
import com.synapse.crm.atendimento.application.RecursoDeAtendimentoIndisponivelException;
import com.synapse.crm.atendimento.application.TransferirAtendimentoUseCase;
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
class AtendimentoAcoesController {

    private final EnviarMensagemUseCase enviar;
    private final TransferirAtendimentoUseCase transferir;
    private final FinalizarAtendimentoUseCase finalizar;
    private final UsuarioContext usuarioContext;

    AtendimentoAcoesController(
            EnviarMensagemUseCase enviar,
            TransferirAtendimentoUseCase transferir,
            FinalizarAtendimentoUseCase finalizar,
            UsuarioContext usuarioContext) {
        this.enviar = enviar;
        this.transferir = transferir;
        this.finalizar = finalizar;
        this.usuarioContext = usuarioContext;
    }

    /** Texto livre apenas — sem endpoint de upload, nao ha midia para enviar ainda. */
    @PostMapping("/mensagens")
    EnvioResposta enviar(@Valid @RequestBody EnviarMensagemRequisicao requisicao) {
        EnviarMensagemUseCase.Resultado resultado =
                enviar.executar(requisicao.leadId(), requisicao.conteudo());
        return EnvioResposta.de(resultado);
    }

    /** {@code paraAtendenteId} ausente devolve o atendimento para a IA. */
    @PostMapping("/{id}/transferir")
    AtendimentoResumo transferir(
            @PathVariable UUID id, @RequestBody(required = false) TransferenciaRequisicao requisicao) {
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

    @PostMapping("/{id}/finalizar")
    AtendimentoResumo finalizar(@PathVariable UUID id) {
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

    record EnviarMensagemRequisicao(@NotNull UUID leadId, @NotBlank String conteudo) {}

    record TransferenciaRequisicao(UUID paraAtendenteId) {}

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
