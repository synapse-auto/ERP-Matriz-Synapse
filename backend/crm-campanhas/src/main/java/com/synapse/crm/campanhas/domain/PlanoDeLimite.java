package com.synapse.crm.campanhas.domain;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Quantas mensagens a campanha pode enfileirar em cada dia, e quando ela termina nesse ritmo.
 *
 * <p>O limite do dia e o menor entre: o limite da campanha (com a rampa, se houver) e o teto da
 * instancia. O ritmo por minuto e a janela de horario impoem um segundo limite fisico: nao adianta
 * prometer 5 mil por dia a 10 por minuto numa janela de duas horas.
 */
public final class PlanoDeLimite {

    /** Para de projetar depois de um ano: uma campanha que leva mais que isso esta mal configurada. */
    public static final int MAXIMO_DE_DIAS_PROJETADOS = 366;

    private PlanoDeLimite() {}

    /** Aumento automatico: soma {@code incrementoPorDia} por dia decorrido, sem passar de {@code teto}. */
    public record Rampa(int incrementoPorDia, int teto) {

        public Rampa {
            if (incrementoPorDia <= 0 || teto <= 0) {
                throw new CampanhaInvalidaException("a rampa exige aumento por dia e teto maiores que zero");
            }
        }
    }

    public record DiaProjetado(LocalDate dia, int mensagens, int limiteDoDia) {}

    /**
     * @param terminoEstimado ultimo dia com envio; nulo se a projecao estourou {@link #MAXIMO_DE_DIAS_PROJETADOS}
     */
    public record Projecao(List<DiaProjetado> dias, LocalDate terminoEstimado, boolean completa) {}

    /** Limite do dia {@code diasDesdeOInicio} (0 = primeiro dia), ja respeitando o teto da instancia. */
    public static int limiteDoDia(int limiteBase, Rampa rampa, long diasDesdeOInicio, int tetoDaInstancia) {
        long efetivo = limiteBase;
        if (rampa != null) {
            long comRampa = limiteBase + (long) rampa.incrementoPorDia() * Math.max(0, diasDesdeOInicio);
            efetivo = Math.max(limiteBase, Math.min(rampa.teto(), comRampa));
        }
        return (int) Math.min(efetivo, tetoDaInstancia);
    }

    /** Teto fisico do dia: o ritmo por minuto multiplicado pelos minutos da janela. */
    public static int capacidadeDoDia(int ritmoPorMinuto, JanelaDeEnvio janela) {
        return (int) Math.min(Integer.MAX_VALUE, (long) ritmoPorMinuto * janela.minutosPorDia());
    }

    public static Projecao projetar(
            int destinatarios,
            LocalDate primeiroDia,
            LocalDate inicioDaCampanha,
            JanelaDeEnvio janela,
            int limiteBase,
            Rampa rampa,
            int ritmoPorMinuto,
            int tetoDaInstancia) {
        List<DiaProjetado> dias = new ArrayList<>();
        int restante = destinatarios;
        LocalDate dia = primeiroDia;
        for (int i = 0; i < MAXIMO_DE_DIAS_PROJETADOS && restante > 0; i++, dia = dia.plusDays(1)) {
            if (!janela.dias().contem(dia.getDayOfWeek())) {
                continue;
            }
            long decorridos = java.time.temporal.ChronoUnit.DAYS.between(inicioDaCampanha, dia);
            int limite = Math.min(
                    limiteDoDia(limiteBase, rampa, decorridos, tetoDaInstancia),
                    capacidadeDoDia(ritmoPorMinuto, janela));
            int hoje = Math.min(restante, limite);
            dias.add(new DiaProjetado(dia, hoje, limite));
            restante -= hoje;
        }
        boolean completa = restante == 0;
        LocalDate termino = completa && !dias.isEmpty() ? dias.get(dias.size() - 1).dia() : null;
        return new Projecao(List.copyOf(dias), termino, completa);
    }

    /** Dias inteiros entre o inicio da campanha e agora, no fuso da instancia (nunca negativo). */
    public static long diasDesdeOInicio(ZonedDateTime inicio, ZonedDateTime agora) {
        return Math.max(0, java.time.temporal.ChronoUnit.DAYS.between(inicio.toLocalDate(), agora.toLocalDate()));
    }
}
