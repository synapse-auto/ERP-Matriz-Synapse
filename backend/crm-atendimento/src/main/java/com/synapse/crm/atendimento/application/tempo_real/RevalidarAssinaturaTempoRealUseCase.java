package com.synapse.crm.atendimento.application.tempo_real;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.atendimento.application.AtendimentoRepositorio;
import com.synapse.crm.atendimento.application.participacao.ParticipacaoAtendimentoRepositorio;
import com.synapse.crm.sharedkernel.identidade.ContextoDeServico;
import com.synapse.crm.sharedkernel.identidade.PapelUsuario;
import com.synapse.crm.sharedkernel.persistencia.Pools;

/**
 * A revalidacao do TTL (E07 §0): confirma que uma assinatura de WebSocket ainda enxerga o
 * atendimento, quando o registro em memoria decide que o prazo de confianca venceu.
 *
 * <p>Ao contrario de {@link AutorizarAssinaturaAtendimentoUseCase}, quem chama nao e uma requisicao
 * de usuario — e o backplane Redis, entregando uma mensagem numa thread que nunca autenticou
 * ninguem. Por isso {@code usuarioId}/{@code papel} vem do que {@code AssinaturaAutorizada} capturou
 * no momento da autorizacao original.
 *
 * <p><b>Sem {@link ContextoDeServico} aqui dentro, de proposito:</b> {@code ContextoDeServico} precisa
 * estar ativo <em>antes</em> da transacao comecar (e quem publica o contexto para o RLS e um gerente
 * de transacao customizado, no inicio do {@code doBegin}). Se este metodo chamasse a si mesmo
 * (auto-invocacao — {@code this.metodo()} de dentro da propria classe) para separar "define o
 * contexto" de "abre a transacao", a chamada interna pularia o proxy CGLIB do Spring e o
 * {@code @Transactional} simplesmente nao valeria — exatamente o bug que ja existe, sem teste que o
 * pegasse, em {@code PublicadorDaOutbox} e {@code ProcessadorDeWebhookEntrada} (ambos fazem
 * {@code ContextoDeServico.executarComo(nome, this::metodoTransacional)}). Por isso quem publica o
 * contexto e {@link com.synapse.crm.atendimento.infrastructure.tempo_real.RedisSubscriberDeAtendimento},
 * chamando este metodo de fora — uma chamada externa de verdade, que passa pelo proxy.
 *
 * <p>{@link com.synapse.crm.atendimento.domain.atendimento.Atendimento#visivelPara} e a mesma regra
 * que a politica SQL aplica — sob contexto de servico a linha vem sem filtro, e este metodo aplica a
 * decisao de visibilidade em Java, sem round-trip extra.
 */
@Service
public class RevalidarAssinaturaTempoRealUseCase {

    private final AtendimentoRepositorio atendimentos;
    private final ParticipacaoAtendimentoRepositorio participacoes;

    public RevalidarAssinaturaTempoRealUseCase(AtendimentoRepositorio atendimentos, ParticipacaoAtendimentoRepositorio participacoes) {
        this.atendimentos = atendimentos; this.participacoes = participacoes;
    }

    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER, readOnly = true)
    public boolean aindaValida(UUID atendimentoId, UUID usuarioId, PapelUsuario papel) {
        return atendimentos
                .porId(atendimentoId)
                .map(atendimento -> atendimento.visivelPara(usuarioId, papel))
                .orElseGet(() -> participacoes.eParticipanteAtivo(atendimentoId, usuarioId));
    }
}
