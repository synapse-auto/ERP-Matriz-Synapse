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
import com.synapse.crm.atendimento.domain.evento.ResponsavelDoContatoDefinido;

/** E231 (b): quem responde pelo lead vira o vinculado, tambem quando ja era o dono; colaborador nao. */
class VinculoDoLeadResponsavelTest {

    private static final UUID LEAD = UUID.randomUUID();
    private static final UUID ATENDIMENTO = UUID.randomUUID();
    private static final UUID DONO = UUID.randomUUID();
    private static final UUID OUTRO = UUID.randomUUID();

    private final Fake repositorio = new Fake();
    private final VinculoDoLeadListener listener = new VinculoDoLeadListener(repositorio);

    @Test
    @DisplayName("o dono que envia mensagem sem ser participante vira o vinculado, mesmo sem troca de dono")
    void donoQueEnviaGrava() {
        repositorio.ligado = true;

        listener.aoAcontecer(enviada(DONO, Optional.of(DONO), false, false));

        assertThat(repositorio.gravacoes).containsExactly("%s>%s:ENVIO_MANUAL".formatted(LEAD, DONO));
    }

    @Test
    @DisplayName("quem nao e o dono e nao assumiu (colaborador) nao vira vinculado; participante tambem nao")
    void colaboradorNaoGrava() {
        repositorio.ligado = true;

        listener.aoAcontecer(enviada(OUTRO, Optional.of(DONO), false, true)); // convidado: dono segue sendo DONO
        listener.aoAcontecer(enviada(OUTRO, Optional.of(DONO), false, false)); // nao e o dono e nao trocou
        listener.aoAcontecer(enviada(DONO, Optional.of(DONO), false, true)); // participante: nao e excecao
        listener.aoAcontecer(enviada(OUTRO, Optional.empty(), false, false)); // lead sem dono que nao mudou

        assertThat(repositorio.gravacoes).isEmpty();
    }

    @Test
    @DisplayName("participante que assumiu o lead (RN-CRM-06 trocou o dono) continua gravando")
    void participanteQueAssumiuGrava() {
        repositorio.ligado = true;

        listener.aoAcontecer(enviada(OUTRO, Optional.of(DONO), true, true));

        assertThat(repositorio.gravacoes).containsExactly("%s>%s:ENVIO_MANUAL".formatted(LEAD, OUTRO));
    }

    @Test
    @DisplayName("quem abriu o contato e ficou responsavel grava com origem CONTATO_ABERTO")
    void contatoAbertoGrava() {
        repositorio.ligado = true;

        listener.aoDefinirResponsavelDoContato(new ResponsavelDoContatoDefinido(LEAD, ATENDIMENTO, DONO, Instant.now()));

        assertThat(repositorio.gravacoes).containsExactly("%s>%s:CONTATO_ABERTO".formatted(LEAD, DONO));
    }

    @Test
    @DisplayName("chave desligada: nem o dono que envia nem o contato aberto gravam")
    void chaveDesligadaNaoGrava() {
        repositorio.ligado = false;

        listener.aoAcontecer(enviada(DONO, Optional.of(DONO), false, false));
        listener.aoDefinirResponsavelDoContato(new ResponsavelDoContatoDefinido(LEAD, ATENDIMENTO, DONO, Instant.now()));

        assertThat(repositorio.gravacoes).isEmpty();
    }

    private static EventoDeAtendimento enviada(
            UUID remetente, Optional<UUID> donoAnterior, boolean transferiu, boolean participante) {
        return new EventoDeAtendimento.MensagemEnviada(
                LEAD, "Lead", ATENDIMENTO, UUID.randomUUID(), remetente, donoAnterior, transferiu, participante, Instant.now());
    }

    private static final class Fake implements VinculoDoLeadRepositorio {
        boolean ligado;
        final List<String> gravacoes = new ArrayList<>();

        @Override
        public Optional<UUID> vinculadoDoAtendimento(UUID atendimentoId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean habilitado() {
            return ligado;
        }

        @Override
        public void registrar(UUID leadId, UUID atendenteId, String origem) {
            gravacoes.add("%s>%s:%s".formatted(leadId, atendenteId, origem));
        }
    }
}
