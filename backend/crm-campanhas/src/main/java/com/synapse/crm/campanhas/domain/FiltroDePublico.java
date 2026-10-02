package com.synapse.crm.campanhas.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Recorte opcional da Agenda para a campanha. Sem nenhum campo, o publico e a Agenda inteira.
 *
 * @param tagIds lead com QUALQUER uma das tags
 * @param nuncaConversou lead sem nenhuma mensagem trocada
 * @param busca trecho do nome ou do telefone
 */
public record FiltroDePublico(
        List<UUID> tagIds,
        UUID etapaId,
        UUID atendenteId,
        LocalDate cadastroDesde,
        LocalDate cadastroAte,
        boolean nuncaConversou,
        String busca) {

    public FiltroDePublico {
        tagIds = tagIds == null ? List.of() : List.copyOf(tagIds);
        busca = busca == null || busca.isBlank() ? null : busca.trim();
        if (cadastroDesde != null && cadastroAte != null && cadastroDesde.isAfter(cadastroAte)) {
            throw new CampanhaInvalidaException("a data inicial do cadastro nao pode ser depois da final");
        }
    }

    public static FiltroDePublico agendaInteira() {
        return new FiltroDePublico(List.of(), null, null, null, null, false, null);
    }
}
