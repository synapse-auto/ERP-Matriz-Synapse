package com.synapse.crm.relatorios.application.auditoria;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.synapse.crm.relatorios.domain.auditoria.FiltroDeAuditLog;
import com.synapse.crm.relatorios.domain.auditoria.LinhaDeAuditLog;

public interface AuditLogRepositorio {

    Page<LinhaDeAuditLog> buscar(FiltroDeAuditLog filtro, Pageable pageable);
}
