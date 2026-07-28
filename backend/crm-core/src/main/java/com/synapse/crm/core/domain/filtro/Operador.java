package com.synapse.crm.core.domain.filtro;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Operadores que o filtro modular sabe traduzir para SQL.
 *
 * <p>Allowlist, e nao vocabulario aberto: o que nao esta neste enum nao chega ao interpretador. Um
 * operador que viesse do JSON como texto livre e fosse concatenado numa clausula seria injecao —
 * aqui ele nem vira objeto.
 *
 * <p>Cada operador declara sua <strong>aridade</strong>. E o que impede um {@code ENTRE} com um
 * valor so (que geraria SQL invalido em tempo de execucao) ou um {@code PREENCHIDO} carregando um
 * valor que ninguem leria — inconsistencia silenciosa e pior que erro.
 */
public enum Operador {

    /** Igualdade exata. Em campo de conjunto (tag), significa "possui". */
    IGUAL(Aridade.UM),

    /**
     * Diferente <em>ou nulo</em>.
     *
     * <p>"Empresa diferente de ACME" inclui, para o usuario, o lead sem empresa preenchida. Em SQL,
     * {@code <>} sozinho descartaria essas linhas, porque {@code NULL <> 'ACME'} nao e verdadeiro.
     * A traducao acrescenta o {@code IS NULL} para que o resultado case com a leitura humana.
     */
    DIFERENTE(Aridade.UM),

    /** Trecho em qualquer posicao, sem diferenciar maiusculas. So texto. */
    CONTEM(Aridade.UM),

    /** Prefixo, sem diferenciar maiusculas. So texto. */
    COMECA_COM(Aridade.UM),

    MAIOR_QUE(Aridade.UM),
    MENOR_QUE(Aridade.UM),

    /** Intervalo fechado nos dois extremos. */
    ENTRE(Aridade.DOIS),

    /** Pertence a lista. */
    EM(Aridade.LISTA),

    /** Tem valor: nao nulo e, em texto, nao vazio. */
    PREENCHIDO(Aridade.NENHUM),

    /** Nao tem valor. */
    VAZIO(Aridade.NENHUM);

    /** Teto do {@code IN}. Uma lista de 50 mil ids e DoS barato disfarcado de filtro. */
    public static final int MAXIMO_DE_VALORES_NA_LISTA = 200;

    private static final Map<String, Operador> POR_NOME = Arrays.stream(values())
            .collect(Collectors.toMap(Enum::name, Function.identity()));

    private final Aridade aridade;

    Operador(Aridade aridade) {
        this.aridade = aridade;
    }

    /** Traduz o texto recebido no JSON. Fora da lista, rejeita — nunca tenta adivinhar. */
    public static Operador de(String recebido) {
        Operador operador = recebido == null ? null : POR_NOME.get(recebido.trim().toUpperCase());
        if (operador == null) {
            throw new FiltroInvalidoException("operador nao permitido: "
                    + FiltroInvalidoException.eco(recebido)
                    + ". Permitidos: " + POR_NOME.keySet().stream().sorted().toList());
        }
        return operador;
    }

    public void exigirQuantidadeDeValores(int quantidade) {
        if (!aridade.aceita(quantidade)) {
            throw new FiltroInvalidoException(
                    "o operador " + name() + " " + aridade.descricao() + ", e recebeu " + quantidade);
        }
    }

    /** Quantos valores cada operador consome. */
    private enum Aridade {
        NENHUM,
        UM,
        DOIS,
        LISTA;

        boolean aceita(int quantidade) {
            return switch (this) {
                case NENHUM -> quantidade == 0;
                case UM -> quantidade == 1;
                case DOIS -> quantidade == 2;
                case LISTA -> quantidade >= 1 && quantidade <= MAXIMO_DE_VALORES_NA_LISTA;
            };
        }

        String descricao() {
            return switch (this) {
                case NENHUM -> "nao aceita valor";
                case UM -> "exige exatamente um valor";
                case DOIS -> "exige exatamente dois valores";
                case LISTA -> "exige de 1 a " + MAXIMO_DE_VALORES_NA_LISTA + " valores";
            };
        }
    }
}
