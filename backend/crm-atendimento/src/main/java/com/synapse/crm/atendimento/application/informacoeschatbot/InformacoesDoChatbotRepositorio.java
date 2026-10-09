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
     * Uma pagina de cards do atendimento, do mais recente para o mais antigo, a partir de um cursor.
     *
     * @param desde limite inferior inclusivo ({@code null} = sem limite): e o que alinha os cards ao
     *     trecho do historico de mensagens ja carregado
     * @param cursorRegistradoEm junto de {@code cursorId}, devolve so o que e estritamente anterior a
     *     eles; {@code null} = primeira pagina
     * @param limite quantidade maxima de linhas devolvidas (o chamador pede uma a mais para saber se ha mais)
     */
    List<InformacoesDoChatbot> anteriores(
            UUID atendimentoId, Instant desde, Instant cursorRegistradoEm, UUID cursorId, int limite);
}
