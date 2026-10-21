package com.synapse.crm.relatorios.application.cancelamentos;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.synapse.crm.relatorios.domain.cancelamentos.CancelamentoAgendamento;
import com.synapse.crm.relatorios.domain.cancelamentos.FiltroCancelamentosInvalidoException;
import com.synapse.crm.sharedkernel.identidade.UsuarioContext;

@Service
public class ObterCancelamentoUseCase {
    private final LeituraDeCancelamentos fonte;
    private final UsuarioContext usuarios;
    private final IdentificacaoDoLeadCancelamento identificacao;
    public ObterCancelamentoUseCase(LeituraDeCancelamentos fonte, UsuarioContext usuarios,
            IdentificacaoDoLeadCancelamento identificacao) {
        this.fonte=fonte; this.usuarios=usuarios; this.identificacao=identificacao;
    }
    @PreAuthorize("hasAnyRole('ATENDENTE','OPERADOR','SUBGESTOR','GESTOR','ADMINISTRADOR') and @capacidades.permite('cancelamentos.ver')")
    @Transactional(readOnly=true, timeoutString="${synapse.cancelamentos.timeout-segundos}")
    public Detalhe executar(String id) {
        if (id == null || !id.matches("[A-Za-z0-9_-]{1,128}")) throw new FiltroCancelamentosInvalidoException();
        var registro = fonte.detalhe(id, usuarios.atual()).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return new Detalhe(registro, registro.leadId() == null ? null : identificacao.obter(registro.leadId()).orElse(null));
    }

    public record Detalhe(CancelamentoAgendamento registro,
            IdentificacaoDoLeadCancelamento.DadosDeIdentificacaoDoCancelamento identificacao) {}
}
