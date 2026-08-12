package com.synapse.crm.relatorios.domain.equipe;

import java.util.List;
import java.util.UUID;

/** Contadores gerenciais exibidos na tabela e nos rankings da Equipe. */
public record DesempenhoDaEquipe(List<DesempenhoPorAtendente> porAtendente) {

    public DesempenhoDaEquipe {
        porAtendente = List.copyOf(porAtendente);
    }

    public record DesempenhoPorAtendente(
            UUID atendenteId, String atendenteNome, long atendimentos, long vendas) {}
}
