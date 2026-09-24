package com.synapse.crm.atendimento.domain.mensagem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * E207: o cliente mandou um anexo que o provedor nunca entregou. A mensagem entra no historico sem
 * arquivo, identificada pelos metadados — mas midia sem arquivo e sem metadados continua invalida.
 */
class MensagemMidiaSemArquivoTest {

    private static final Instant QUANDO = Instant.parse("2026-09-22T18:17:42Z");
    private static final String METADADOS = "{\"indisponivel\":true,\"nome\":\"exame.pdf\"}";

    @ParameterizedTest
    @EnumSource(value = TipoMensagem.class, names = {"IMAGEM", "AUDIO", "VIDEO", "DOCUMENTO"})
    void aceitaMidiaSemArquivoQuandoOsMetadadosDizemOQueEra(TipoMensagem tipo) {
        Mensagem mensagem = Mensagem.midia(
                UUID.randomUUID(), UUID.randomUUID(), Remetente.lead(), tipo, null, METADADOS, QUANDO);

        assertThat(mensagem.midiaUrl()).isNull();
        assertThat(mensagem.ehMidiaSemArquivo()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = TipoMensagem.class, names = {"IMAGEM", "AUDIO", "VIDEO", "DOCUMENTO"})
    void recusaMidiaSemArquivoESemMetadados(TipoMensagem tipo) {
        assertThatThrownBy(() -> Mensagem.midia(
                        UUID.randomUUID(), UUID.randomUUID(), Remetente.lead(), tipo, null, null, QUANDO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exige midiaUrl");
    }

    @ParameterizedTest
    @EnumSource(value = TipoMensagem.class, names = {"IMAGEM", "DOCUMENTO"})
    void midiaComArquivoNaoESemArquivo(TipoMensagem tipo) {
        Mensagem mensagem = Mensagem.midia(
                UUID.randomUUID(), UUID.randomUUID(), Remetente.lead(), tipo,
                "midia/abc.pdf", "{\"nome\":\"exame.pdf\"}", QUANDO);

        assertThat(mensagem.ehMidiaSemArquivo()).isFalse();
    }
}
