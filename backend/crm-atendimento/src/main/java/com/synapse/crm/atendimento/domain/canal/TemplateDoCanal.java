package com.synapse.crm.atendimento.domain.canal;

/**
 * Um modelo de mensagem do provedor, ja traduzido para vocabulario do CRM.
 *
 * <p>Nome, idioma e categoria existem porque a Meta os exige para enviar fora da janela de 24h —
 * nao porque o dominio goste de detalhe de API. O payload cru ({@code components}, {@code
 * parameter_format}) nao atravessa esta fronteira.
 */
public record TemplateDoCanal(
        String id,
        String nome,
        String idioma,
        Categoria categoria,
        Status status,
        String corpo,
        int quantidadeDeParametros,
        RecursosAlemDoCorpo recursos) {

    public TemplateDoCanal {
        recursos = recursos == null ? RecursosAlemDoCorpo.NENHUM : recursos;
    }

    /** Template so de corpo textual: o formato que todo provedor sabe listar e enviar. */
    public TemplateDoCanal(
            String id,
            String nome,
            String idioma,
            Categoria categoria,
            Status status,
            String corpo,
            int quantidadeDeParametros) {
        this(id, nome, idioma, categoria, status, corpo, quantidadeDeParametros, RecursosAlemDoCorpo.NENHUM);
    }

    /**
     * O que o template tem alem do corpo. Quem envia em massa (campanha) so sabe preencher variaveis do
     * corpo; midia no cabecalho, variavel no cabecalho e botao com parametro dinamico exigem dados que
     * o disparo nao tem, e a Meta recusaria cada envio.
     *
     * @param outroComponente carrossel, oferta por tempo limitado ou qualquer componente desconhecido
     */
    public record RecursosAlemDoCorpo(
            boolean cabecalhoDeMidia,
            boolean cabecalhoComVariavel,
            boolean botaoComParametro,
            boolean outroComponente) {

        public static final RecursosAlemDoCorpo NENHUM = new RecursosAlemDoCorpo(false, false, false, false);

        public boolean suportadoEmCampanha() {
            return !cabecalhoDeMidia && !cabecalhoComVariavel && !botaoComParametro && !outroComponente;
        }
    }

    public enum Categoria {
        UTILIDADE,
        MARKETING,
        AUTENTICACAO
    }

    public enum Status {
        APROVADO,
        PENDENTE,
        REJEITADO,
        PAUSADO,
        DESCONHECIDO
    }
}
