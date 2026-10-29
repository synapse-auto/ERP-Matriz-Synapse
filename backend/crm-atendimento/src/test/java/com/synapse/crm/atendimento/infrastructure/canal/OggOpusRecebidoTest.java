package com.synapse.crm.atendimento.infrastructure.canal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import com.synapse.crm.sharedkernel.midia.ValidadorDeOggOpus;

class OggOpusRecebidoTest {
    @Test void aceitaSemEosSomenteNoPerfilDeEntradaComIntegridade() throws Exception {
        byte[] bytes = fixture();
        assertThat(ValidadorDeOggOpus.ehValido(bytes)).isFalse();
        assertThat(OggOpusRecebido.semEosIntegro(bytes)).isTrue();
        assertThat(OggOpusRecebido.semEosIntegro(Arrays.copyOf(bytes, bytes.length - 1))).isFalse();
        bytes[bytes.length - 1] ^= 1;
        assertThat(OggOpusRecebido.semEosIntegro(bytes)).isFalse();
    }

    @Test void recusaPaginasAusentesCabecalhoFalsoEGranuleZero() throws Exception {
        byte[] bytes = fixture();
        bytes[61] ^= 1; // Serial divergente mesmo com CRC recalculado.
        recalcular(bytes);
        assertThat(OggOpusRecebido.semEosIntegro(bytes)).isFalse();
        bytes = fixture();
        bytes[28] = 'X';
        recalcular(bytes);
        assertThat(OggOpusRecebido.semEosIntegro(bytes)).isFalse();
        bytes = fixture();
        Arrays.fill(bytes, 97, 105, (byte) 0);
        recalcular(bytes);
        assertThat(OggOpusRecebido.semEosIntegro(bytes)).isFalse();
    }

    static byte[] fixture() throws Exception {
        try (var entrada = OggOpusRecebidoTest.class.getResourceAsStream("/midia/audio-sintetico.ogg")) {
            byte[] bytes = entrada.readAllBytes();
            for (int pos = 0; pos < bytes.length;) {
                bytes[pos + 5] &= ~4;
                int corpo = pos + 27 + (bytes[pos + 26] & 255);
                int fim = corpo;
                for (int i = pos + 27; i < corpo; i++) fim += bytes[i] & 255;
                pos = fim;
            }
            recalcular(bytes);
            return bytes;
        }
    }

    static void recalcular(byte[] bytes) {
        for (int pos = 0; pos < bytes.length;) {
            int corpo = pos + 27 + (bytes[pos + 26] & 255);
            int fim = corpo;
            for (int i = pos + 27; i < corpo; i++) fim += bytes[i] & 255;
            int crc = 0;
            for (int i = pos; i < fim; i++) {
                crc ^= (i >= pos + 22 && i < pos + 26 ? 0 : bytes[i] & 255) << 24;
                for (int bit = 0; bit < 8; bit++) crc = (crc << 1) ^ (crc < 0 ? 0x04c11db7 : 0);
            }
            for (int i = 0; i < 4; i++) bytes[pos + 22 + i] = (byte) (crc >>> (8 * i));
            pos = fim;
        }
    }
}
