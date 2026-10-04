package com.synapse.crm.campanhas.application;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.function.UnaryOperator;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.campanhas.domain.Campanha;
import com.synapse.crm.sharedkernel.auditoria.Auditable;
import com.synapse.crm.sharedkernel.persistencia.Pools;

/**
 * As acoes do administrador sobre uma campanha existente. Cada uma le a campanha com {@code FOR UPDATE},
 * aplica a transicao do dominio e grava: serializa com o ciclo de envio e com outra acao concorrente, e uma
 * transicao invalida lanca em vez de ser ignorada.
 */
@Service
public class ControleDaCampanhaUseCases {

    private final CampanhaRepositorio campanhas;
    private final DestinatarioRepositorio destinatarios;
    private final ConfiguracaoDeCampanhas configuracao;
    private final DisponibilidadeDeCampanhas disponibilidade;
    private final Clock relogio;

    public ControleDaCampanhaUseCases(
            CampanhaRepositorio campanhas,
            DestinatarioRepositorio destinatarios,
            ConfiguracaoDeCampanhas configuracao,
            DisponibilidadeDeCampanhas disponibilidade,
            Clock relogio) {
        this.campanhas = campanhas;
        this.destinatarios = destinatarios;
        this.configuracao = configuracao;
        this.disponibilidade = disponibilidade;
        this.relogio = relogio;
    }

    @PreAuthorize(PermissoesDeCampanha.OPERAR)
    @Auditable(acao = "PAUSAR_CAMPANHA", entidadeTipo = "CAMPANHA", capturarDados = false)
    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER)
    public Campanha pausar(UUID id) {
        return alterar(id, campanha -> campanha.pausar(agora()));
    }

    @PreAuthorize(PermissoesDeCampanha.OPERAR)
    @Auditable(acao = "RETOMAR_CAMPANHA", entidadeTipo = "CAMPANHA", capturarDados = false)
    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER)
    public Campanha retomar(UUID id) {
        return alterar(id, campanha -> campanha.retomar(agora()));
    }

    @PreAuthorize(PermissoesDeCampanha.OPERAR)
    @Auditable(acao = "CANCELAR_CAMPANHA", entidadeTipo = "CAMPANHA", capturarDados = false)
    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER)
    public Campanha cancelar(UUID id) {
        return alterar(id, campanha -> campanha.cancelar(agora()));
    }

    /** Vale no proximo ciclo de envio; o ritmo, quando informado, tambem. */
    @PreAuthorize(PermissoesDeCampanha.OPERAR)
    @Auditable(acao = "ALTERAR_LIMITE_CAMPANHA", entidadeTipo = "CAMPANHA", capturarDados = false)
    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER)
    public Campanha alterarLimite(UUID id, int limiteDiario, Integer ritmoPorMinuto) {
        int teto = configuracao.atuais().tetoDiarioDaInstancia();
        return alterar(id, campanha -> {
            Campanha comLimite = campanha.comLimiteDiario(limiteDiario, teto);
            return ritmoPorMinuto == null ? comLimite : comLimite.comRitmo(ritmoPorMinuto);
        });
    }

    /** Interruptor da campanha: para o envio no proximo ciclo (ou o religa), sem mudar o status. */
    @PreAuthorize(PermissoesDeCampanha.OPERAR)
    @Auditable(acao = "ALTERAR_INTERRUPTOR_CAMPANHA", entidadeTipo = "CAMPANHA", capturarDados = false)
    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER)
    public Campanha alterarInterruptor(UUID id, boolean desligada) {
        return alterar(id, campanha -> campanha.comDesligada(desligada));
    }

    /**
     * A pessoa conferiu no provedor o que aconteceu com o envio e o tira da lista. Nao reenvia: reenviar e
     * uma decisao humana, fora desta acao.
     */
    @PreAuthorize(PermissoesDeCampanha.CONFERIR)
    @Auditable(acao = "RESOLVER_CONFERENCIA_CAMPANHA", entidadeTipo = "CAMPANHA", capturarDados = false)
    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER)
    public boolean resolverConferencia(UUID id, UUID destinatarioId) {
        disponibilidade.exigir();
        campanhas.bloquearPorId(id).orElseThrow(() -> new CampanhaNaoEncontradaException(id));
        boolean resolvido = destinatarios.resolverConferencia(id, destinatarioId);
        if (resolvido) {
            campanhas.variarContadores(id, CampanhaRepositorio.Variacao.conferencia(-1));
        }
        return resolvido;
    }

    private Campanha alterar(UUID id, UnaryOperator<Campanha> transicao) {
        disponibilidade.exigir();
        Campanha atual = campanhas.bloquearPorId(id).orElseThrow(() -> new CampanhaNaoEncontradaException(id));
        Campanha nova = transicao.apply(atual);
        campanhas.atualizar(nova);
        return campanhas.porId(id).orElse(nova);
    }

    private Instant agora() {
        return Instant.now(relogio);
    }
}
