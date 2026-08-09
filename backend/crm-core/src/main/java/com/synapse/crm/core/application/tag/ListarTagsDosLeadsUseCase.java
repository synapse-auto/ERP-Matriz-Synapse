package com.synapse.crm.core.application.tag;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.core.domain.tag.Tag;

/**
 * Tags de varios leads de uma vez (E16 §Bloco 1) — a tabela da agenda enriquece uma pagina inteira
 * numa consulta so, nunca uma por linha.
 *
 * <p>Os leads ja chegam autorizados: quem chama isto e sempre o resultado de {@code
 * FiltrarLeadsUseCase}, ja recortado pela visibilidade. Este caso de uso nao confere lead de novo —
 * so busca a associacao — porque {@link Tag} nao tem regra de visibilidade propria (e da operacao
 * inteira, nao de um atendente).
 *
 * <p>{@code @Transactional} aqui e obrigatorio, e nao decoracao: {@code LeadTagRepositorioJdbc} exige
 * transacao ativa para que a conexao receba {@code SET LOCAL ROLE}/{@code app.usuario_id} antes da
 * politica RLS entrar em jogo — chamar o repositorio direto do controller, sem essa fronteira,
 * derruba a consulta com {@code AcessoSemTransacaoException}.
 */
@Service
public class ListarTagsDosLeadsUseCase {

    private final LeadTagRepositorio vinculos;

    public ListarTagsDosLeadsUseCase(LeadTagRepositorio vinculos) {
        this.vinculos = vinculos;
    }

    @PreAuthorize("hasAnyRole('ATENDENTE', 'SUBGESTOR', 'GESTOR', 'ADMINISTRADOR')")
    @Transactional(readOnly = true)
    public Map<UUID, List<Tag>> executar(List<UUID> leadIds) {
        return vinculos.listarPorLeads(leadIds);
    }
}
