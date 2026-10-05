package com.synapse.crm.atendimento.interfaces;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.synapse.crm.atendimento.application.finalizacaomassa.FinalizacaoEmMassaRepositorio.ContagemDosItens;
import com.synapse.crm.atendimento.application.finalizacaomassa.FinalizacaoEmMassaRepositorio.ContagemPorAtendente;
import com.synapse.crm.atendimento.application.finalizacaomassa.FinalizacaoEmMassaRepositorio.ItemDeFinalizacao;
import com.synapse.crm.atendimento.application.finalizacaomassa.FinalizacaoEmMassaRepositorio.OperacaoDeFinalizacao;
import com.synapse.crm.atendimento.application.finalizacaomassa.PedidoDeFinalizacao;
import com.synapse.crm.atendimento.application.finalizacaomassa.PreverFinalizacaoEmMassaUseCase.Previa;

/** Contratos HTTP da finalizacao em massa. O dominio nunca sai pela API: tudo passa por estes records. */
final class FinalizacaoEmMassaDtos {

    private FinalizacaoEmMassaDtos() {}

    /** Filtros. Datas inclusivas; horarios opcionais em {@code HH:mm}; tudo no fuso da instancia. */
    record PedidoRequisicao(
            @NotEmpty @Size(max = 100) List<@NotNull UUID> atendenteIds,
            @NotNull LocalDate de,
            @NotNull LocalDate ate,
            LocalTime horaInicio,
            LocalTime horaFim) {

        PedidoDeFinalizacao paraDominio() {
            return new PedidoDeFinalizacao(atendenteIds, de, ate, horaInicio, horaFim);
        }
    }

    record PeriodoResposta(
            Instant inicio, Instant fim, String fuso, LocalDate de, LocalDate ate, LocalTime horaInicio, LocalTime horaFim) {}

    record AtendenteContadoResposta(UUID atendenteId, String nome, long quantidade) {

        static AtendenteContadoResposta de(ContagemPorAtendente c) {
            return new AtendenteContadoResposta(c.atendenteId(), c.nome(), c.quantidade());
        }
    }

    record PreviaResposta(
            long total,
            List<AtendenteContadoResposta> porAtendente,
            Instant periodoInicio,
            Instant periodoFim,
            String fuso,
            int limite,
            boolean excedeLimite) {

        static PreviaResposta de(Previa previa) {
            return new PreviaResposta(
                    previa.total(),
                    previa.porAtendente().stream().map(AtendenteContadoResposta::de).toList(),
                    previa.periodoInicio(),
                    previa.periodoFim(),
                    previa.fuso(),
                    previa.limite(),
                    previa.excedeLimite());
        }
    }

    /** Estado da operacao. {@code processados} = finalizados + ignorados + falhas; {@code percentual} de 0 a 100. */
    record OperacaoResposta(
            UUID id,
            UUID solicitanteId,
            List<UUID> atendenteIds,
            PeriodoResposta periodo,
            String status,
            int encontrados,
            int pendentes,
            int processados,
            int finalizados,
            int ignorados,
            int falhas,
            int percentual,
            Instant criadaEm,
            Instant iniciadaEm,
            Instant concluidaEm,
            boolean repetida) {

        static OperacaoResposta de(OperacaoDeFinalizacao o, ContagemDosItens itens, boolean repetida) {
            int processados = itens.processados();
            int percentual = o.encontrados() == 0 ? 100 : Math.min(100, processados * 100 / o.encontrados());
            return new OperacaoResposta(
                    o.id(),
                    o.solicitanteId(),
                    o.atendenteIds(),
                    new PeriodoResposta(
                            o.periodoInicio(), o.periodoFim(), o.fuso(), o.dataDe(), o.dataAte(), o.horaInicio(), o.horaFim()),
                    o.status().name(),
                    o.encontrados(),
                    itens.pendentes(),
                    processados,
                    itens.finalizados(),
                    itens.ignorados(),
                    itens.falhas(),
                    percentual,
                    o.criadaEm(),
                    o.iniciadaEm(),
                    o.concluidaEm(),
                    repetida);
        }
    }

    record ItemResposta(
            UUID atendimentoId,
            UUID atendenteId,
            String atendenteNome,
            String leadNome,
            String status,
            String motivo,
            Instant processadoEm) {

        static ItemResposta de(ItemDeFinalizacao i) {
            return new ItemResposta(
                    i.atendimentoId(),
                    i.atendenteId(),
                    i.atendenteNome(),
                    i.leadNome(),
                    i.status().name(),
                    i.motivo() == null ? null : i.motivo().name(),
                    i.processadoEm());
        }
    }

    record PaginaDeItensResposta(List<ItemResposta> itens, int pagina, int tamanho) {}
}
