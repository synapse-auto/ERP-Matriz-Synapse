package com.synapse.crm.relatorios.domain.cancelamentos;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

/** Intervalo local inclusivo convertido para [inicio, fim) no fuso da instancia. */
public record FiltroCancelamentos(
        BaseTemporal base, Instant inicio, Instant fimExclusivo, String profissional,
        String tipo, Descricao descricao, String nome, int pagina, int tamanho) {
    public enum BaseTemporal { REGISTRO, AGENDAMENTO, CANCELAMENTO }
    public enum Descricao { TODAS, COM_DESCRICAO, SEM_DESCRICAO }

    public static FiltroCancelamentos criar(BaseTemporal base, LocalDate inicio, LocalDate fim,
            String profissional, String tipo, Descricao descricao, String nome, int pagina, int tamanho,
            ZoneId fuso, int maximoPagina, int maximoDias, int maximoPaginas, int maximoBusca) {
        if (base == null || descricao == null || inicio == null || fim == null || fim.isBefore(inicio)
                || ChronoUnit.DAYS.between(inicio, fim) >= maximoDias
                || pagina < 0 || pagina >= maximoPaginas || tamanho < 1 || tamanho > maximoPagina) {
            throw new FiltroCancelamentosInvalidoException();
        }
        return new FiltroCancelamentos(base, inicio.atStartOfDay(fuso).toInstant(),
                fim.plusDays(1).atStartOfDay(fuso).toInstant(), texto(profissional, maximoBusca),
                texto(tipo, maximoBusca), descricao, texto(nome, maximoBusca), pagina, tamanho);
    }

    private static String texto(String valor, int limite) {
        if (valor == null || valor.isBlank()) return null;
        String limpo = valor.strip();
        if (limpo.length() > limite || limpo.codePoints().anyMatch(Character::isISOControl)) {
            throw new FiltroCancelamentosInvalidoException();
        }
        return limpo;
    }
}
