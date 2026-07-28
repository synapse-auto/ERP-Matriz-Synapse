package com.synapse.crm.atendimento.infrastructure.tempo_real;

import java.util.UUID;

import com.synapse.crm.sharedkernel.identidade.PapelUsuario;

/**
 * Uma assinatura que passou pela autorizacao — o registro que substitui "confiar no cliente sobre em
 * que ele esta inscrito".
 *
 * <p>Guarda {@code papel} junto, capturado no momento da autorizacao. E o que permite a revogacao
 * (E06 secao 1) decidir se uma sessao continua autorizada apos uma transferencia <b>sem</b> reabrir
 * transacao de banco em nome de um usuario que nao esta fazendo a requisicao — a mesma pergunta que
 * {@code Atendimento.visivelPara} responde em Java puro, com os dois ingredientes que tambem formam a
 * politica RLS: papel amplo, ou dono, ou sem dono.
 */
record AssinaturaAutorizada(
        String sessionId,
        String subscriptionId,
        UUID atendimentoId,
        UUID usuarioId,
        PapelUsuario papel) {}
