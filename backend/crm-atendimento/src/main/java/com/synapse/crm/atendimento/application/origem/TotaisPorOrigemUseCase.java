package com.synapse.crm.atendimento.application.origem;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.atendimento.application.origem.OrigemDeMensagemAutomaticaRepositorio.TotalPorOrigem;
import com.synapse.crm.sharedkernel.persistencia.Pools;

/** Visao somente leitura: quantas mensagens automaticas e quantos leads por origem e dia (E219). */
@Service
public class TotaisPorOrigemUseCase {

    /** Um trimestre: cobre a auditoria pedida sem varrer o historico inteiro de mensagem. */
    public static final int MAXIMO_DE_DIAS = 93;

    private final OrigemDeMensagemAutomaticaRepositorio origens;
    private final ZoneId fuso;

    public TotaisPorOrigemUseCase(OrigemDeMensagemAutomaticaRepositorio origens, ZoneId fuso) {
        this.origens = origens;
        this.fuso = fuso;
    }

    @PreAuthorize("hasRole('SERVICO')")
    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER, readOnly = true)
    public Totais calcular(LocalDate de, LocalDate ate) {
        if (de == null || ate == null || ate.isBefore(de) || ChronoUnit.DAYS.between(de, ate) >= MAXIMO_DE_DIAS) {
            throw new PeriodoInvalidoException();
        }
        return new Totais(fuso.getId(), origens.totaisPorDia(de, ate, fuso));
    }

    /** @param fuso fuso da instancia em que os dias foram contados */
    public record Totais(String fuso, List<TotalPorOrigem> linhas) {}

    public static final class PeriodoInvalidoException extends RuntimeException {
        PeriodoInvalidoException() {
            super("periodo invalido: de <= ate e no maximo " + MAXIMO_DE_DIAS + " dias");
        }
    }
}
