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
import com.synapse.crm.atendimento.domain.midia.ArmazenamentoDeMidia;
import com.synapse.crm.atendimento.infrastructure.midia.MidiaProperties;

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
    private final ArmazenamentoDeMidia armazenamento;
    private final MidiaProperties midiaPropriedades;

    AtendimentoMensagensController(
            ListarMensagensDesdeUseCase listar,
            ArmazenamentoDeMidia armazenamento,
            MidiaProperties midiaPropriedades) {
        this.listar = listar;
        this.armazenamento = armazenamento;
        this.midiaPropriedades = midiaPropriedades;
    }

    /** {@code desde} ausente devolve toda a janela conhecida — primeira carga da tela. */
    @GetMapping("/{id}/mensagens")
    List<MensagemResposta> mensagens(
            @PathVariable UUID id, @RequestParam(required = false) Instant desde) {
        Instant efetivo = desde == null ? Instant.EPOCH : desde;
        return listar.executar(id, efetivo).stream()
                .map(mensagem -> MensagemResposta.de(mensagem, armazenamento, midiaPropriedades))
                .toList();
    }

    @ExceptionHandler(RecursoDeAtendimentoIndisponivelException.class)
    ResponseStatusException naoEncontrado() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Atendimento nao encontrado");
    }

    /**
     * {@code tipo}, {@code midiaUrl} e {@code midiaMetadados} destravam renderizar midia recebida do
     * lead (imagem, audio, documento) — ja existem no dominio, so nao eram serializados.
     */
    record MensagemResposta(
            UUID id,
            String remetenteTipo,
            UUID remetenteId,
            String tipo,
            String conteudo,
            String midiaUrl,
            String midiaMetadados,
            String statusEntrega,
            Instant enviadoEm) {

        static MensagemResposta de(
                Mensagem mensagem, ArmazenamentoDeMidia armazenamento, MidiaProperties midiaPropriedades) {
            // A referencia opaca so vira URL assinada aqui, depois que a mensagem ja passou
            // pela visibilidade do atendimento (ver o Javadoc desta classe) — e o que impede
            // uma URL utilizavel de mensagem de outro atendente vazar por este endpoint.
            String midiaUrl = mensagem.midiaUrl() == null
                    ? null
                    : armazenamento.urlAssinada(mensagem.midiaUrl(), midiaPropriedades.expiracaoLeitura());
            return new MensagemResposta(
                    mensagem.id(),
                    mensagem.remetente().tipo().name(),
                    mensagem.remetente().id(),
                    mensagem.tipo().name(),
                    mensagem.conteudo(),
                    midiaUrl,
                    mensagem.midiaMetadados(),
                    mensagem.statusEntrega().name(),
                    mensagem.enviadoEm());
        }
    }
}
