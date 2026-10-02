package com.synapse.crm.campanhas.domain;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Liga cada variavel {{n}} do corpo do template a um campo do lead, com valor de reserva para quando o campo
 * esta vazio. A Meta recusa parametro vazio (codigo 132012), entao toda variavel tem reserva.
 */
public record MapeamentoDeVariaveis(List<Variavel> variaveis) {

    public static final int TAMANHO_MAXIMO_DA_RESERVA = 60;

    /** @param posicao posicao do parametro no template, a partir de 1 */
    public record Variavel(int posicao, CampoDoLead campo, String reserva) {}

    public MapeamentoDeVariaveis {
        variaveis = variaveis == null ? List.of() : List.copyOf(variaveis);
    }

    public static MapeamentoDeVariaveis vazio() {
        return new MapeamentoDeVariaveis(List.of());
    }

    /** O mapeamento tem de cobrir exatamente as posicoes 1..n do template, cada uma uma vez, com reserva. */
    public void validarPara(int parametrosDoTemplate) {
        if (variaveis.size() != parametrosDoTemplate) {
            throw new CampanhaInvalidaException(
                    "o template tem " + parametrosDoTemplate + " variavel(is); mapeie exatamente essa quantidade");
        }
        Set<Integer> vistas = new HashSet<>();
        for (Variavel variavel : variaveis) {
            if (variavel.campo() == null) {
                throw new CampanhaInvalidaException("a variavel " + variavel.posicao() + " precisa de um campo");
            }
            if (variavel.posicao() < 1 || variavel.posicao() > parametrosDoTemplate || !vistas.add(variavel.posicao())) {
                throw new CampanhaInvalidaException("posicao de variavel invalida ou repetida: " + variavel.posicao());
            }
            String reserva = sanitizar(variavel.reserva());
            if (reserva.isEmpty() || reserva.length() > TAMANHO_MAXIMO_DA_RESERVA) {
                throw new CampanhaInvalidaException(
                        "a variavel " + variavel.posicao() + " precisa de um valor de reserva de ate "
                                + TAMANHO_MAXIMO_DA_RESERVA + " caracteres");
            }
        }
    }

    /** Parametros na ordem do template. Campo vazio vira a reserva; o resultado nunca tem parametro vazio. */
    public List<String> resolver(CampoDoLead.Dados dados) {
        return variaveis.stream()
                .sorted(Comparator.comparingInt(Variavel::posicao))
                .map(variavel -> {
                    String valor = sanitizar(variavel.campo().valor(dados));
                    return valor.isEmpty() ? sanitizar(variavel.reserva()) : valor;
                })
                .toList();
    }

    /** A Meta nao aceita quebra de linha, tabulacao nem sequencias de espacos dentro de um parametro. */
    static String sanitizar(String texto) {
        return texto == null ? "" : texto.replaceAll("\\s+", " ").trim();
    }
}
