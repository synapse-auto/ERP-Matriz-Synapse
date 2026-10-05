package com.synapse.crm.atendimento.application.finalizacaomassa;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/**
 * Filtros como o cliente os envia. Nada aqui e confiavel: o backend revalida tudo (existencia e escopo dos
 * atendentes, periodo, limites) antes de montar um {@link FiltroDeFinalizacao}.
 */
public record PedidoDeFinalizacao(
        List<UUID> atendenteIds, LocalDate de, LocalDate ate, LocalTime horaInicio, LocalTime horaFim) {}
