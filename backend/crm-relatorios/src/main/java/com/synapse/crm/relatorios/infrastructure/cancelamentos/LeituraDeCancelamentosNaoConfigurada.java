package com.synapse.crm.relatorios.infrastructure.cancelamentos;

import java.util.Optional;

import org.springframework.stereotype.Component;

import com.synapse.crm.relatorios.application.cancelamentos.FonteCancelamentosException;
import com.synapse.crm.relatorios.application.cancelamentos.LeituraDeCancelamentos;
import com.synapse.crm.relatorios.domain.cancelamentos.CancelamentoAgendamento;
import com.synapse.crm.relatorios.domain.cancelamentos.FiltroCancelamentos;
import com.synapse.crm.sharedkernel.identidade.UsuarioAutenticado;

/** Unico adaptador de producao nesta etapa. Nao consulta nem presume banco externo. */
@Component
class LeituraDeCancelamentosNaoConfigurada implements LeituraDeCancelamentos {
    @Override public Pagina listar(FiltroCancelamentos filtro, UsuarioAutenticado usuario) { throw ausente(); }
    @Override public Optional<CancelamentoAgendamento> detalhe(String id, UsuarioAutenticado usuario) { throw ausente(); }
    private FonteCancelamentosException ausente() {
        return new FonteCancelamentosException(FonteCancelamentosException.Codigo.INTEGRACAO_NAO_CONFIGURADA);
    }
}
