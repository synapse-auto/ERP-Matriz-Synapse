package com.synapse.crm.atendimento.application.painel;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.sharedkernel.identidade.UsuarioAutenticado;
import com.synapse.crm.sharedkernel.identidade.UsuarioContext;
import com.synapse.crm.sharedkernel.persistencia.Pools;

/**
 * A lista de conversas que alimenta a tela de Atendimentos.
 *
 * <p>Quem pede nunca escolhe "quero ver de todo mundo" — o papel decide isso, sempre a partir do
 * {@link UsuarioContext}.
 *
 * <p>E225 (PR 5): uma listagem que leva mais que {@code synapse.observabilidade.listagem.lenta-acima-de} (padrao 1 s) grava
 * um WARN {@code [LISTAGEM_PAINEL_LENTA]} com papel, aba, forma da pagina, duracao e o estado do pool do chat naquele
 * instante. A duracao e a da execucao da consulta, ja com a conexao em maos: a espera por conexao aparece em
 * {@code poolEsperando} e no log por minuto do pool. Sem dado pessoal: nem nome, nem id de usuario ou de lead.
 */
@Service
public class ListarAtendimentosVisiveisUseCase {

    static final String MARCADOR_LENTA = "[LISTAGEM_PAINEL_LENTA]";
    private static final Logger log = LoggerFactory.getLogger(ListarAtendimentosVisiveisUseCase.class);

    private final PainelDeAtendimentosRepositorio painel;
    private final UsuarioContext usuarioContext;
    private final MedidorDoPoolDoChat medidorDoPool;
    private final Clock relogio;
    private final Duration limiteDeLentidao;

    public ListarAtendimentosVisiveisUseCase(
            PainelDeAtendimentosRepositorio painel,
            UsuarioContext usuarioContext,
            MedidorDoPoolDoChat medidorDoPool,
            Clock relogio,
            @Value("${synapse.observabilidade.listagem.lenta-acima-de}") Duration limiteDeLentidao) {
        this.painel = painel;
        this.usuarioContext = usuarioContext;
        this.medidorDoPool = medidorDoPool;
        this.relogio = relogio;
        this.limiteDeLentidao = limiteDeLentidao;
    }

    @PreAuthorize("isAuthenticated()")
    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER, readOnly = true)
    public List<CartaoAtendimento> executar(VisaoAtendimento visao) {
        UsuarioAutenticado atual = usuarioContext.atual();
        visao.exigirAcesso(atual);
        boolean restritoAoProprioAtendente = !atual.enxergaTodosOsLeads();
        return medir(atual, visao, "lista-simples", () -> painel.listar(visao, atual.id(), restritoAoProprioAtendente));
    }

    @PreAuthorize("isAuthenticated()")
    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER, readOnly = true)
    public List<CartaoAtendimento> executarPaginado(VisaoAtendimento visao, int limite,
            boolean depoisSemAtendimentoAberto, Instant depoisDe, UUID depoisDoId) {
        return executarPaginado(visao, limite, depoisSemAtendimentoAberto, depoisDe, depoisDoId, null);
    }

    @PreAuthorize("isAuthenticated()")
    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER, readOnly = true)
    public List<CartaoAtendimento> executarPaginado(VisaoAtendimento visao, int limite,
            boolean depoisSemAtendimentoAberto, Instant depoisDe, UUID depoisDoId,
            UUID filtroAtendenteId) {
        UsuarioAutenticado atual = usuarioContext.atual();
        visao.exigirAcesso(atual);
        if (filtroAtendenteId != null && visao == VisaoAtendimento.FINALIZADOS
                && !atual.enxergaTodosOsLeads() && !atual.id().equals(filtroAtendenteId)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "atendente só pode filtrar os próprios atendimentos finalizados");
        }
        String forma = "paginada limite=" + limite + " cursor=" + (depoisDoId != null ? "sim" : "nao")
                + " filtroAtendente=" + (filtroAtendenteId != null ? "sim" : "nao");
        if (filtroAtendenteId == null) {
            return medir(atual, visao, forma, () -> painel.listarPaginado(visao, atual.id(), !atual.enxergaTodosOsLeads(),
                    depoisSemAtendimentoAberto, depoisDe, depoisDoId, limite));
        }
        return medir(atual, visao, forma, () -> painel.listarPaginado(visao, atual.id(), !atual.enxergaTodosOsLeads(),
                depoisSemAtendimentoAberto, depoisDe, depoisDoId, limite, filtroAtendenteId));
    }

    /** Executa a consulta e, se passou do limite, deixa o rastro. Falha da consulta passa direto, sem log daqui. */
    private List<CartaoAtendimento> medir(
            UsuarioAutenticado atual, VisaoAtendimento visao, String forma, Supplier<List<CartaoAtendimento>> consulta) {
        Instant inicio = relogio.instant();
        List<CartaoAtendimento> cartoes = consulta.get();
        Duration duracao = Duration.between(inicio, relogio.instant());
        if (duracao.compareTo(limiteDeLentidao) >= 0) {
            registrarLentidao(atual, visao, forma, duracao, cartoes.size());
        }
        return cartoes;
    }

    private void registrarLentidao(
            UsuarioAutenticado atual, VisaoAtendimento visao, String forma, Duration duracao, int cartoes) {
        String pool = medidorDoPool
                .estadoAtual()
                .map(estado -> "poolAtivas=" + estado.ativas() + " poolOciosas=" + estado.ociosas()
                        + " poolEsperando=" + estado.esperando() + " poolMaximo=" + estado.maximo())
                .orElse("pool=indisponivel");
        log.warn("{} papel={} aba={} pagina={} cartoes={} duracaoMs={} limiteMs={} {}",
                MARCADOR_LENTA, atual.papel(), visao, forma, cartoes, duracao.toMillis(), limiteDeLentidao.toMillis(), pool);
    }
}
