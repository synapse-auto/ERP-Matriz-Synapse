package com.synapse.crm.relatorios.application.cancelamentos;

import java.util.List;
import java.util.Optional;

import com.synapse.crm.relatorios.domain.cancelamentos.CancelamentoAgendamento;
import com.synapse.crm.relatorios.domain.cancelamentos.FiltroCancelamentos;
import com.synapse.crm.sharedkernel.identidade.UsuarioAutenticado;

/**
 * Porta generica. Adaptador aplica escopo antes de filtros/paginacao e detalhe.
 * Para alcance MEUS: somente correlacao exata validada e visivel pela Specification/RLS;
 * sem correlacao ou sem acesso, excluir. Nunca inferir vinculo por nome/telefone.
 * Ordem: data da base DESC, id estavel DESC; intervalo exclui nulos da base antes de ordenar.
 * Sem COUNT global ou catalogos historicos.
 * Adaptador local usa transacao read-only/RLS no pool geral, sem consulta externa.
 */
public interface LeituraDeCancelamentos {
    Pagina listar(FiltroCancelamentos filtro, UsuarioAutenticado usuario);
    Optional<CancelamentoAgendamento> detalhe(String id, UsuarioAutenticado usuario);

    record Pagina(List<CancelamentoAgendamento> itens, boolean temProxima) {
        public Pagina { itens = List.copyOf(itens); }
    }
}
