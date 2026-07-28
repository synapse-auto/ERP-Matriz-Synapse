package com.synapse.crm.app.atendimento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.synapse.crm.atendimento.domain.atendimento.Atendimento;
import com.synapse.crm.atendimento.domain.atendimento.AtendimentoJaFinalizadoException;
import com.synapse.crm.atendimento.domain.atendimento.StatusAtendimento;
import com.synapse.crm.atendimento.domain.mensagem.Mensagem;
import com.synapse.crm.atendimento.domain.mensagem.Remetente;
import com.synapse.crm.atendimento.domain.mensagem.RemetenteTipo;
import com.synapse.crm.atendimento.domain.mensagem.StatusEntrega;
import com.synapse.crm.atendimento.domain.mensagem.TipoMensagem;

/**
 * As regras dos agregados, sem banco e sem Spring.
 *
 * <p>Estado terminal que nao e terminal, e remetente sem identidade, sao os dois erros que passariam
 * despercebidos em teste de integracao — o banco aceitaria os dois de bom grado.
 */
class AtendimentoTest {

    private static final UUID LEAD = UUID.randomUUID();
    private static final UUID ANA = UUID.randomUUID();
    private static final UUID BRUNO = UUID.randomUUID();
    private static final Instant AGORA = Instant.parse("2026-07-28T12:00:00Z");

    @Test
    @DisplayName("atendimento nasce com a IA e sem dono")
    void abrir_semAtendente_ficaComIa() {
        Atendimento novo = Atendimento.abrirComIa(UUID.randomUUID(), LEAD, null, null, AGORA);

        assertThat(novo.status()).isEqualTo(StatusAtendimento.EM_IA);
        assertThat(novo.atendenteId()).isNull();
        assertThat(novo.estaAberto()).isTrue();
    }

    @Test
    @DisplayName("transferir troca o dono e tira a conversa da IA")
    void transferir_paraAtendente_mudaDonoEStatus() {
        Atendimento comAna =
                Atendimento.abrirComIa(UUID.randomUUID(), LEAD, null, null, AGORA).transferirPara(ANA);

        assertThat(comAna.pertenceA(ANA)).isTrue();
        assertThat(comAna.pertenceA(BRUNO)).isFalse();
        assertThat(comAna.status()).isEqualTo(StatusAtendimento.EM_ATENDIMENTO);
    }

    /** Imutabilidade: a transicao devolve outro objeto e o original nao muda. */
    @Test
    @DisplayName("transicao nao altera a instancia anterior")
    void transicao_naoMutaOOriginal() {
        Atendimento original = Atendimento.abrirComIa(UUID.randomUUID(), LEAD, null, null, AGORA);

        original.transferirPara(ANA);

        assertThat(original.atendenteId()).isNull();
        assertThat(original.status()).isEqualTo(StatusAtendimento.EM_IA);
    }

    @Test
    @DisplayName("finalizar duas vezes falha em vez de virar no-op")
    void finalizar_duasVezes_recusa() {
        Atendimento finalizado =
                Atendimento.abrirComIa(UUID.randomUUID(), LEAD, null, null, AGORA).finalizar(AGORA);

        assertThatThrownBy(() -> finalizado.finalizar(AGORA))
                .isInstanceOf(AtendimentoJaFinalizadoException.class);
    }

    /** Sem isso, uma conversa encerrada voltaria a aceitar dono e a timeline mentiria. */
    @Test
    @DisplayName("atendimento finalizado nao aceita transferencia")
    void transferir_aposFinalizar_recusa() {
        Atendimento finalizado = Atendimento.abrirComIa(UUID.randomUUID(), LEAD, null, null, AGORA)
                .transferirPara(ANA)
                .finalizar(AGORA);

        assertThatThrownBy(() -> finalizado.transferirPara(BRUNO))
                .isInstanceOf(AtendimentoJaFinalizadoException.class);
    }

    /**
     * A RN-CRM-06 usa o id do remetente para decidir de quem passa a ser o lead. Um
     * {@code ATENDENTE} sem id transferiria o lead para ninguem e ele sumiria da agenda de todos.
     */
    @Test
    @DisplayName("remetente ATENDENTE sem id e rejeitado")
    void remetente_atendenteSemId_recusa() {
        assertThatThrownBy(() -> new Remetente(RemetenteTipo.ATENDENTE, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exige o id");
    }

    @Test
    @DisplayName("remetente LEAD com id e rejeitado")
    void remetente_leadComId_recusa() {
        assertThatThrownBy(() -> new Remetente(RemetenteTipo.LEAD, ANA))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nao tem id");
    }

    @Test
    @DisplayName("mensagem de midia sem url e rejeitada")
    void mensagem_midiaSemUrl_recusa() {
        assertThatThrownBy(() -> new Mensagem(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        Remetente.lead(),
                        TipoMensagem.IMAGEM,
                        null,
                        null,
                        null,
                        StatusEntrega.ENVIADO,
                        AGORA))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exige midiaUrl");
    }

    /** {@code enviadoEm} e a chave de particao: sem ele o Postgres nao sabe onde gravar. */
    @Test
    @DisplayName("mensagem sem enviadoEm e rejeitada")
    void mensagem_semEnviadoEm_recusa() {
        assertThatThrownBy(() ->
                        Mensagem.texto(UUID.randomUUID(), UUID.randomUUID(), Remetente.lead(), "oi", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("chave de particao");
    }
}
