package com.synapse.crm.atendimento.application.finalizacaomassa;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import com.synapse.crm.atendimento.domain.finalizacaomassa.PeriodoDeFinalizacao;

/** Filtro ja validado: atendentes sem repeticao, em ordem estavel, e um periodo com instantes resolvidos. */
public record FiltroDeFinalizacao(List<UUID> atendenteIds, PeriodoDeFinalizacao periodo) {

    public FiltroDeFinalizacao {
        atendenteIds = List.copyOf(atendenteIds);
    }

    /**
     * Impressao digital estavel dos filtros. A mesma chave de idempotencia com outra impressao e um conflito;
     * a ordem em que o cliente listou os atendentes nao muda a impressao.
     */
    public String impressao() {
        String canonico = String.join(",", atendenteIds.stream().map(UUID::toString).toList())
                + "|" + periodo.de() + "|" + periodo.ate()
                + "|" + periodo.horaInicio() + "|" + periodo.horaFim() + "|" + periodo.fuso();
        try {
            byte[] resumo = MessageDigest.getInstance("SHA-256").digest(canonico.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(resumo);
        } catch (NoSuchAlgorithmException impossivel) {
            throw new IllegalStateException("SHA-256 indisponivel", impossivel);
        }
    }
}
