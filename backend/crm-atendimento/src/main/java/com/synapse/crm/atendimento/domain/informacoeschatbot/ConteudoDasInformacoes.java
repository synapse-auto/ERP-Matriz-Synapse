package com.synapse.crm.atendimento.domain.informacoeschatbot;

/**
 * Texto que a Automacao entrega para o card de informacoes do chatbot, ja normalizado.
 *
 * <p>E texto puro: o CRM nunca interpreta HTML, Markdown ou o formato interno do n8n. A normalizacao e
 * parte do contrato de idempotencia — o mesmo conteudo reenviado com outra quebra de linha tem de
 * produzir o mesmo valor, senao o retry viraria "chave reutilizada com conteudo diferente".
 *
 * <p>Sem tamanho padrao: o limite e configuracao da instancia e chega por parametro.
 */
public record ConteudoDasInformacoes(String texto) {

    public ConteudoDasInformacoes {
        if (texto == null || texto.isBlank()) {
            throw new ConteudoDasInformacoesInvalidoException("o conteudo das informacoes e obrigatorio");
        }
    }

    /**
     * @param bruto o que o n8n enviou
     * @param tamanhoMaximo limite em caracteres, depois de aparar e normalizar as quebras de linha
     * @throws ConteudoDasInformacoesInvalidoException vazio, com caractere nulo ou acima do limite
     */
    public static ConteudoDasInformacoes de(String bruto, int tamanhoMaximo) {
        if (bruto == null) {
            throw new ConteudoDasInformacoesInvalidoException("o conteudo das informacoes e obrigatorio");
        }
        if (bruto.indexOf('\u0000') >= 0) {
            throw new ConteudoDasInformacoesInvalidoException("o conteudo contem caractere nulo");
        }
        String normalizado = bruto.replace("\r\n", "\n").replace('\r', '\n').strip();
        if (normalizado.length() > tamanhoMaximo) {
            throw new ConteudoDasInformacoesInvalidoException(
                    "o conteudo excede o limite de " + tamanhoMaximo + " caracteres");
        }
        return new ConteudoDasInformacoes(normalizado);
    }
}
