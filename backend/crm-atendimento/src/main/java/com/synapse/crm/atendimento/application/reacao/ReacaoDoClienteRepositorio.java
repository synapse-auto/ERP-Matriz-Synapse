package com.synapse.crm.atendimento.application.reacao;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Reacao atual do cliente por mensagem (E214). Separada de {@link ReacaoDeMensagemRepositorio}, que e
 * por usuario do CRM: o cliente nao tem {@code usuario_id}.
 */
public interface ReacaoDoClienteRepositorio {

    /**
     * Grava o emoji ({@code null} = removida) se o evento for igual ou mais novo que o registrado e
     * mudar alguma coisa. Evento atrasado ou repetido devolve {@code false} e nao altera a linha.
     */
    boolean aplicar(
            ReacaoDeMensagemRepositorio.Chave chave, String emoji, Instant reagidoEm, String idExternoEvento);

    /** Emoji atual por mensagem, em lote; mensagens sem reacao (ou removida) ficam de fora. */
    Map<ReacaoDeMensagemRepositorio.Chave, String> atuais(List<ReacaoDeMensagemRepositorio.Chave> chaves);
}
