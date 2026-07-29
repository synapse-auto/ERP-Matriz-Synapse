package com.synapse.crm.automacaoconfig.infrastructure;

/**
 * Nomes de chave do cache Redis de {@code configuracao_automacao} (E07 §3).
 *
 * <p>Publica, e num pacote neutro acima de {@code persistencia} e {@code reacao}, porque as duas
 * precisam concordar sobre o mesmo nome: quem escreve o cache ({@code
 * persistencia.ConfiguracaoAutomacaoRepositorioJpa}) e quem invalida ({@code
 * reacao.CacheDeConfiguracaoAutomacaoListener}) sao classes de pacotes diferentes, e uma referencia
 * cruzada entre adaptadores de infraestrutura seria pior do que uma constante compartilhada.
 */
public final class ChavesDeCacheConfiguracaoAutomacao {

    public static final String TODAS = "synapse:config-automacao:todas";
    private static final String PREFIXO_CHAVE = "synapse:config-automacao:chave:";

    public static String porChave(String chave) {
        return PREFIXO_CHAVE + chave;
    }

    private ChavesDeCacheConfiguracaoAutomacao() {}
}
