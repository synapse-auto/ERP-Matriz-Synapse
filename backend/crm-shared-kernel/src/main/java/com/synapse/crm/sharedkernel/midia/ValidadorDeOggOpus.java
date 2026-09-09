package com.synapse.crm.sharedkernel.midia;

/** Valida a estrutura mínima de um contêiner OGG/Opus utilizável como nota de voz. */
public final class ValidadorDeOggOpus {

    private static final byte[] OGG = {'O', 'g', 'g', 'S'};
    private static final byte[] OPUS_HEAD = {'O', 'p', 'u', 's', 'H', 'e', 'a', 'd'};

    private ValidadorDeOggOpus() {}

    /**
     * Exige páginas completas, cabeçalho OpusHead, EOS e granule position final positivo. Só a
     * assinatura inicial não é suficiente: um OGG truncado faria o WhatsApp mostrar 0:00.
     */
    public static boolean ehValido(byte[] bytes) {
        if (bytes == null || bytes.length < 28) return false;

        int deslocamento = 0;
        boolean temOpusHead = false;
        int ultimasFlags = 0;
        long ultimoGranule = 0;
        while (deslocamento < bytes.length) {
            int restante = bytes.length - deslocamento;
            if (restante < 27 || !temAssinaturaEm(bytes, deslocamento, OGG)) return false;
            if (bytes[deslocamento + 4] != 0) return false;

            int quantidadeSegmentos = bytes[deslocamento + 26] & 0xFF;
            int inicioTabela = deslocamento + 27;
            if (bytes.length - inicioTabela < quantidadeSegmentos) return false;

            int tamanhoCorpo = 0;
            for (int indice = 0; indice < quantidadeSegmentos; indice++) {
                tamanhoCorpo += bytes[inicioTabela + indice] & 0xFF;
            }
            int inicioCorpo = inicioTabela + quantidadeSegmentos;
            if (bytes.length - inicioCorpo < tamanhoCorpo) return false;

            if (!temOpusHead && contém(bytes, inicioCorpo, tamanhoCorpo, OPUS_HEAD)) {
                int inicioCabecalho = inicioDe(bytes, inicioCorpo, tamanhoCorpo, OPUS_HEAD);
                if (!cabecalhoOpusCompativel(bytes, inicioCabecalho, inicioCorpo + tamanhoCorpo)) {
                    return false;
                }
                temOpusHead = true;
            }
            ultimasFlags = bytes[deslocamento + 5] & 0xFF;
            ultimoGranule = lerGranulePosition(bytes, deslocamento);
            deslocamento = inicioCorpo + tamanhoCorpo;
        }
        // EOS precisa ser a última página e carregar a posição final positiva. Aceitar um EOS
        // intermediário deixaria páginas posteriores sem duração confiável para o WhatsApp.
        return temOpusHead && (ultimasFlags & 0x04) != 0 && ultimoGranule > 0;
    }

    private static boolean contém(byte[] bytes, int inicio, int tamanho, byte[] trecho) {
        if (tamanho < trecho.length) return false;
        for (int deslocamento = inicio; deslocamento <= inicio + tamanho - trecho.length; deslocamento++) {
            boolean igual = true;
            for (int indice = 0; indice < trecho.length; indice++) {
                if (bytes[deslocamento + indice] != trecho[indice]) {
                    igual = false;
                    break;
                }
            }
            if (igual) return true;
        }
        return false;
    }

    private static int inicioDe(byte[] bytes, int inicio, int tamanho, byte[] trecho) {
        for (int deslocamento = inicio; deslocamento <= inicio + tamanho - trecho.length; deslocamento++) {
            boolean igual = true;
            for (int indice = 0; indice < trecho.length; indice++) {
                if (bytes[deslocamento + indice] != trecho[indice]) {
                    igual = false;
                    break;
                }
            }
            if (igual) return deslocamento;
        }
        return -1;
    }

    /** OpusHead: versão 1, mono e taxa de amostragem declarada de 48 kHz. */
    private static boolean cabecalhoOpusCompativel(byte[] bytes, int inicio, int fimDoCorpo) {
        if (inicio < 0 || fimDoCorpo - inicio < 19) return false;
        if (bytes[inicio + 8] != 1 || bytes[inicio + 9] != 1) return false;
        int taxa = (bytes[inicio + 12] & 0xFF)
                | ((bytes[inicio + 13] & 0xFF) << 8)
                | ((bytes[inicio + 14] & 0xFF) << 16)
                | ((bytes[inicio + 15] & 0xFF) << 24);
        return taxa == 48000;
    }

    private static boolean temAssinaturaEm(byte[] bytes, int inicio, byte[] assinatura) {
        if (inicio < 0 || bytes.length - inicio < assinatura.length) return false;
        for (int indice = 0; indice < assinatura.length; indice++) {
            if (bytes[inicio + indice] != assinatura[indice]) return false;
        }
        return true;
    }

    private static long lerGranulePosition(byte[] bytes, int inicio) {
        long valor = 0;
        for (int indice = 0; indice < Long.BYTES; indice++) {
            valor |= (bytes[inicio + 6 + indice] & 0xFFL) << (8 * indice);
        }
        return valor;
    }
}
