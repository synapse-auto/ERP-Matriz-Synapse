package com.synapse.crm.campanhas.application;

import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import com.synapse.crm.campanhas.domain.Campanha;
import com.synapse.crm.campanhas.domain.StatusDaCampanha;
import com.synapse.crm.campanhas.domain.TransicaoDeStatusInvalidaException;
import com.synapse.crm.sharedkernel.auditoria.Auditable;

/**
 * Dispara ou agenda a campanha. O template e conferido no provedor ANTES da transacao (rede fora do banco):
 * o status dele muda do lado da Meta, e uma campanha nao nasce sobre um template pausado.
 */
@Service
public class IniciarCampanhaUseCase {

    private final CampanhaRepositorio campanhas;
    private final MontadorDeCampanha montador;
    private final ConfirmarInicioDaCampanha confirmacao;
    private final DisponibilidadeDeCampanhas disponibilidade;
    private final TransacoesDeCampanha transacoes;

    public IniciarCampanhaUseCase(
            CampanhaRepositorio campanhas,
            MontadorDeCampanha montador,
            ConfirmarInicioDaCampanha confirmacao,
            DisponibilidadeDeCampanhas disponibilidade,
            TransacoesDeCampanha transacoes) {
        this.campanhas = campanhas;
        this.montador = montador;
        this.confirmacao = confirmacao;
        this.disponibilidade = disponibilidade;
        this.transacoes = transacoes;
    }

    @PreAuthorize(PermissoesDeCampanha.ESCRITA)
    @Auditable(acao = "INICIAR_CAMPANHA", entidadeTipo = "CAMPANHA", capturarDados = false)
    public Campanha executar(UUID id) {
        disponibilidade.exigir();
        Campanha atual = transacoes.noChatSomenteLeitura(() -> campanhas.porId(id))
                .orElseThrow(() -> new CampanhaNaoEncontradaException(id));
        if (atual.status() != StatusDaCampanha.RASCUNHO) {
            throw new TransicaoDeStatusInvalidaException(atual.status(), "iniciar");
        }
        montador.exigirTemplateUtilizavel(atual.template().nome(), atual.template().idioma());
        return confirmacao.executar(id);
    }
}
