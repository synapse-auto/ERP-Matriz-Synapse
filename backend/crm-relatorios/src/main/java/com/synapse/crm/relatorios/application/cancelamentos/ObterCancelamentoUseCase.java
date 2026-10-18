package com.synapse.crm.relatorios.application.cancelamentos;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.synapse.crm.relatorios.domain.cancelamentos.CancelamentoAgendamento;
import com.synapse.crm.relatorios.domain.cancelamentos.FiltroCancelamentosInvalidoException;
import com.synapse.crm.sharedkernel.identidade.UsuarioContext;

@Service
public class ObterCancelamentoUseCase {
    private final LeituraDeCancelamentos fonte;
    private final UsuarioContext usuarios;
    public ObterCancelamentoUseCase(LeituraDeCancelamentos fonte, UsuarioContext usuarios) { this.fonte=fonte; this.usuarios=usuarios; }
    @PreAuthorize("hasAnyRole('ATENDENTE','OPERADOR','SUBGESTOR','GESTOR','ADMINISTRADOR') and @capacidades.permite('cancelamentos.ver')")
    public CancelamentoAgendamento executar(String id) {
        if (id == null || !id.matches("[A-Za-z0-9_-]{1,128}")) throw new FiltroCancelamentosInvalidoException();
        return fonte.detalhe(id, usuarios.atual()).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
}
