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
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.synapse.crm.atendimento.application.RecursoDeAtendimentoIndisponivelException;
import com.synapse.crm.atendimento.application.informacoeschatbot.ListarInformacoesDoChatbotUseCase;
import com.synapse.crm.atendimento.domain.informacoeschatbot.InformacoesDoChatbot;

/** Leitura dos cards do historico; o navegador so le, quem escreve e o contrato interno do n8n. */
@RestController
@RequestMapping("/api/v1/atendimentos/{atendimentoId}/informacoes-do-chatbot")
@Tag(name = "Informações do chatbot", description = "Cards internos com o que o chatbot coletou antes da transferência.")
@SecurityRequirement(name = "bearerAuth")
class InformacoesDoChatbotController {

    private final ListarInformacoesDoChatbotUseCase listar;

    InformacoesDoChatbotController(ListarInformacoesDoChatbotUseCase listar) {
        this.listar = listar;
    }

    @Operation(
            summary = "Listar os cards de informações do chatbot do atendimento",
            description = "Pagina de cards, da mais recente para a mais antiga, com cursor opaco: nenhum card some"
                    + " em silêncio, quem lê decide até onde ir. Cada página sai em ordem cronológica; o tamanho vem da"
                    + " configuração da instância. `desde` limita a janela ao trecho do histórico já carregado. Lista vazia"
                    + " quando a instância não habilitou o recurso. Os cards não são mensagens: não entram na paginação de"
                    + " mensagens nem na outbox de envio.",
            responses = {
                @ApiResponse(responseCode = "200", description = "Página de cards e próximo cursor, quando houver."),
                @ApiResponse(responseCode = "400", description = "Cursor ou `desde` malformado."),
                @ApiResponse(responseCode = "404", description = "Atendimento inexistente ou fora do alcance de quem pediu.")
            })
    @GetMapping
    InformacoesDoChatbotResposta listar(
            @Parameter(description = "Identificador do atendimento.", required = true) @PathVariable UUID atendimentoId,
            @Parameter(description = "Limite inferior inclusivo (ISO-8601): só cards registrados a partir deste instante.")
                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant desde,
            @Parameter(description = "Cursor opaco devolvido pela página anterior.")
                    @RequestParam(required = false) String cursor) {
        var pagina = listar.executar(atendimentoId, desde, decodificar(cursor));
        return new InformacoesDoChatbotResposta(
                pagina.itens().stream().map(CardResposta::de).toList(), codificar(pagina.proximoCursor()));
    }

    @ExceptionHandler(RecursoDeAtendimentoIndisponivelException.class)
    ResponseStatusException naoEncontrado() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Atendimento nao encontrado");
    }

    private static ListarInformacoesDoChatbotUseCase.Cursor decodificar(String cursor) {
        if (cursor == null) return null;
        try {
            String valor = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            String[] partes = valor.split("\\|", 2);
            return new ListarInformacoesDoChatbotUseCase.Cursor(Instant.parse(partes[0]), UUID.fromString(partes[1]));
        } catch (RuntimeException erro) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cursor de informacoes do chatbot invalido");
        }
    }

    private static String codificar(ListarInformacoesDoChatbotUseCase.Cursor cursor) {
        if (cursor == null) return null;
        String valor = cursor.registradoEm() + "|" + cursor.id();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(valor.getBytes(StandardCharsets.UTF_8));
    }

    record InformacoesDoChatbotResposta(List<CardResposta> itens, String proximoCursor) {
        InformacoesDoChatbotResposta {
            itens = List.copyOf(itens);
        }
    }

    /** {@code origem} fixa em AUTOMACAO: o card nunca e atribuido a uma pessoa nem ao cliente. */
    record CardResposta(UUID id, UUID atendimentoId, String conteudo, String origem, Instant registradoEm) {
        static CardResposta de(InformacoesDoChatbot informacoes) {
            return new CardResposta(
                    informacoes.id(),
                    informacoes.atendimentoId(),
                    informacoes.conteudo(),
                    "AUTOMACAO",
                    informacoes.registradoEm());
        }
    }
}
