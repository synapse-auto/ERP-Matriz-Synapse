package com.synapse.crm.campanhas.application;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import com.synapse.crm.campanhas.domain.Campanha;
import com.synapse.crm.sharedkernel.auditoria.Auditable;
import com.synapse.crm.sharedkernel.identidade.UsuarioContext;

/** Salva a campanha como rascunho. Nao envia nada e nao toca em destinatario. */
@Service
public class CriarCampanhaUseCase {

    private final MontadorDeCampanha montador;
    private final CampanhaRepositorio campanhas;
    private final DisponibilidadeDeCampanhas disponibilidade;
    private final TransacoesDeCampanha transacoes;
    private final UsuarioContext usuario;
    private final Clock relogio;

    public CriarCampanhaUseCase(
            MontadorDeCampanha montador,
            CampanhaRepositorio campanhas,
            DisponibilidadeDeCampanhas disponibilidade,
            TransacoesDeCampanha transacoes,
            UsuarioContext usuario,
            Clock relogio) {
        this.montador = montador;
        this.campanhas = campanhas;
        this.disponibilidade = disponibilidade;
        this.transacoes = transacoes;
        this.usuario = usuario;
        this.relogio = relogio;
    }

    @PreAuthorize(PermissoesDeCampanha.ESCRITA)
    @Auditable(acao = "CRIAR_CAMPANHA", entidadeTipo = "CAMPANHA", capturarDados = false)
    public Campanha executar(PedidoDeCampanha pedido) {
        disponibilidade.exigir();
        Instant agora = Instant.now(relogio);
        Campanha campanha = montador.montar(UUID.randomUUID(), pedido, usuario.atual().id(), agora);
        transacoes.noChatSemRetorno(() -> campanhas.inserir(campanha));
        return campanha;
    }
}
