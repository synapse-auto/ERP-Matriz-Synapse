package com.synapse.crm.campanhas.domain;

import java.time.Duration;
import java.time.LocalTime;
import java.time.ZonedDateTime;

/**
 * Faixa de horario e dias em que a campanha pode enviar, sempre no fuso da instancia (quem chama passa o
 * instante ja convertido). Fora dela o ciclo nao envia nada e a fila fica intacta.
 */
public record JanelaDeEnvio(LocalTime inicio, LocalTime fim, DiasDaSemana dias) {

    public JanelaDeEnvio {
        if (inicio == null || fim == null || dias == null) {
            throw new CampanhaInvalidaException("a janela de envio exige horario inicial, final e dias");
        }
        if (!inicio.isBefore(fim)) {
            throw new CampanhaInvalidaException("o horario inicial da janela precisa ser antes do final");
        }
    }

    public boolean abertaEm(ZonedDateTime instante) {
        LocalTime hora = instante.toLocalTime();
        return dias.contem(instante.getDayOfWeek()) && !hora.isBefore(inicio) && hora.isBefore(fim);
    }

    public boolean permiteODia(ZonedDateTime instante) {
        return dias.contem(instante.getDayOfWeek());
    }

    public long minutosPorDia() {
        return Duration.between(inicio, fim).toMinutes();
    }

    /** Proxima abertura estritamente depois de {@code instante}; se a janela abre hoje mais tarde, e hoje. */
    public ZonedDateTime proximaAberturaApos(ZonedDateTime instante) {
        ZonedDateTime candidata = instante.toLocalDate().atTime(inicio).atZone(instante.getZone());
        if (!candidata.isAfter(instante)) {
            candidata = candidata.plusDays(1);
        }
        // DiasDaSemana garante ao menos um dia marcado, entao o laco termina em no maximo 7 passos.
        while (!permiteODia(candidata)) {
            candidata = candidata.plusDays(1);
        }
        return candidata;
    }
}
