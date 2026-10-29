package com.synapse.crm.atendimento.infrastructure.canal;

import java.nio.charset.StandardCharsets;

/** Perfil de entrada Z-API, com EOS opcional, sem alterar bytes ou regras de upload. */
final class OggOpusRecebido {
    private OggOpusRecebido() {}

    static boolean integro(byte[] bytes) {
        int pos = 0, sequencia = 0, pacotes = 0;
        long serial = -1, granuleFinal = 0;
        while (pos < bytes.length) {
            if (bytes.length - pos < 27 || !assinatura(bytes, pos, "OggS") || bytes[pos + 4] != 0) return false;
            // Perfil estreito: um stream, sem chaining, continuacoes ou EOS intermediario.
            int flags = bytes[pos + 5] & 255;
            if (sequencia == 0 ? flags != 2 : (flags != 0 && flags != 4)) return false;
            long serialAtual = inteiro(bytes, pos + 14);
            if (sequencia == 0) serial = serialAtual;
            if (serialAtual != serial || inteiro(bytes, pos + 18) != sequencia) return false;
            int segmentos = bytes[pos + 26] & 255;
            int corpo = pos + 27 + segmentos;
            if (segmentos == 0 || corpo > bytes.length || (bytes[corpo - 1] & 255) == 255) return false;
            int fim = corpo;
            for (int i = pos + 27; i < corpo; i++) fim += bytes[i] & 255;
            if (fim > bytes.length || inteiro(bytes, pos + 22) != crc(bytes, pos, fim)) return false;
            if (flags == 4 && fim != bytes.length) return false;
            int inicioPacote = corpo, tamanho = 0;
            for (int i = pos + 27; i < corpo; i++) {
                int segmento = bytes[i] & 255;
                tamanho += segmento;
                if (segmento == 255) continue;
                if (tamanho == 0) return false;
                if (pacotes == 0 && (tamanho != 19 || !assinatura(bytes, inicioPacote, "OpusHead")
                        || bytes[inicioPacote + 8] != 1 || bytes[inicioPacote + 9] != 1
                        || bytes[inicioPacote + 18] != 0)) return false;
                // RFC 7845 5.1: input sample rate e metadado informativo (inclusive zero),
                // nao e taxa de reproducao e nao decide a validade dos pacotes Opus.
                if (pacotes == 1 && !tags(bytes, inicioPacote, tamanho)) return false;
                pacotes++;
                inicioPacote += tamanho;
                tamanho = 0;
            }
            long granule = 0;
            for (int i = 7; i >= 0; i--) granule = (granule << 8) | (bytes[pos + 6 + i] & 255L);
            if (granule < granuleFinal) return false;
            granuleFinal = granule;
            sequencia++;
            pos = fim;
        }
        return pacotes >= 3 && sequencia >= 3 && granuleFinal > 0;
    }

    private static boolean tags(byte[] bytes, int pos, int tamanho) {
        int fim = pos + tamanho;
        if (tamanho < 16 || !assinatura(bytes, pos, "OpusTags")) return false;
        long vendor = inteiro(bytes, pos + 8);
        if (vendor > tamanho - 16) return false;
        int cursor = pos + 12 + (int) vendor;
        long comentarios = inteiro(bytes, cursor);
        cursor += 4;
        if (comentarios > (fim - cursor) / 4) return false;
        for (long i = 0; i < comentarios; i++) {
            if (fim - cursor < 4) return false;
            long tamanhoComentario = inteiro(bytes, cursor);
            cursor += 4;
            if (tamanhoComentario > fim - cursor) return false;
            cursor += (int) tamanhoComentario;
        }
        return true;
    }

    private static boolean assinatura(byte[] bytes, int pos, String texto) {
        byte[] esperado = texto.getBytes(StandardCharsets.US_ASCII);
        if (bytes.length - pos < esperado.length) return false;
        for (int i = 0; i < esperado.length; i++) if (bytes[pos + i] != esperado[i]) return false;
        return true;
    }

    private static long inteiro(byte[] bytes, int pos) {
        return (bytes[pos] & 255L) | ((bytes[pos + 1] & 255L) << 8)
                | ((bytes[pos + 2] & 255L) << 16) | ((bytes[pos + 3] & 255L) << 24);
    }

    private static long crc(byte[] bytes, int inicio, int fim) {
        int crc = 0;
        for (int i = inicio; i < fim; i++) {
            int valor = i >= inicio + 22 && i < inicio + 26 ? 0 : bytes[i] & 255;
            crc ^= valor << 24;
            for (int bit = 0; bit < 8; bit++) crc = (crc << 1) ^ (crc < 0 ? 0x04c11db7 : 0);
        }
        return Integer.toUnsignedLong(crc);
    }
}
