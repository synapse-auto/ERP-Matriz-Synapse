package com.synapse.crm.core.domain.campocustomizado;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Valida {@code dados_customizados} contra os metadados antes de gravar — a metade de escrita da
 * mesma allowlist que o filtro modular usa na leitura.
 *
 * <p>Chave nao cadastrada e rejeitada, nunca ignorada em silencio: se fosse ignorada, o atendente
 * preencheria "numero da obra" e o valor simplesmente sumiria, sem nenhum sinal.
 *
 * <p>Datas sao canonicalizadas para {@link Instant#toString()} (ex.: {@code 2026-01-31T00:00:00Z})
 * antes de gravar. E o que permite ao filtro modular comparar {@code MAIOR_QUE}/{@code MENOR_QUE} em
 * campo customizado de data como texto ISO simples, sem CAST no banco: comparacao lexicografica de
 * ISO-8601 de largura fixa e cronologicamente correta.
 */
public final class ValidadorDeDadosCustomizados {

    private ValidadorDeDadosCustomizados() {}

    /**
     * @param bruto o que o cliente mandou, com valores ja desserializados pelo Jackson (String,
     *     Number, Boolean) na camada HTTP — este metodo so ve tipos Java puros
     * @param metadados os campos cadastrados; cada chave em {@code bruto} precisa estar aqui
     * @return copia validada e canonicalizada, pronta para mesclar no dado existente do lead
     */
    public static Map<String, Object> validar(
            Map<String, Object> bruto, List<CampoCustomizado> metadados) {
        Map<String, CampoCustomizado> porChave = new LinkedHashMap<>();
        metadados.forEach(campo -> porChave.put(campo.chave(), campo));

        Map<String, Object> validado = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entrada : bruto.entrySet()) {
            String chave = entrada.getKey();
            CampoCustomizado campo = porChave.get(chave);
            if (campo == null) {
                throw new DadosCustomizadosInvalidosException(
                        "campo customizado nao cadastrado: '" + chave + "'");
            }
            validado.put(chave, validarValor(campo, entrada.getValue()));
        }
        metadados.stream()
                .filter(CampoCustomizado::obrigatorio)
                .filter(campo -> !bruto.containsKey(campo.chave()))
                .findFirst()
                .ifPresent(campo -> {
                    throw new DadosCustomizadosInvalidosException(
                            "campo customizado '" + campo.chave() + "' e obrigatorio");
                });
        return validado;
    }

    private static Object validarValor(CampoCustomizado campo, Object bruto) {
        if (bruto == null) {
            if (campo.obrigatorio()) {
                throw new DadosCustomizadosInvalidosException(
                        "campo customizado '" + campo.chave() + "' e obrigatorio e nao pode ser nulo");
            }
            return null;
        }

        String texto = String.valueOf(bruto).trim();
        if (campo.obrigatorio() && texto.isBlank()) {
            throw new DadosCustomizadosInvalidosException(
                    "campo customizado '" + campo.chave() + "' e obrigatorio e nao pode ficar vazio");
        }

        return switch (campo.tipo()) {
            case TEXTO -> texto;
            case NUMERO -> validarNumero(campo, texto);
            case DATA -> canonicalizarData(campo, texto);
            case BOOLEANO -> validarBooleano(campo, bruto, texto);
            case LISTA -> validarOpcao(campo, texto);
        };
    }

    private static long validarNumero(CampoCustomizado campo, String texto) {
        try {
            return Long.parseLong(texto);
        } catch (NumberFormatException e) {
            throw new DadosCustomizadosInvalidosException(
                    "campo customizado '" + campo.chave() + "' exige numero inteiro, recebeu: " + texto);
        }
    }

    private static String canonicalizarData(CampoCustomizado campo, String texto) {
        try {
            return Instant.parse(texto).toString();
        } catch (DateTimeParseException instanteInvalido) {
            try {
                return LocalDate.parse(texto).atStartOfDay(ZoneOffset.UTC).toInstant().toString();
            } catch (DateTimeParseException dataInvalida) {
                throw new DadosCustomizadosInvalidosException("campo customizado '" + campo.chave()
                        + "' exige data ISO-8601, recebeu: " + texto);
            }
        }
    }

    private static boolean validarBooleano(CampoCustomizado campo, Object bruto, String texto) {
        if (bruto instanceof Boolean booleano) {
            return booleano;
        }
        if ("true".equalsIgnoreCase(texto) || "false".equalsIgnoreCase(texto)) {
            return Boolean.parseBoolean(texto);
        }
        throw new DadosCustomizadosInvalidosException(
                "campo customizado '" + campo.chave() + "' exige booleano, recebeu: " + texto);
    }

    private static String validarOpcao(CampoCustomizado campo, String texto) {
        if (!campo.opcoes().contains(texto)) {
            throw new DadosCustomizadosInvalidosException("campo customizado '" + campo.chave()
                    + "' aceita apenas " + campo.opcoes() + ", recebeu: " + texto);
        }
        return texto;
    }
}
