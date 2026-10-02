package com.synapse.crm.campanhas.domain;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** O corpo de um template e suas variaveis posicionais {{1}}, {{2}}... */
public final class CorpoDoTemplate {

    private static final Pattern VARIAVEL = Pattern.compile("\\{\\{\\s*(\\d+)\\s*}}");

    private CorpoDoTemplate() {}

    /**
     * O texto como o cliente o le: cada {{n}} vira o n-esimo parametro. Variavel sem parametro correspondente
     * fica como esta, para o defeito aparecer na previa em vez de sumir.
     */
    public static String renderizar(String corpo, List<String> parametros) {
        if (corpo == null) {
            return "";
        }
        Matcher encontrada = VARIAVEL.matcher(corpo);
        StringBuilder resultado = new StringBuilder();
        while (encontrada.find()) {
            int posicao = Integer.parseInt(encontrada.group(1));
            String valor = posicao >= 1 && posicao <= parametros.size() ? parametros.get(posicao - 1) : encontrada.group();
            encontrada.appendReplacement(resultado, Matcher.quoteReplacement(valor));
        }
        encontrada.appendTail(resultado);
        return resultado.toString();
    }

    /** Maior posicao de variavel que o corpo usa; 0 se nao tem nenhuma. */
    public static int quantidadeDeVariaveis(String corpo) {
        if (corpo == null) {
            return 0;
        }
        Matcher encontrada = VARIAVEL.matcher(corpo);
        int maior = 0;
        while (encontrada.find()) {
            maior = Math.max(maior, Integer.parseInt(encontrada.group(1)));
        }
        return maior;
    }
}
