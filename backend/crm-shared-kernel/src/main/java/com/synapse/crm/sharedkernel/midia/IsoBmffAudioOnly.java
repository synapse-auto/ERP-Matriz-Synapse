package com.synapse.crm.sharedkernel.midia;

/**
 * Inspeção mínima e defensiva de trilhas ISO-BMFF (MP4/QuickTime). Não interpreta codecs nem executa
 * ferramentas externas: apenas confirma que existe trilha de áudio e nenhuma de vídeo.
 */
public final class IsoBmffAudioOnly {

    private static final int CABECALHO = 8;
    private static final int LIMITE_PROFUNDIDADE = 32;

    private IsoBmffAudioOnly() {}

    public static boolean ehAudioSemVideo(byte[] bytes) {
        if (bytes == null || bytes.length < CABECALHO) return false;
        Resultado resultado = new Resultado();
        if (!lerCaixas(bytes, 0, bytes.length, 0, resultado)) return false;
        return resultado.temAudio && !resultado.temVideo;
    }

    /**
     * MediaRecorder em alguns navegadores gera ISO-BMFF de áudio que o Tika rotula como
     * {@code video/quicktime} ou {@code video/mp4}. Se as trilhas forem só de áudio, trata como
     * {@code audio/mp4} — o mesmo critério do envio no atendimento.
     */
    public static String mimetypeDeAudioSeCamuflado(String mimetypeDetectado, byte[] bytes) {
        if (("video/quicktime".equals(mimetypeDetectado) || "video/mp4".equals(mimetypeDetectado))
                && ehAudioSemVideo(bytes)) {
            return "audio/mp4";
        }
        return mimetypeDetectado;
    }

    private static boolean lerCaixas(
            byte[] bytes, int inicio, int fim, int profundidade, Resultado resultado) {
        if (profundidade > LIMITE_PROFUNDIDADE) return false;
        int posicao = inicio;
        while (posicao < fim) {
            if (fim - posicao < CABECALHO) return false;
            long tamanho = inteiro32(bytes, posicao);
            int dados = posicao + CABECALHO;
            if (tamanho == 1) {
                if (fim - dados < 8) return false;
                tamanho = inteiro64(bytes, dados);
                dados += 8;
            } else if (tamanho == 0) {
                tamanho = fim - posicao;
            }
            if (tamanho < dados - posicao || tamanho > fim - posicao) return false;
            int caixaFim = (int) (posicao + tamanho);
            String tipo = new String(bytes, posicao + 4, 4, java.nio.charset.StandardCharsets.US_ASCII);
            if ("hdlr".equals(tipo)) {
                if (caixaFim - dados < 12) return false;
                int handler = dados + 8;
                String handlerTipo = new String(bytes, handler, 4, java.nio.charset.StandardCharsets.US_ASCII);
                resultado.temAudio |= "soun".equals(handlerTipo);
                resultado.temVideo |= "vide".equals(handlerTipo);
            }
            if (ehContainer(tipo)
                    && !lerCaixas(bytes, dados, caixaFim, profundidade + 1, resultado)) return false;
            posicao = caixaFim;
        }
        return posicao == fim;
    }

    private static boolean ehContainer(String tipo) {
        return switch (tipo) {
            case "moov", "trak", "mdia", "minf", "stbl", "edts", "dinf", "udta", "meta" -> true;
            default -> false;
        };
    }

    private static long inteiro32(byte[] bytes, int posicao) {
        return ((long) (bytes[posicao] & 0xFF) << 24)
                | ((long) (bytes[posicao + 1] & 0xFF) << 16)
                | ((long) (bytes[posicao + 2] & 0xFF) << 8)
                | (bytes[posicao + 3] & 0xFFL);
    }

    private static long inteiro64(byte[] bytes, int posicao) {
        long valor = 0;
        for (int i = 0; i < 8; i++) valor = (valor << 8) | (bytes[posicao + i] & 0xFFL);
        return valor;
    }

    private static final class Resultado {
        private boolean temAudio;
        private boolean temVideo;
    }
}
