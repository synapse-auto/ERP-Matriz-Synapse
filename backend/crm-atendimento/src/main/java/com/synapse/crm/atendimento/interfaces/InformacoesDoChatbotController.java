package com.synapse.crm.atendimento.interfaces;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
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
            description = "Devolve os cards mais recentes em ordem cronológica, num limite configurável. Lista vazia"
                    + " quando a instância não habilitou o recurso. Os cards não são mensagens: não entram na"
                    + " paginação do histórico nem na outbox de envio.",
            responses = {
                @ApiResponse(responseCode = "200", description = "Cards do atendimento; pode vir vazio."),
                @ApiResponse(responseCode = "404", description = "Atendimento inexistente ou fora do alcance de quem pediu.")
            })
    @GetMapping
    InformacoesDoChatbotResposta listar(
            @Parameter(description = "Identificador do atendimento.", required = true) @PathVariable UUID atendimentoId) {
        return new InformacoesDoChatbotResposta(
                listar.executar(atendimentoId).stream().map(CardResposta::de).toList());
    }

    @ExceptionHandler(RecursoDeAtendimentoIndisponivelException.class)
    ResponseStatusException naoEncontrado() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Atendimento nao encontrado");
    }

    record InformacoesDoChatbotResposta(List<CardResposta> itens) {
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
