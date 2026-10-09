package com.synapse.crm.atendimento.application.informacoeschatbot;

import java.time.Instant;
import java.util.ArrayList;
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
 * Le os cards do historico de UM atendimento em paginas estaveis, do mais recente para o mais antigo.
 *
 * <p>Nenhum card some em silencio: a lista e paginada por cursor, no mesmo espirito do historico de
 * mensagens, e quem le decide ate onde ir. O parametro {@code desde} alinha os cards ao trecho de
 * mensagens que a tela ja carregou, entao uma conversa longa nunca traz todos os cards de uma vez.
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
    private final int tamanhoDaPagina;

    public ListarInformacoesDoChatbotUseCase(
            AtendimentoRepositorio atendimentos,
            InformacoesDoChatbotRepositorio informacoes,
            HabilitacaoDasInformacoesDoChatbot habilitacao,
            @Value("${synapse.automacao.informacoes-chatbot-tamanho-pagina:50}") int tamanhoDaPagina) {
        if (tamanhoDaPagina < 1) {
            throw new IllegalStateException("synapse.automacao.informacoes-chatbot-tamanho-pagina deve ser >= 1");
        }
        this.atendimentos = atendimentos;
        this.informacoes = informacoes;
        this.habilitacao = habilitacao;
        this.tamanhoDaPagina = tamanhoDaPagina;
    }

    /**
     * @param desde limite inferior inclusivo do trecho de interesse; {@code null} = todo o historico
     * @param cursor devolvido pela pagina anterior; {@code null} = primeira pagina (a mais recente)
     * @return pagina vazia quando a instancia nao habilitou o recurso, sem sequer consultar o
     *     atendimento: a flag desligada nao muda nenhuma resposta existente nem revela se ele existe
     * @throws RecursoDeAtendimentoIndisponivelException atendimento inexistente ou fora do alcance
     */
    @PreAuthorize("isAuthenticated()")
    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER, readOnly = true)
    public Pagina executar(UUID atendimentoId, Instant desde, Cursor cursor) {
        if (!habilitacao.habilitada()) {
            return new Pagina(List.of(), null);
        }
        atendimentos
                .porId(atendimentoId)
                .orElseThrow(() -> new RecursoDeAtendimentoIndisponivelException("atendimento", atendimentoId));

        List<InformacoesDoChatbot> encontradas = informacoes.anteriores(
                atendimentoId,
                desde,
                cursor == null ? null : cursor.registradoEm(),
                cursor == null ? null : cursor.id(),
                tamanhoDaPagina + 1);
        boolean temMais = encontradas.size() > tamanhoDaPagina;
        List<InformacoesDoChatbot> pagina = new ArrayList<>(temMais ? encontradas.subList(0, tamanhoDaPagina) : encontradas);
        Cursor proximo = temMais
                ? new Cursor(pagina.getLast().registradoEm(), pagina.getLast().id())
                : null;
        // Cada pagina sai em ordem cronologica; quem junta as paginas (mais antigas por ultimo) inverte a lista.
        return new Pagina(pagina.reversed(), proximo);
    }

    public record Cursor(Instant registradoEm, UUID id) {}

    public record Pagina(List<InformacoesDoChatbot> itens, Cursor proximoCursor) {
        public Pagina {
            itens = List.copyOf(itens);
        }
    }
}
