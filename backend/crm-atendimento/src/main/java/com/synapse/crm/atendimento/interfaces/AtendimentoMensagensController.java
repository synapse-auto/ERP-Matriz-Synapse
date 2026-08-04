package com.synapse.crm.atendimento.interfaces;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.synapse.crm.atendimento.application.ListarHistoricoMensagensUseCase;
import com.synapse.crm.atendimento.application.RecursoDeAtendimentoIndisponivelException;
import com.synapse.crm.atendimento.application.tempo_real.ListarMensagensDesdeUseCase;
import com.synapse.crm.atendimento.domain.mensagem.Mensagem;
import com.synapse.crm.atendimento.domain.midia.ArmazenamentoDeMidia;
import com.synapse.crm.atendimento.infrastructure.midia.MidiaProperties;

/** Historico paginado e reconciliacao incremental da conversa. */
@RestController
@RequestMapping("/api/v1/atendimentos")
class AtendimentoMensagensController {

    private final ListarHistoricoMensagensUseCase listarHistorico;
    private final ListarMensagensDesdeUseCase listarDesde;
    private final ArmazenamentoDeMidia armazenamento;
    private final MidiaProperties midiaPropriedades;
    private final int tamanhoPagina;

    AtendimentoMensagensController(
            ListarHistoricoMensagensUseCase listarHistorico,
            ListarMensagensDesdeUseCase listarDesde,
            ArmazenamentoDeMidia armazenamento,
            MidiaProperties midiaPropriedades,
            @Value("${synapse.atendimento.historico.tamanho-pagina}") int tamanhoPagina) {
        this.listarHistorico = listarHistorico;
        this.listarDesde = listarDesde;
        this.armazenamento = armazenamento;
        this.midiaPropriedades = midiaPropriedades;
        this.tamanhoPagina = tamanhoPagina;
    }

    /** Cursor opaco e composto por instante + id; mensagens novas nao deslocam a proxima pagina. */
    @GetMapping("/{id}/mensagens")
    PaginaMensagensResposta mensagens(
            @PathVariable UUID id, @RequestParam(required = false) String cursor) {
        var pagina = listarHistorico.executar(id, decodificar(cursor), tamanhoPagina);
        return new PaginaMensagensResposta(
                pagina.mensagens().stream()
                        .map(mensagem -> MensagemResposta.de(mensagem, armazenamento, midiaPropriedades))
                        .toList(),
                codificar(pagina.proximoCursor()));
    }

    /** Lacuna curta do WebSocket; deliberadamente separada da navegacao do historico. */
    @GetMapping("/{id}/mensagens/desde")
    List<MensagemResposta> mensagensDesde(@PathVariable UUID id, @RequestParam Instant desde) {
        return listarDesde.executar(id, desde).stream()
                .map(mensagem -> MensagemResposta.de(mensagem, armazenamento, midiaPropriedades))
                .toList();
    }

    private static ListarHistoricoMensagensUseCase.Cursor decodificar(String cursor) {
        if (cursor == null) return null;
        try {
            String valor = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            String[] partes = valor.split("\\|", 2);
            return new ListarHistoricoMensagensUseCase.Cursor(
                    Instant.parse(partes[0]), UUID.fromString(partes[1]));
        } catch (RuntimeException erro) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cursor de mensagens invalido");
        }
    }

    private static String codificar(ListarHistoricoMensagensUseCase.Cursor cursor) {
        if (cursor == null) return null;
        String valor = cursor.enviadoEm() + "|" + cursor.id();
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(valor.getBytes(StandardCharsets.UTF_8));
    }

    @ExceptionHandler(RecursoDeAtendimentoIndisponivelException.class)
    ResponseStatusException naoEncontrado() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Atendimento nao encontrado");
    }

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

    record PaginaMensagensResposta(List<MensagemResposta> mensagens, String proximoCursor) {}
}
