package com.synapse.crm.atendimento.interfaces;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.synapse.crm.atendimento.application.RecursoDeAtendimentoIndisponivelException;
import com.synapse.crm.atendimento.application.tempo_real.ListarMensagensDesdeUseCase;
import com.synapse.crm.atendimento.domain.mensagem.Mensagem;

/**
 * A reconciliacao que fecha a lacuna do WebSocket (E06 secao 4).
 *
 * <p>Uma queda de rede de 10s nao pode virar mensagem perdida. O cliente guarda o instante da ultima
 * mensagem que recebeu e, ao reconectar, chama esta rota antes de retomar o socket — so entao volta a
 * confiar no tempo real para o que chegar dali em diante.
 */
@RestController
@RequestMapping("/api/v1/atendimentos")
class AtendimentoMensagensController {

    private final ListarMensagensDesdeUseCase listar;

    AtendimentoMensagensController(ListarMensagensDesdeUseCase listar) {
        this.listar = listar;
    }

    /** {@code desde} ausente devolve toda a janela conhecida — primeira carga da tela. */
    @GetMapping("/{id}/mensagens")
    List<MensagemResposta> mensagens(
            @PathVariable UUID id, @RequestParam(required = false) Instant desde) {
        Instant efetivo = desde == null ? Instant.EPOCH : desde;
        return listar.executar(id, efetivo).stream().map(MensagemResposta::de).toList();
    }

    @ExceptionHandler(RecursoDeAtendimentoIndisponivelException.class)
    ResponseStatusException naoEncontrado() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Atendimento nao encontrado");
    }

    record MensagemResposta(
            UUID id,
            String remetenteTipo,
            UUID remetenteId,
            String conteudo,
            String statusEntrega,
            Instant enviadoEm) {

        static MensagemResposta de(Mensagem mensagem) {
            return new MensagemResposta(
                    mensagem.id(),
                    mensagem.remetente().tipo().name(),
                    mensagem.remetente().id(),
                    mensagem.conteudo(),
                    mensagem.statusEntrega().name(),
                    mensagem.enviadoEm());
        }
    }
}
