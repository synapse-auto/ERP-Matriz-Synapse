package com.synapse.crm.relatorios.application.cancelamentos;

import java.util.List;
import java.util.Optional;

import com.synapse.crm.relatorios.domain.cancelamentos.CancelamentoAgendamento;
import com.synapse.crm.relatorios.domain.cancelamentos.FiltroCancelamentos;
import com.synapse.crm.sharedkernel.identidade.UsuarioAutenticado;

/**
 * Porta generica. Adaptador futuro DEVE aplicar escopo antes de filtros/paginacao e detalhe.
 * Para alcance MEUS: somente correlacao exata validada e visivel pela Specification/RLS;
 * sem correlacao ou sem acesso, excluir. Nunca inferir vinculo por nome/telefone.
 * Ordem: data da base DESC NULLS LAST, id estavel DESC. Sem COUNT global ou catalogos historicos.
 * Implementacao real depende de contrato aprovado, timeout, circuit breaker e isolamento de recursos.
 */
public interface LeituraDeCancelamentos {
    Pagina listar(FiltroCancelamentos filtro, UsuarioAutenticado usuario);
    Optional<CancelamentoAgendamento> detalhe(String id, UsuarioAutenticado usuario);

    record Pagina(List<CancelamentoAgendamento> itens, boolean temProxima) {
        public Pagina { itens = List.copyOf(itens); }
    }
}
