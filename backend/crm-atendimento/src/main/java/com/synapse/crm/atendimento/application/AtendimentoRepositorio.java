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

    /** Atendimentos ainda abertos que o contexto RLS do usuario atual alcanca. */
    List<Atendimento> abertosVisiveis();

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
