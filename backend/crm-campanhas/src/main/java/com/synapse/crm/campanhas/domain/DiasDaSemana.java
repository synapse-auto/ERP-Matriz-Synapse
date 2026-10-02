package com.synapse.crm.campanhas.domain;

import java.time.DayOfWeek;
import java.util.EnumSet;
import java.util.Set;

/** Dias em que a campanha envia, guardados como mascara de bits (bit 0 = segunda ... bit 6 = domingo). */
public record DiasDaSemana(int mascara) {

    public static final DiasDaSemana DIAS_UTEIS = de(Set.of(
            DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY));

    public DiasDaSemana {
        if (mascara < 1 || mascara > 127) {
            throw new CampanhaInvalidaException("escolha ao menos um dia da semana");
        }
    }

    public static DiasDaSemana de(Set<DayOfWeek> dias) {
        int mascara = 0;
        for (DayOfWeek dia : dias) {
            mascara |= 1 << (dia.getValue() - 1);
        }
        return new DiasDaSemana(mascara);
    }

    public boolean contem(DayOfWeek dia) {
        return (mascara & (1 << (dia.getValue() - 1))) != 0;
    }

    public Set<DayOfWeek> conjunto() {
        Set<DayOfWeek> dias = EnumSet.noneOf(DayOfWeek.class);
        for (DayOfWeek dia : DayOfWeek.values()) {
            if (contem(dia)) {
                dias.add(dia);
            }
        }
        return dias;
    }
}
