package com.synapse.crm.atendimento.domain.finalizacaomassa;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

/**
 * Janela de tempo de uma finalizacao em massa, sempre no fuso da instancia.
 *
 * <p>Regras (documentadas em docs/55):
 *
 * <ul>
 *   <li>data inicial e data final sao <b>inclusivas</b>;
 *   <li>sem horario inicial, a janela comeca as 00:00 da data inicial;
 *   <li>sem horario final, ela vai ate o fim do dia da data final (exclusivo: 00:00 do dia seguinte);
 *   <li>com horario final, ele e <b>inclusivo no minuto</b>: 18:00 inclui 18:00:59.999;
 *   <li>o horario inicial vale so no primeiro dia e o final so no ultimo.
 * </ul>
 *
 * <p>{@link #fim()} e exclusivo. Uma atividade exatamente em {@link #inicio()} entra; exatamente em
 * {@link #fim()} nao.
 */
public record PeriodoDeFinalizacao(
        LocalDate de,
        LocalDate ate,
        LocalTime horaInicio,
        LocalTime horaFim,
        ZoneId fuso,
        Instant inicio,
        Instant fim) {

    public static PeriodoDeFinalizacao de(
            LocalDate de, LocalDate ate, LocalTime horaInicio, LocalTime horaFim, ZoneId fuso, int maximoDeDias) {
        if (de == null || ate == null) {
            throw FinalizacaoEmMassaException.invalida("PERIODO_INVALIDO", "informe a data inicial e a data final");
        }
        if (ate.isBefore(de)) {
            throw FinalizacaoEmMassaException.invalida("PERIODO_INVERTIDO", "a data final e anterior a data inicial");
        }
        long dias = ChronoUnit.DAYS.between(de, ate) + 1;
        if (dias > maximoDeDias) {
            throw FinalizacaoEmMassaException.invalida(
                    "PERIODO_EXCEDE_MAXIMO", "o periodo tem " + dias + " dias; o maximo e " + maximoDeDias);
        }
        LocalDateTime comeco = LocalDateTime.of(de, horaInicio == null ? LocalTime.MIDNIGHT : horaInicio);
        LocalDateTime termino = horaFim == null
                ? ate.plusDays(1).atStartOfDay()
                : LocalDateTime.of(ate, horaFim).truncatedTo(ChronoUnit.MINUTES).plusMinutes(1);
        if (!termino.isAfter(comeco)) {
            throw FinalizacaoEmMassaException.invalida(
                    "PERIODO_INVERTIDO", "o horario final e anterior ao inicial no mesmo dia");
        }
        return new PeriodoDeFinalizacao(
                de, ate, horaInicio, horaFim, fuso, comeco.atZone(fuso).toInstant(), termino.atZone(fuso).toInstant());
    }

    /** O instante esta dentro da janela: {@code inicio <= instante < fim}. */
    public boolean contem(Instant instante) {
        return !instante.isBefore(inicio) && instante.isBefore(fim);
    }
}
