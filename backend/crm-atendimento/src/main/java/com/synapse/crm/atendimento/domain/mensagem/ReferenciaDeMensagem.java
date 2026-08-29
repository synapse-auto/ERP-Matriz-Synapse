package com.synapse.crm.atendimento.domain.mensagem;

import java.time.Instant;
import java.util.UUID;

/** Ligacao persistida de resposta ou encaminhamento, com a citacao ja sanitizada. */
public record ReferenciaDeMensagem(
        TipoReferencia tipo,
        UUID origemMensagemId,
        Instant origemEnviadaEm,
        UUID origemAtendimentoId,
        String citacaoAutor,
        String citacaoTipo,
        String citacaoPrevia,
        String contextoWamid) {

    public ReferenciaDeMensagem {
        if (tipo == null) {
            throw new IllegalArgumentException("tipo da referencia e obrigatorio");
        }
        if (origemMensagemId == null || origemEnviadaEm == null || origemAtendimentoId == null) {
            throw new IllegalArgumentException("origem da referencia e obrigatoria");
        }
        citacaoAutor = citacaoAutor == null ? "" : citacaoAutor;
        citacaoTipo = citacaoTipo == null ? "" : citacaoTipo;
        citacaoPrevia = citacaoPrevia == null ? "" : citacaoPrevia;
    }
}
