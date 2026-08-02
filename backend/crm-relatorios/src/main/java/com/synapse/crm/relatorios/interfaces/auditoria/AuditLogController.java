package com.synapse.crm.relatorios.interfaces.auditoria;

import java.time.Instant;
import java.util.UUID;

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
class AuditLogController {

    private final ConsultarAuditLogUseCase consultar;

    AuditLogController(ConsultarAuditLogUseCase consultar) {
        this.consultar = consultar;
    }

    @GetMapping
    PaginaResposta<LinhaDeAuditLogResposta> listar(
            @RequestParam(required = false) UUID atorId,
            @RequestParam(required = false) String acao,
            @RequestParam(required = false) String entidadeTipo,
            @RequestParam(required = false) UUID entidadeId,
            @RequestParam(required = false) UUID leadId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant de,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant ate,
            @PageableDefault(size = 50) Pageable pageable) {

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
