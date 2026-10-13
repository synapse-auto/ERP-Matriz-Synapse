package com.synapse.crm.atendimento.infrastructure.vinculo;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.synapse.crm.atendimento.application.VinculoDoLeadRepositorio;
import com.synapse.crm.atendimento.domain.evento.EventoDeAtendimento;
import com.synapse.crm.core.domain.timeline.OrigemEvento;

/** Quando o vinculo e gravado: so ao receber/assumir, so com a chave ligada, e nunca ao devolver para a IA. */
class VinculoDoLeadListenerTest {

    private static final UUID LEAD = UUID.randomUUID();
    private static final UUID ATENDIMENTO = UUID.randomUUID();
    private static final UUID ATENDENTE = UUID.randomUUID();

    private final Fake repositorio = new Fake();
    private final VinculoDoLeadListener listener = new VinculoDoLeadListener(repositorio);

    @Test
    @DisplayName("transferencia para um humano grava o vinculo com origem TRANSFERENCIA")
    void transferenciaParaHumanoGrava() {
        repositorio.ligado = true;

        listener.aoAcontecer(transferido(ATENDENTE));

        assertThat(repositorio.gravacoes).containsExactly("%s>%s:TRANSFERENCIA".formatted(LEAD, ATENDENTE));
    }

    @Test
    @DisplayName("envio manual que trocou o dono (RN-CRM-06) grava com origem ENVIO_MANUAL")
    void envioQueTransferiuGrava() {
        repositorio.ligado = true;

        listener.aoAcontecer(enviada(true));

        assertThat(repositorio.gravacoes).containsExactly("%s>%s:ENVIO_MANUAL".formatted(LEAD, ATENDENTE));
    }

    @Test
    @DisplayName("devolver para a IA, envio que nao trocou o dono e mensagem recebida nao mexem no vinculo")
    void outrosEventosNaoGravam() {
        repositorio.ligado = true;

        listener.aoAcontecer(transferido(null));
        listener.aoAcontecer(enviada(false));
        listener.aoAcontecer(new EventoDeAtendimento.MensagemRecebida(LEAD, ATENDIMENTO, UUID.randomUUID(), false, Instant.now()));

        assertThat(repositorio.gravacoes).isEmpty();
        assertThat(repositorio.consultasDaChave).as("evento irrelevante nem consulta a chave").isZero();
    }

    @Test
    @DisplayName("chave desligada: nada e gravado")
    void chaveDesligadaNaoGrava() {
        repositorio.ligado = false;

        listener.aoAcontecer(transferido(ATENDENTE));
        listener.aoAcontecer(enviada(true));

        assertThat(repositorio.gravacoes).isEmpty();
    }

    private static EventoDeAtendimento transferido(UUID para) {
        return new EventoDeAtendimento.AtendimentoTransferido(
                LEAD, "Lead", ATENDIMENTO, null, para, null, OrigemEvento.AUTOMACAO, Instant.now());
    }

    private static EventoDeAtendimento enviada(boolean transferiu) {
        return new EventoDeAtendimento.MensagemEnviada(
                LEAD, "Lead", ATENDIMENTO, UUID.randomUUID(), ATENDENTE, Optional.empty(), transferiu, false, Instant.now());
    }

    private static final class Fake implements VinculoDoLeadRepositorio {
        boolean ligado;
        int consultasDaChave;
        final List<String> gravacoes = new ArrayList<>();

        @Override
        public Optional<UUID> vinculadoDoAtendimento(UUID atendimentoId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean habilitado() {
            consultasDaChave++;
            return ligado;
        }

        @Override
        public void registrar(UUID leadId, UUID atendenteId, String origem) {
            gravacoes.add("%s>%s:%s".formatted(leadId, atendenteId, origem));
        }
    }
}
