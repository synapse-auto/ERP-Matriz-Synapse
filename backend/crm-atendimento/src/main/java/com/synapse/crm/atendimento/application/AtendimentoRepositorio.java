package com.synapse.crm.atendimento.application;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.synapse.crm.atendimento.domain.atendimento.Atendimento;

/**
 * Porta de acesso a atendimento.
 *
 * <p>Como em {@code LeadRepositorio}, o vocabulario nao consegue expressar "me da todos os
 * atendimentos": nao ha {@code findAll()} nem busca crua por id. O que existe e sempre ancorado —
 * "o aberto <em>deste lead</em>", "este atendimento <em>se eu alcanco</em>".
 *
 * <p>O recorte por papel nao aparece na assinatura porque nao e escolha de quem chama: as politicas
 * RLS de {@code atendimento} filtram pelo contexto publicado no inicio da transacao. Vazio significa
 * "nao existe ou nao e seu", e os dois casos respondem igual — distinguir contaria ao atendente que a
 * conversa existe e esta com um colega.
 */
public interface AtendimentoRepositorio {

    /**
     * O atendimento em aberto deste lead, se houver.
     *
     * <p>Um lead tem no maximo um aberto por vez: e o que permite a mensagem recebida reusar a
     * conversa em vez de abrir uma nova a cada mensagem.
     */
    Optional<Atendimento> abertoDoLead(UUID leadId);

    /** Este atendimento, se quem pede o alcanca. */
    Optional<Atendimento> porId(UUID atendimentoId);

    /** Rele o estado sob lock, depois de bloquear o lead; preserva o recorte RLS. */
    Optional<Atendimento> porIdParaAlteracao(UUID atendimentoId);

    /**
     * Atendimentos em atendimento humano que o contexto RLS do usuario atual alcanca.
     *
     * <p>Nao inclui {@code EM_IA}: o lote de finalizacao nao pode encerrar a fila da IA, e o
     * {@code UPDATE} que tira a linha da visibilidade do atendente e recusado pela RLS.
     *
     * @param atendenteIdFiltro filtro adicional sobre o dono; {@code null} = todos os visiveis.
     *     Nunca amplia o alcance da RLS: um id de colega para quem so ve a propria carteira
     *     devolve lista vazia.
     */
    List<Atendimento> abertosVisiveis(UUID atendenteIdFiltro);

    /**
     * Quebra da mesma populacao de {@link #abertosVisiveis(UUID)} sem filtro de dono — uma linha
     * por {@code atendente_id}, com nome, para alimentar o seletor do lote (E137).
     */
    List<ContagemPorAtendente> contagemAbertosVisiveisPorAtendente();

    /** Contagem agregada de {@link #abertosVisiveis(UUID)} — mesma definicao, sem carregar as linhas. */
    record ContagemPorAtendente(UUID atendenteId, String nome, long quantidade) {}

    /**
     * Serializa a escolha de destino feita pela Automacao.
     *
     * <p>Sem esta trava, duas requisicoes concorrentes podem observar a mesma carga e entregar os
     * dois atendimentos ao mesmo profissional. A trava dura somente a transacao corrente e nao cria
     * estado novo no banco.
     */
    void bloquearDistribuicaoDaAutomacao();

    /** Quantos atendimentos abertos compoem a carga atual de um candidato elegivel. */
    long contarAbertosDoAtendente(UUID atendenteId);

    /** Registra a leitura do usuario corrente, independentemente do dono comercial do atendimento. */
    void marcarComoLido(UUID atendimentoId, UUID usuarioId, Instant quando);

    /**
     * Publica {@code app.papel = SERVICO} no resto da transação.
     *
     * <p>A RLS de {@code atendimento} aplica {@code USING} também à linha nova do UPDATE: um
     * atendente que transfere para um colega deixa de "enxergar" o registro no mesmo comando, e o
     * Postgres responde 500. A leitura e as recusas (404, destino inválido, potencial) já
     * rodaram com o papel real; daqui pra frente só gravamos o que já foi autorizado.
     */
    void elevarRlsParaEscritaDeNovoDono();

    /** Insere ou atualiza. Devolve o estado gravado. */
    Atendimento salvar(Atendimento atendimento);
}
