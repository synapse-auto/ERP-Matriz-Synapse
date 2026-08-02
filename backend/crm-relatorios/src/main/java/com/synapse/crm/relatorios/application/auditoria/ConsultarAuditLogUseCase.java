package com.synapse.crm.relatorios.application.auditoria;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.relatorios.domain.auditoria.FiltroDeAuditLog;
import com.synapse.crm.relatorios.domain.auditoria.LinhaDeAuditLog;

/**
 * Consulta de {@code audit_log} — tabela que so cresce (a segunda maior depois de {@code mensagem}),
 * por isso paginacao nao e opcional aqui.
 *
 * <p>{@code @Transactional(readOnly = true)}: garante uma transacao Spring real, o que faz o gerente
 * de transacao customizado ({@code TransacaoComRlsConfig}) aplicar {@code SET LOCAL ROLE}/
 * {@code app.usuario_id} antes de {@link AuditLogRepositorio} tocar o banco — mesmo sem RLS em
 * {@code audit_log} (nao tem), e boa pratica nao ler fora de transacao.
 */
@Service
public class ConsultarAuditLogUseCase {

    private final AuditLogRepositorio repositorio;

    public ConsultarAuditLogUseCase(AuditLogRepositorio repositorio) {
        this.repositorio = repositorio;
    }

    @PreAuthorize("hasAnyRole('GESTOR', 'ADMINISTRADOR')")
    @Transactional(readOnly = true)
    public Page<LinhaDeAuditLog> executar(FiltroDeAuditLog filtro, Pageable pageable) {
        return repositorio.buscar(filtro, pageable);
    }
}
