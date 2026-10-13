package com.synapse.crm.atendimento.domain.evento;

import java.time.Instant;
import java.util.UUID;

/**
 * Um atendente abriu o contato pela Agenda (sem enviar mensagem) e ficou como o responsável do atendimento.
 *
 * <p>Não pertence a {@link EventoDeAtendimento}, de propósito: aquele tipo é selado e alimenta timeline, auditoria e
 * tempo real, que já recebem o sinal canônico dessa abertura. Este fato existe só para a reação do atendente
 * vinculado (D3b) e, como os demais, é consumido depois do commit. Abrir a ficha de um lead <b>não</b> o publica.
 */
public record ResponsavelDoContatoDefinido(UUID leadId, UUID atendimentoId, UUID atendenteId, Instant ocorridoEm) {}
