package com.synapse.crm.atendimento.domain.mensagem;

import java.util.Objects;
import java.util.UUID;

/**
 * Quem mandou a mensagem: o tipo e, quando faz sentido, quem exatamente.
 *
 * <p>O construtor exige {@code id} para {@link RemetenteTipo#ATENDENTE} e o recusa para os demais. A
 * razao e a RN-CRM-06: e o id do atendente que decide para quem o lead vai. Um remetente
 * {@code ATENDENTE} sem id transferiria o lead para ninguem — e o lead sumiria da agenda de todo
 * mundo sem que nenhuma linha de codigo tivesse falhado.
 */
public record Remetente(RemetenteTipo tipo, UUID id) {

    public Remetente {
        Objects.requireNonNull(tipo, "tipo de remetente e obrigatorio");
        if (tipo.exigeIdentificacao() && id == null) {
            throw new IllegalArgumentException("remetente " + tipo + " exige o id de quem enviou");
        }
        if (!tipo.exigeIdentificacao() && id != null) {
            throw new IllegalArgumentException("remetente " + tipo + " nao tem id de usuario");
        }
    }

    public static Remetente lead() {
        return new Remetente(RemetenteTipo.LEAD, null);
    }

    public static Remetente atendente(UUID atendenteId) {
        return new Remetente(RemetenteTipo.ATENDENTE, atendenteId);
    }

    public static Remetente ia() {
        return new Remetente(RemetenteTipo.IA, null);
    }

    public static Remetente sistema() {
        return new Remetente(RemetenteTipo.SISTEMA, null);
    }

    /** Se esta mensagem e do cliente — ou seja, recebida e nao enviada. */
    public boolean ehDoLead() {
        return tipo == RemetenteTipo.LEAD;
    }
}
