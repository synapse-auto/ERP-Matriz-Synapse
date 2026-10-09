package com.synapse.crm.atendimento.application.informacoeschatbot;

import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.atendimento.application.AtendimentoRepositorio;
import com.synapse.crm.atendimento.application.RecursoDeAtendimentoIndisponivelException;
import com.synapse.crm.atendimento.domain.informacoeschatbot.InformacoesDoChatbot;
import com.synapse.crm.sharedkernel.persistencia.Pools;

/**
 * Le os cards do historico de UM atendimento, numa unica consulta limitada.
 *
 * <p>Fica fora da paginacao de mensagens de proposito: o card nao e mensagem e nao pode distorcer o
 * cursor, a contagem nem a reconciliacao do historico. Quem alcanca o atendimento alcanca os cards —
 * o acesso e checado pelo mesmo {@code AtendimentoRepositorio.porId} que protege as mensagens, e a
 * RLS da tabela repete a regra no banco.
 */
@Service
public class ListarInformacoesDoChatbotUseCase {

    private final AtendimentoRepositorio atendimentos;
    private final InformacoesDoChatbotRepositorio informacoes;
    private final HabilitacaoDasInformacoesDoChatbot habilitacao;
    private final int limite;

    public ListarInformacoesDoChatbotUseCase(
            AtendimentoRepositorio atendimentos,
            InformacoesDoChatbotRepositorio informacoes,
            HabilitacaoDasInformacoesDoChatbot habilitacao,
            @Value("${synapse.automacao.informacoes-chatbot-limite-listagem:50}") int limite) {
        if (limite < 1) {
            throw new IllegalStateException("synapse.automacao.informacoes-chatbot-limite-listagem deve ser >= 1");
        }
        this.atendimentos = atendimentos;
        this.informacoes = informacoes;
        this.habilitacao = habilitacao;
        this.limite = limite;
    }

    /**
     * @return vazio quando a instancia nao habilitou o recurso, sem sequer consultar o atendimento: a
     *     flag desligada nao muda nenhuma resposta existente nem revela se o atendimento existe
     * @throws RecursoDeAtendimentoIndisponivelException atendimento inexistente ou fora do alcance
     */
    @PreAuthorize("isAuthenticated()")
    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER, readOnly = true)
    public List<InformacoesDoChatbot> executar(UUID atendimentoId) {
        if (!habilitacao.habilitada()) {
            return List.of();
        }
        atendimentos
                .porId(atendimentoId)
                .orElseThrow(() -> new RecursoDeAtendimentoIndisponivelException("atendimento", atendimentoId));
        return informacoes.recentesDoAtendimento(atendimentoId, limite);
    }
}
