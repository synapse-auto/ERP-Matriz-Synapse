package com.synapse.crm.atendimento.interfaces;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.synapse.crm.atendimento.application.ListarHistoricoMensagensUseCase;
import com.synapse.crm.atendimento.application.MarcarAtendimentoComoLidoUseCase;
import com.synapse.crm.atendimento.application.RecursoDeAtendimentoIndisponivelException;
import com.synapse.crm.atendimento.application.historico.MensagemDoHistorico;
import com.synapse.crm.atendimento.application.tempo_real.ListarMensagensDesdeUseCase;
import com.synapse.crm.atendimento.domain.mensagem.Mensagem;
import com.synapse.crm.atendimento.domain.midia.ArmazenamentoDeMidia;
import com.synapse.crm.atendimento.infrastructure.midia.MidiaProperties;

/** Historico paginado e reconciliacao incremental da conversa. */
@RestController
@RequestMapping("/api/v1/atendimentos")
@Tag(name = "Mensagens dos atendimentos", description = "Histórico paginado e reconciliação após lacunas do WebSocket.")
@SecurityRequirement(name = "bearerAuth")
class AtendimentoMensagensController {

    private final ListarHistoricoMensagensUseCase listarHistorico;
    private final ListarMensagensDesdeUseCase listarDesde;
    private final MarcarAtendimentoComoLidoUseCase marcarComoLido;
    private final ArmazenamentoDeMidia armazenamento;
    private final MidiaProperties midiaPropriedades;
    private final int tamanhoPagina;

    AtendimentoMensagensController(
            ListarHistoricoMensagensUseCase listarHistorico,
            ListarMensagensDesdeUseCase listarDesde,
            MarcarAtendimentoComoLidoUseCase marcarComoLido,
            ArmazenamentoDeMidia armazenamento,
            MidiaProperties midiaPropriedades,
            @Value("${synapse.atendimento.historico.tamanho-pagina}") int tamanhoPagina) {
        this.listarHistorico = listarHistorico;
        this.listarDesde = listarDesde;
        this.marcarComoLido = marcarComoLido;
        this.armazenamento = armazenamento;
        this.midiaPropriedades = midiaPropriedades;
        this.tamanhoPagina = tamanhoPagina;
    }

    /** Cursor opaco e composto por instante + id; mensagens novas nao deslocam a proxima pagina. */
    @Operation(
            summary = "Listar histórico de mensagens",
            description = "Navega para trás com cursor opaco e estável; o tamanho da página vem da configuração da instância.",
            responses = {
                @ApiResponse(responseCode = "200", description = "Página do histórico e próximo cursor, quando houver."),
                @ApiResponse(responseCode = "400", description = "Cursor malformado."),
                @ApiResponse(responseCode = "404", description = "Atendimento inexistente ou não visível.")
            })
    @GetMapping("/{id}/mensagens")
    PaginaMensagensResposta mensagens(
            @Parameter(description = "Identificador do atendimento.", required = true) @PathVariable UUID id,
            @Parameter(description = "Cursor opaco devolvido pela página anterior.")
                    @RequestParam(required = false) String cursor) {
        var pagina = listarHistorico.executar(id, decodificar(cursor), tamanhoPagina);
        return new PaginaMensagensResposta(
                pagina.mensagens().stream()
                        .map(mensagem -> MensagemResposta.de(mensagem, armazenamento, midiaPropriedades))
                        .toList(),
                codificar(pagina.proximoCursor()));
    }

    /** Lacuna curta do WebSocket; deliberadamente separada da navegacao do historico. */
    @Operation(
            summary = "Reconciliar mensagens desde um instante",
            description = "Retorna a lacuna curta após perda ou reconexão do WebSocket; não substitui a paginação do histórico.",
            responses = {
                @ApiResponse(responseCode = "200", description = "Mensagens posteriores ao instante informado."),
                @ApiResponse(responseCode = "404", description = "Atendimento inexistente ou não visível.")
            })
    @GetMapping("/{id}/mensagens/desde")
    List<MensagemResposta> mensagensDesde(
            @Parameter(description = "Identificador do atendimento.", required = true) @PathVariable UUID id,
            @Parameter(description = "Instante inicial exclusivo em UTC.", required = true, example = "2026-08-05T12:00:00Z")
                    @RequestParam Instant desde) {
        return listarDesde.executar(id, desde).stream()
                .map(mensagem -> MensagemResposta.de(mensagem, armazenamento, midiaPropriedades))
                .toList();
    }

    @Operation(
            summary = "Marcar conversa como lida",
            description = "Avança a leitura somente quando o usuário autenticado é o responsável atual; a consulta de um gestor é um no-op.",
            responses = @ApiResponse(responseCode = "204", description = "Abertura processada sem expor a propriedade do atendimento."))
    @PostMapping("/{id}/leitura")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void marcarComoLido(
            @Parameter(description = "Identificador do atendimento.", required = true) @PathVariable UUID id) {
        marcarComoLido.executar(id);
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
            String remetenteNome,
            String tipo,
            String conteudo,
            String midiaUrl,
            String midiaMetadados,
            String statusEntrega,
            Instant enviadoEm) {

        static MensagemResposta de(
                MensagemDoHistorico item,
                ArmazenamentoDeMidia armazenamento,
                MidiaProperties midiaPropriedades) {
            Mensagem mensagem = item.mensagem();
            String midiaUrl = mensagem.midiaUrl() == null
                    ? null
                    : armazenamento.urlAssinada(mensagem.midiaUrl(), midiaPropriedades.expiracaoLeitura());
            return new MensagemResposta(
                    mensagem.id(),
                    mensagem.remetente().tipo().name(),
                    mensagem.remetente().id(),
                    item.remetenteNome(),
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
