package com.synapse.crm.core.domain.campocustomizado;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.EnumSet;
import java.util.Set;

import com.synapse.crm.core.domain.filtro.FiltroInvalidoException;
import com.synapse.crm.core.domain.filtro.Operador;
import com.synapse.crm.core.domain.filtro.ValorDeFiltro;

/**
 * Natureza de um campo customizado. Espelha o {@code CHECK} de {@code campo_customizado.tipo}.
 *
 * <p>E o irmao de {@link com.synapse.crm.core.domain.filtro.TipoDeCampo}, e nao o mesmo tipo, porque
 * os dois tem publicos diferentes: aquele descreve os campos <b>fixos</b> do lead, este descreve o
 * que um filho cadastrou em tempo de execucao. Tratar os dois como um so obrigaria o metadado
 * dinamico a caber no vocabulario do schema estatico — {@code BOOLEANO} e {@code LISTA} nao existem
 * la porque nenhum campo fixo do lead precisa deles.
 */
public enum TipoCampoCustomizado {

    TEXTO {
        @Override
        public ValorDeFiltro converterParaFiltro(String bruto) {
            return new ValorDeFiltro.Texto(exigirPresente(bruto).trim());
        }
    },

    /**
     * Inteiro, como {@link com.synapse.crm.core.domain.filtro.TipoDeCampo#NUMERO}: o mesmo
     * {@link ValorDeFiltro.Numero} guarda {@code long}, entao um "numero da obra" com casas decimais
     * nao cabe aqui — cabe em {@code TEXTO} se precisar.
     */
    NUMERO {
        @Override
        public ValorDeFiltro converterParaFiltro(String bruto) {
            try {
                return new ValorDeFiltro.Numero(Long.parseLong(exigirPresente(bruto).trim()));
            } catch (NumberFormatException e) {
                throw new FiltroInvalidoException(
                        "valor nao e um numero inteiro: " + FiltroInvalidoException.eco(bruto));
            }
        }
    },

    DATA {
        @Override
        public ValorDeFiltro converterParaFiltro(String bruto) {
            String limpo = exigirPresente(bruto).trim();
            try {
                return new ValorDeFiltro.Data(Instant.parse(limpo));
            } catch (DateTimeParseException instanteInvalido) {
                try {
                    return new ValorDeFiltro.Data(
                            LocalDate.parse(limpo).atStartOfDay(ZoneOffset.UTC).toInstant());
                } catch (DateTimeParseException dataInvalida) {
                    throw new FiltroInvalidoException(
                            "valor nao e uma data ISO-8601: " + FiltroInvalidoException.eco(bruto));
                }
            }
        }
    },

    BOOLEANO {
        @Override
        public ValorDeFiltro converterParaFiltro(String bruto) {
            String limpo = exigirPresente(bruto).trim().toLowerCase();
            if (!"true".equals(limpo) && !"false".equals(limpo)) {
                throw new FiltroInvalidoException(
                        "valor nao e booleano: " + FiltroInvalidoException.eco(bruto));
            }
            return new ValorDeFiltro.Booleano(Boolean.parseBoolean(limpo));
        }
    },

    /** Uma opcao dentre as declaradas em {@code campo_customizado.opcoes}. Guardado como texto. */
    LISTA {
        @Override
        public ValorDeFiltro converterParaFiltro(String bruto) {
            return new ValorDeFiltro.Texto(exigirPresente(bruto).trim());
        }
    };

    /** Converte o texto recebido no filtro, ou rejeita a requisicao inteira. */
    public abstract ValorDeFiltro converterParaFiltro(String bruto);

    /**
     * Operadores que fazem sentido neste tipo. {@code MAIOR_QUE} nao faz sentido em
     * {@code BOOLEANO} — nao ha "mais verdadeiro que" — entao ele simplesmente nao aparece aqui, e o
     * interpretador nunca precisa saber disso.
     */
    public Set<Operador> operadoresPermitidos() {
        return switch (this) {
            case TEXTO -> EnumSet.of(
                    Operador.IGUAL,
                    Operador.DIFERENTE,
                    Operador.CONTEM,
                    Operador.COMECA_COM,
                    Operador.EM,
                    Operador.PREENCHIDO,
                    Operador.VAZIO);
            case NUMERO -> EnumSet.of(
                    Operador.IGUAL,
                    Operador.DIFERENTE,
                    Operador.MAIOR_QUE,
                    Operador.MENOR_QUE,
                    Operador.ENTRE);
            case DATA -> EnumSet.of(
                    Operador.MAIOR_QUE, Operador.MENOR_QUE, Operador.ENTRE, Operador.PREENCHIDO,
                    Operador.VAZIO);
            case BOOLEANO -> EnumSet.of(Operador.IGUAL, Operador.DIFERENTE);
            case LISTA -> EnumSet.of(
                    Operador.IGUAL, Operador.DIFERENTE, Operador.EM, Operador.PREENCHIDO,
                    Operador.VAZIO);
        };
    }

    private static String exigirPresente(String bruto) {
        if (bruto == null || bruto.isBlank()) {
            throw new FiltroInvalidoException("valor ausente para um operador que exige valor");
        }
        return bruto;
    }
}
