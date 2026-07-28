package com.synapse.crm.core.domain.filtro;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

import com.synapse.crm.core.domain.lead.StatusBasicoLead;

/**
 * Natureza de um campo filtravel: que operadores fazem sentido nele e como o valor recebido vira
 * objeto tipado.
 *
 * <p>A conversao e a segunda parede contra injecao. A primeira e a allowlist ({@link CampoFiltravel},
 * {@link Operador}), que impede identificador arbitrario de virar SQL. Esta impede <em>valor</em>
 * arbitrario de virar qualquer coisa: em campo de referencia so passa o que {@link UUID#fromString}
 * aceita, em campo de data so o que o parser ISO aceita. A terceira parede e o interpretador, que so
 * emite parametro — nunca concatena.
 */
public enum TipoDeCampo {

    TEXTO {
        @Override
        ValorDeFiltro converter(String bruto) {
            String limpo = exigirPresente(bruto).trim();
            if (limpo.length() > MAXIMO_DE_CARACTERES) {
                throw new FiltroInvalidoException(
                        "valor de texto acima de " + MAXIMO_DE_CARACTERES + " caracteres");
            }
            return new ValorDeFiltro.Texto(limpo);
        }
    },

    NUMERO {
        @Override
        ValorDeFiltro converter(String bruto) {
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
        ValorDeFiltro converter(String bruto) {
            String limpo = exigirPresente(bruto).trim();
            try {
                return new ValorDeFiltro.Data(Instant.parse(limpo));
            } catch (DateTimeParseException instanteInvalido) {
                // A tela manda "2026-01-31"; a Automacao manda instante completo. Aceitar os dois
                // evita que o frontend precise saber o fuso do servidor para filtrar por dia.
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

    STATUS {
        @Override
        ValorDeFiltro converter(String bruto) {
            try {
                return new ValorDeFiltro.Status(
                        StatusBasicoLead.valueOf(exigirPresente(bruto).trim().toUpperCase()));
            } catch (IllegalArgumentException e) {
                throw new FiltroInvalidoException("status desconhecido: "
                        + FiltroInvalidoException.eco(bruto)
                        + ". Permitidos: " + EnumSet.allOf(StatusBasicoLead.class));
            }
        }
    },

    REFERENCIA {
        @Override
        ValorDeFiltro converter(String bruto) {
            try {
                return new ValorDeFiltro.Referencia(UUID.fromString(exigirPresente(bruto).trim()));
            } catch (IllegalArgumentException e) {
                throw new FiltroInvalidoException(
                        "valor nao e um identificador: " + FiltroInvalidoException.eco(bruto));
            }
        }
    };

    private static final int MAXIMO_DE_CARACTERES = 200;

    /** Converte o texto recebido no JSON, ou rejeita a requisicao inteira. */
    abstract ValorDeFiltro converter(String bruto);

    /** Operadores que fazem sentido neste tipo — o teto do que um campo pode permitir. */
    Set<Operador> operadoresNaturais() {
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
                    Operador.MAIOR_QUE,
                    Operador.MENOR_QUE,
                    Operador.ENTRE,
                    Operador.PREENCHIDO,
                    Operador.VAZIO);
            case STATUS -> EnumSet.of(Operador.IGUAL, Operador.DIFERENTE, Operador.EM);
            case REFERENCIA -> EnumSet.of(
                    Operador.IGUAL, Operador.DIFERENTE, Operador.EM, Operador.PREENCHIDO, Operador.VAZIO);
        };
    }

    private static String exigirPresente(String bruto) {
        if (bruto == null || bruto.isBlank()) {
            throw new FiltroInvalidoException("valor ausente para um operador que exige valor");
        }
        return bruto;
    }
}
