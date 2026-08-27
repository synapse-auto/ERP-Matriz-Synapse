package com.synapse.crm.app.inbox;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

import com.synapse.crm.atendimento.application.painel.CartaoAtendimento;
import com.synapse.crm.atendimento.domain.atendimento.StatusAtendimento;

/** Contrato discriminado da lista de Atendimentos. Nunca mistura os modelos de domínio. */
public record InboxUnificada(List<Item> itens, String proximoCursor) {
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Item(
            Tipo tipo,
            UUID atendimentoId,
            UUID conversaId,
            String nome,
            String avatarUrl,
            String identificadorVisual,
            String ultimaMensagemPreview,
            String ultimaMensagemRemetenteTipo,
            Instant ultimaMensagemEm,
            Instant ultimaMensagemDoLeadEm,
            long naoLidas,
            UUID leadId,
            String leadNome,
            String leadFotoUrl,
            String leadEmpresa,
            String canalTipo,
            StatusAtendimento status,
            UUID etapaId,
            String etapaNome,
            String etapaCor,
            UUID atendenteId,
            String atendenteNome,
            UUID atendimentoAtivoId,
            String participantes,
            String tipoConversa) {

        public static Item cliente(CartaoAtendimento cartao) {
            return new Item(
                    Tipo.CLIENTE,
                    cartao.atendimentoId(),
                    null,
                    cartao.leadNome(),
                    cartao.leadFotoUrl(),
                    cartao.atendimentoId().toString(),
                    cartao.ultimaMensagemPreview(),
                    cartao.ultimaMensagemRemetenteTipo(),
                    cartao.ultimaMensagemEm(),
                    cartao.ultimaMensagemDoLeadEm(),
                    cartao.naoLidas(),
                    cartao.leadId(),
                    cartao.leadNome(),
                    cartao.leadFotoUrl(),
                    cartao.leadEmpresa(),
                    cartao.canalTipo(),
                    cartao.status(),
                    cartao.etapaId(),
                    cartao.etapaNome(),
                    cartao.etapaCor(),
                    cartao.atendenteId(),
                    cartao.atendenteNome(),
                    cartao.atendimentoAtivoId(),
                    null,
                    null);
        }

        public static Item equipe(
                UUID id,
                String participantes,
                String preview,
                Instant ultimaMensagemEm,
                long naoLidas,
                String tipoConversa) {
            return new Item(
                    Tipo.EQUIPE_INTERNA,
                    null,
                    id,
                    participantes,
                    null,
                    id.toString(),
                    preview,
                    null,
                    ultimaMensagemEm,
                    null,
                    naoLidas,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    participantes,
                    tipoConversa);
        }
    }

    public enum Tipo { CLIENTE, EQUIPE_INTERNA }
}
