package com.synapse.crm.atendimento.infrastructure.canal;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class AacAdtsDeIsoBmffTest {

    private static final byte[] FRAME_AAC = {0x21, 0x10, 0x04, 0x60, (byte) 0x8C, 0x1C};

    @Test
    void arquivoSemMoofNaoEConvertido() {
        byte[] m4a = concatenar(
                caixa("ftyp", "M4A isom".getBytes(StandardCharsets.US_ASCII)),
                caixa("moov", new byte[0]),
                caixa("mdat", FRAME_AAC));

        assertThat(AacAdtsDeIsoBmff.contemMoof(m4a)).isFalse();
        assertThat(AacAdtsDeIsoBmff.extrairSeFragmentado(m4a)).isEmpty();
    }

    @Test
    void fmp4ComUmFrameViraAdts() {
        byte[] fmp4 = fmp4ComUmFrame(FRAME_AAC);

        assertThat(AacAdtsDeIsoBmff.contemMoof(fmp4)).isTrue();
        Optional<byte[]> aac = AacAdtsDeIsoBmff.extrairSeFragmentado(fmp4);

        assertThat(aac).isPresent();
        byte[] bytes = aac.get();
        assertThat(bytes[0]).isEqualTo((byte) 0xFF);
        assertThat(bytes[1]).isEqualTo((byte) 0xF1);
        byte[] payload = new byte[FRAME_AAC.length];
        System.arraycopy(bytes, 7, payload, 0, FRAME_AAC.length);
        assertThat(payload).isEqualTo(FRAME_AAC);
        int tamanhoAdts = ((bytes[3] & 0x03) << 11) | ((bytes[4] & 0xFF) << 3) | ((bytes[5] >> 5) & 0x07);
        assertThat(tamanhoAdts).isEqualTo(7 + FRAME_AAC.length);
    }

    @Test
    void lixoNaoQuebraAExtracao() {
        assertThat(AacAdtsDeIsoBmff.extrairSeFragmentado(new byte[] {1, 2, 3})).isEmpty();
        assertThat(AacAdtsDeIsoBmff.extrairSeFragmentado(null)).isEmpty();
    }

    static byte[] fmp4ComUmFrame(byte[] frame) {
        byte[] esds = caixa(
                "esds",
                concatenar(
                        new byte[] {0, 0, 0, 0},
                        new byte[] {
                            0x03, 0x16, 0x00, 0x00, 0x00, 0x04, 0x11, 0x40, 0x15, 0x00, 0x00, 0x00,
                            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x05, 0x02, 0x12, 0x08
                        }));
        byte[] mp4aPayload = concatenar(new byte[28], esds);
        byte[] mp4a = caixa("mp4a", mp4aPayload);
        byte[] stsd = caixa("stsd", concatenar(new byte[] {0, 0, 0, 0, 0, 0, 0, 1}, mp4a));
        byte[] moov = caixa("moov", caixa("trak", caixa("mdia", caixa("minf", caixa("stbl", stsd)))));

        byte[] trunPayload = new byte[] {
            0, 0, 0x02, 0x01,
            0, 0, 0, 1,
            0, 0, 0, 0,
            0, 0, 0, (byte) frame.length
        };
        byte[] trun = caixa("trun", trunPayload);
        byte[] traf = caixa("traf", concatenar(tfhdDefaultBaseIsMoof(), trun));
        byte[] moofSemOffset = caixa("moof", traf);
        int dataOffset = moofSemOffset.length + 8;
        trunPayload[8] = (byte) ((dataOffset >> 24) & 0xFF);
        trunPayload[9] = (byte) ((dataOffset >> 16) & 0xFF);
        trunPayload[10] = (byte) ((dataOffset >> 8) & 0xFF);
        trunPayload[11] = (byte) (dataOffset & 0xFF);
        byte[] moof = caixa("moof", caixa("traf", concatenar(tfhdDefaultBaseIsMoof(), caixa("trun", trunPayload))));
        byte[] mdat = caixa("mdat", frame);
        return concatenar(caixa("ftyp", "isomiso5".getBytes(StandardCharsets.US_ASCII)), moov, moof, mdat);
    }

    private static byte[] tfhdDefaultBaseIsMoof() {
        return caixa("tfhd", new byte[] {0, 0x02, 0x00, 0x00, 0, 0, 0, 1});
    }

    private static byte[] caixa(String tipo, byte[] payload) {
        int tamanho = 8 + payload.length;
        byte[] out = new byte[tamanho];
        out[0] = (byte) ((tamanho >> 24) & 0xFF);
        out[1] = (byte) ((tamanho >> 16) & 0xFF);
        out[2] = (byte) ((tamanho >> 8) & 0xFF);
        out[3] = (byte) (tamanho & 0xFF);
        byte[] nome = tipo.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(nome, 0, out, 4, 4);
        System.arraycopy(payload, 0, out, 8, payload.length);
        return out;
    }

    private static byte[] concatenar(byte[]... partes) {
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        try {
            for (byte[] parte : partes) {
                saida.write(parte);
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return saida.toByteArray();
    }
}
