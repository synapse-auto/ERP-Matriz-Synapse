package com.synapse.crm.campanhas.application;

import java.time.Clock;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import com.synapse.crm.campanhas.domain.Campanha;
import com.synapse.crm.campanhas.domain.StatusDaCampanha;
import com.synapse.crm.campanhas.domain.TransicaoDeStatusInvalidaException;
import com.synapse.crm.sharedkernel.auditoria.Auditable;

/** O assistente salva o rascunho a cada passo. So campanha ainda RASCUNHO muda de conteudo. */
@Service
public class AtualizarRascunhoUseCase {

    private final MontadorDeCampanha montador;
    private final CampanhaRepositorio campanhas;
    private final DisponibilidadeDeCampanhas disponibilidade;
    private final TransacoesDeCampanha transacoes;

    public AtualizarRascunhoUseCase(
            MontadorDeCampanha montador,
            CampanhaRepositorio campanhas,
            DisponibilidadeDeCampanhas disponibilidade,
            TransacoesDeCampanha transacoes,
            Clock relogio) {
        this.montador = montador;
        this.campanhas = campanhas;
        this.disponibilidade = disponibilidade;
        this.transacoes = transacoes;
    }

    @PreAuthorize(PermissoesDeCampanha.ESCRITA)
    @Auditable(acao = "ATUALIZAR_RASCUNHO_CAMPANHA", entidadeTipo = "CAMPANHA", capturarDados = false)
    public Campanha executar(UUID id, PedidoDeCampanha pedido) {
        disponibilidade.exigir();
        Campanha atual = transacoes.noChatSomenteLeitura(() -> campanhas.porId(id))
                .orElseThrow(() -> new CampanhaNaoEncontradaException(id));
        exigirRascunho(atual);
        Campanha nova = montador.montar(id, pedido, atual.criadaPor(), atual.criadaEm());
        transacoes.noChatSemRetorno(() -> {
            Campanha travada = campanhas.bloquearPorId(id).orElseThrow(() -> new CampanhaNaoEncontradaException(id));
            exigirRascunho(travada);
            campanhas.atualizar(nova);
        });
        return nova;
    }

    private static void exigirRascunho(Campanha campanha) {
        if (campanha.status() != StatusDaCampanha.RASCUNHO) {
            throw new TransicaoDeStatusInvalidaException(campanha.status(), "editar o conteudo de");
        }
    }
}
