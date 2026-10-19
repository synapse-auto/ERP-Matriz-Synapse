package com.synapse.crm.relatorios.domain.cancelamentos;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/** Contrato minimo de evento; nao contem payload clinico nem telefone. */
public record NovoCancelamento(String origem, String eventoExternoId, String agendamentoExternoId,
        UUID leadId, UUID atendimentoId, Instant agendadoPara, Instant canceladoEm,
        String contatoNome, String profissionalNome, String tipoAgendamento, String descricao) {
    public NovoCancelamento {
        origem = identificador(origem, 80);
        eventoExternoId = identificador(eventoExternoId, 200);
        agendamentoExternoId = identificador(agendamentoExternoId, 200);
        if ((leadId == null) != (atendimentoId == null) || agendadoPara == null)
            throw new RegistroCancelamentoInvalidoException();
        agendadoPara = agendadoPara.truncatedTo(ChronoUnit.MICROS);
        if (canceladoEm != null) canceladoEm = canceladoEm.truncatedTo(ChronoUnit.MICROS);
        contatoNome = texto(contatoNome, 200, false);
        profissionalNome = texto(profissionalNome, 200, false);
        tipoAgendamento = texto(tipoAgendamento, 200, false);
        descricao = texto(descricao, 2000, true);
    }
    public static String identificador(String valor, int limite) {
        String texto = texto(valor, limite, false);
        if (texto == null || !texto.matches("[A-Za-z0-9][A-Za-z0-9._:/-]*"))
            throw new RegistroCancelamentoInvalidoException();
        return texto;
    }
    private static String texto(String valor, int limite, boolean multilinha) {
        if (valor == null) return null;
        if (valor.length() > limite || valor.codePoints().anyMatch(c -> Character.isISOControl(c)
                && !(multilinha && (c == '\n' || c == '\r' || c == '\t'))))
            throw new RegistroCancelamentoInvalidoException();
        return valor.isBlank() ? null : valor.strip();
    }
}
