package com.synapse.crm.relatorios.interfaces.auditoria;

import java.time.Instant;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.synapse.crm.relatorios.application.auditoria.ConsultarAuditLogUseCase;
import com.synapse.crm.relatorios.domain.auditoria.FiltroDeAuditLog;
import com.synapse.crm.relatorios.domain.auditoria.LinhaDeAuditLog;
import com.synapse.crm.relatorios.interfaces.PaginaResposta;

/**
 * Consulta do log de auditoria (E09a). A restricao a GESTOR/ADMINISTRADOR e declarada em
 * {@link ConsultarAuditLogUseCase}, nao aqui — mesma convencao do resto do projeto.
 */
@RestController
@RequestMapping("/api/v1/audit-log")
@Tag(name = "Auditoria", description = "Consulta administrativa e paginada das ações sensíveis registradas.")
@SecurityRequirement(name = "bearerAuth")
class AuditLogController {

    private final ConsultarAuditLogUseCase consultar;

    AuditLogController(ConsultarAuditLogUseCase consultar) {
        this.consultar = consultar;
    }

    @Operation(
            summary = "Consultar log de auditoria",
            description = "Combina filtros opcionais, paginação baseada em zero e ordenação Spring; restrito a gestor e administrador.",
            responses = @ApiResponse(responseCode = "200", description = "Página de registros de auditoria."))
    @Parameters({
        @Parameter(name = "page", description = "Índice da página, começando em zero.", example = "0"),
        @Parameter(name = "size", description = "Quantidade de itens; padrão 50.", example = "50"),
        @Parameter(name = "sort", description = "Campo e direção, por exemplo criadoEm,desc.", example = "criadoEm,desc")
    })
    @GetMapping
    PaginaResposta<LinhaDeAuditLogResposta> listar(
            @Parameter(description = "Identificador do usuário ou serviço autor.") @RequestParam(required = false) UUID atorId,
            @Parameter(description = "Código exato da ação auditada.") @RequestParam(required = false) String acao,
            @Parameter(description = "Tipo lógico da entidade alterada.") @RequestParam(required = false) String entidadeTipo,
            @Parameter(description = "Identificador da entidade alterada.") @RequestParam(required = false) UUID entidadeId,
            @Parameter(description = "Lead relacionado à ação.") @RequestParam(required = false) UUID leadId,
            @Parameter(description = "Início inclusivo em UTC.", example = "2026-08-01T00:00:00Z")
                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant de,
            @Parameter(description = "Fim inclusivo em UTC.", example = "2026-08-05T23:59:59Z")
                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant ate,
            @Parameter(hidden = true) @PageableDefault(size = 50) Pageable pageable) {

        FiltroDeAuditLog filtro = new FiltroDeAuditLog(atorId, acao, entidadeTipo, entidadeId, leadId, de, ate);
        Page<LinhaDeAuditLog> pagina = consultar.executar(filtro, pageable);
        return PaginaResposta.de(pagina, LinhaDeAuditLogResposta::de);
    }
}

record LinhaDeAuditLogResposta(
        long id,
        UUID atorId,
        String atorTipo,
        String acao,
        String entidadeTipo,
        UUID entidadeId,
        UUID leadId,
        String dadosAntes,
        String dadosDepois,
        String ip,
        Instant criadoEm) {

    static LinhaDeAuditLogResposta de(LinhaDeAuditLog linha) {
        return new LinhaDeAuditLogResposta(
                linha.id(),
                linha.atorId(),
                linha.atorTipo(),
                linha.acao(),
                linha.entidadeTipo(),
                linha.entidadeId(),
                linha.leadId(),
                linha.dadosAntes(),
                linha.dadosDepois(),
                linha.ip(),
                linha.criadoEm());
    }
}
