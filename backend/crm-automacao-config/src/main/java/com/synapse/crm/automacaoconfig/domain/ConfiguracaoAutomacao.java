package com.synapse.crm.automacaoconfig.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Um parametro tipado da automacao (E07 §3).
 *
 * <p>Java puro — sem JPA, sem Spring. {@code valor} e sempre texto no dominio (como na tabela); quem
 * consome (a Automacao, via {@code /internal/v1}) interpreta segundo {@link #tipo()}. Guardar como
 * texto evita uma coluna por tipo e mantem a tabela generica o bastante para qualquer parametro futuro
 * caber sem migration de schema — so uma linha nova.
 */
public record ConfiguracaoAutomacao(
        String chave,
        String valor,
        String unidade,
        TipoConfiguracaoAutomacao tipo,
        BigDecimal valorMin,
        BigDecimal valorMax,
        String descricao,
        UUID atualizadoPorId,
        Instant atualizadoEm) {

    public ConfiguracaoAutomacao {
        Objects.requireNonNull(chave, "chave e obrigatoria");
        Objects.requireNonNull(valor, "valor e obrigatorio");
        Objects.requireNonNull(tipo, "tipo e obrigatorio");
        Objects.requireNonNull(atualizadoEm, "atualizadoEm e obrigatorio");
    }

    /**
     * Copia com o valor trocado, ja validado contra {@link #tipo()} e a faixa desta propria linha.
     *
     * <p>A faixa e o tipo nunca vem de fora: sao os que ja estao gravados nesta linha, a mesma
     * garantia que o {@code CHECK} do banco da para um {@code INSERT} direto.
     */
    public ConfiguracaoAutomacao comNovoValor(String novoValor, UUID quemAlterou, Instant quando) {
        Objects.requireNonNull(novoValor, "novo valor e obrigatorio");
        validar(novoValor);
        return new ConfiguracaoAutomacao(
                chave, novoValor, unidade, tipo, valorMin, valorMax, descricao, quemAlterou, quando);
    }

    private void validar(String novoValor) {
        switch (tipo) {
            case INT, DECIMAL -> validarFaixaNumerica(novoValor);
            case BOOLEAN -> validarBooleano(novoValor);
            case TEXT -> {
                // Texto livre: nenhuma faixa se aplica.
            }
        }
    }

    private void validarFaixaNumerica(String novoValor) {
        BigDecimal numero;
        try {
            numero = new BigDecimal(novoValor.trim());
        } catch (NumberFormatException e) {
            throw new ConfiguracaoAutomacaoInvalidaException(
                    "'" + chave + "' exige numero (" + tipo + "), recebeu: " + novoValor);
        }
        if (tipo == TipoConfiguracaoAutomacao.INT && numero.stripTrailingZeros().scale() > 0) {
            throw new ConfiguracaoAutomacaoInvalidaException(
                    "'" + chave + "' exige numero inteiro, recebeu: " + novoValor);
        }
        if (valorMin != null && numero.compareTo(valorMin) < 0) {
            throw new ConfiguracaoAutomacaoInvalidaException(
                    "'" + chave + "' nao pode ser menor que " + valorMin + " (recebeu " + numero + ")");
        }
        if (valorMax != null && numero.compareTo(valorMax) > 0) {
            throw new ConfiguracaoAutomacaoInvalidaException(
                    "'" + chave + "' nao pode ser maior que " + valorMax + " (recebeu " + numero + ")");
        }
    }

    private void validarBooleano(String novoValor) {
        String limpo = novoValor.trim().toLowerCase();
        if (!"true".equals(limpo) && !"false".equals(limpo)) {
            throw new ConfiguracaoAutomacaoInvalidaException(
                    "'" + chave + "' exige booleano (true/false), recebeu: " + novoValor);
        }
    }
}
