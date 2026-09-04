package com.synapse.crm.atendimento.domain.mensagem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * Invariantes de dominio para mensagens do tipo LOCALIZACAO.
 *
 * <p>Cobre a protecao que existia e nao protegia nada: LOCALIZACAO era aceita com
 * conteudo=null pelo construtor, mas explodia em runtime porque o invariante de
 * "texto exige conteudo" so excluia exigeMidia() e exigeOpcoes(). Com exigeMetadados()
 * o invariante foi estendido para aceitar LOCALIZACAO sem conteudo e sem midiaUrl,
 * desde que midiaMetadados esteja presente.
 */
class MensagemLocalizacaoTest {

    private static final UUID ID = UUID.randomUUID();
    private static final UUID ATENDIMENTO_ID = UUID.randomUUID();
    private static final Remetente REMETENTE = Remetente.lead();
    private static final Instant QUANDO = Instant.now();
    private static final String JSON_LOCALIZACAO =
            "{\"latitude\":-7.115,\"longitude\":-34.864,\"nome\":\"Parque\"}";

    @Test
    void aceitaLocalizacaoComMetadados() {
        Mensagem msg = Mensagem.midia(
                ID, ATENDIMENTO_ID, REMETENTE, TipoMensagem.LOCALIZACAO,
                null, JSON_LOCALIZACAO, QUANDO);
        assertThat(msg.tipo()).isEqualTo(TipoMensagem.LOCALIZACAO);
        assertThat(msg.midiaMetadados()).isEqualTo(JSON_LOCALIZACAO);
        assertThat(msg.conteudo()).isNull();
        assertThat(msg.midiaUrl()).isNull();
    }

    @Test
    void rejeitaLocalizacaoSemMetadados() {
        assertThatThrownBy(() -> Mensagem.midia(
                ID, ATENDIMENTO_ID, REMETENTE, TipoMensagem.LOCALIZACAO,
                null, null, QUANDO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exige midiaMetadados");
    }

    @Test
    void tipoLocalizacaoExigeMetadados() {
        assertThat(TipoMensagem.LOCALIZACAO.exigeMetadados()).isTrue();
        assertThat(TipoMensagem.LOCALIZACAO.exigeMidia()).isFalse();
        assertThat(TipoMensagem.LOCALIZACAO.exigeOpcoes()).isFalse();
    }
}
