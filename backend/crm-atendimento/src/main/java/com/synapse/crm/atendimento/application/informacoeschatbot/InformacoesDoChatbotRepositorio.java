package com.synapse.crm.atendimento.application.informacoeschatbot;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.synapse.crm.atendimento.domain.informacoeschatbot.InformacoesDoChatbot;

/**
 * Porta dos cards de informacoes do chatbot.
 *
 * <p>Sem busca crua por id e sem {@code findAll}: toda leitura e ancorada num atendimento, e a
 * visibilidade e a da propria tabela (RLS ancorada na de {@code atendimento}). Nao ha atualizacao nem
 * remocao — o snapshot e imutavel.
 */
public interface InformacoesDoChatbotRepositorio {

    void inserir(UUID id, UUID atendimentoId, String chaveIdempotencia, String conteudo, Instant registradoEm);

    /**
     * Os cards mais recentes do atendimento, em ordem cronologica crescente.
     *
     * @param limite teto de linhas; o recorte e sempre o dos <em>mais recentes</em>
     */
    List<InformacoesDoChatbot> recentesDoAtendimento(UUID atendimentoId, int limite);
}
