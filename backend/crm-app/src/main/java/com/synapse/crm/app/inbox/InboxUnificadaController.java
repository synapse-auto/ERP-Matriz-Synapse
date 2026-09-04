package com.synapse.crm.app.inbox;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.synapse.crm.atendimento.application.painel.VisaoAtendimento;

@RestController
@RequestMapping("/api/v1/atendimentos/inbox")
@Tag(name = "Inbox unificada", description = "Conversas de clientes e equipe interna ordenadas por recência.")
@SecurityRequirement(name = "bearerAuth")
class InboxUnificadaController {
    private final ListarInboxUnificadaUseCase listar;

    InboxUnificadaController(ListarInboxUnificadaUseCase listar) { this.listar = listar; }

    @GetMapping
    @Operation(summary = "Listar inbox unificada", description = "Retorna clientes visíveis e conversas internas das quais o usuário participa.")
    InboxUnificada listar(
            @Parameter(description = "Visão operacional; conversas internas aparecem em ATIVOS e TODOS.")
                    @RequestParam(defaultValue = "TODOS") VisaoAtendimento visao,
            @Parameter(description = "Quantidade por página, entre 1 e 100.")
                    @RequestParam(defaultValue = "50") int limite,
            @Parameter(description = "Cursor opaco devolvido pela página anterior.")
                    @RequestParam(required = false) String cursor) {
        return listar.executar(visao, limite, cursor);
    }
}
