package com.synapse.crm.atendimento.infrastructure.canal;

import java.time.Instant;

import com.fasterxml.jackson.databind.JsonNode;

import com.synapse.crm.atendimento.domain.canal.TradutorDeCanal.MotivoDeDescarte;
import com.synapse.crm.atendimento.domain.canal.TradutorDeCanal.ReacaoRecebidaDoCanal;
import com.synapse.crm.sharedkernel.emoji.EmojiInvalidoException;
import com.synapse.crm.sharedkernel.emoji.EmojiUnicode;

/**
 * Leitura de {@code type: reaction}, igual na Meta Cloud API e no Swagger da Uzapi:
 * {@code reaction.message_id} e o id externo da mensagem reagida e {@code reaction.emoji} e o emoji
 * atual. Emoji vazio ou ausente e a remocao da reacao — os dois provedores sinalizam assim.
 *
 * <p>Nada daqui vai para log: emoji e id da mensagem sao conteudo da conversa.
 */
final class ReacaoDoProvedor {

    private ReacaoDoProvedor() {}

    static ReacaoRecebidaDoCanal ler(
            JsonNode mensagem,
            String idExterno,
            String telefoneRemetente,
            String identificadorDestino,
            Instant reagidoEm) {
        if (idExterno == null || idExterno.isBlank() || telefoneRemetente == null || telefoneRemetente.isBlank()) {
            throw new ItemNaoTraduzido(MotivoDeDescarte.SEM_IDENTIFICADOR);
        }
        JsonNode reacao = mensagem.path("reaction");
        if (!reacao.isObject()) {
            throw new ItemNaoTraduzido(MotivoDeDescarte.CONTEUDO_INVALIDO);
        }
        String alvo = texto(reacao, "message_id");
        if (alvo == null) {
            alvo = texto(reacao, "messageId");
        }
        if (alvo == null) {
            // Sem alvo nao ha a que associar; nunca "adivinhar" a ultima mensagem.
            throw new ItemNaoTraduzido(MotivoDeDescarte.CONTEUDO_INVALIDO);
        }
        String emoji = texto(reacao, "emoji");
        if (emoji != null) {
            try {
                emoji = EmojiUnicode.validar(emoji);
            } catch (EmojiInvalidoException e) {
                throw new ItemNaoTraduzido(MotivoDeDescarte.CONTEUDO_INVALIDO);
            }
        }
        return new ReacaoRecebidaDoCanal(
                idExterno, telefoneRemetente, identificadorDestino, alvo, emoji, reagidoEm);
    }

    private static String texto(JsonNode no, String campo) {
        JsonNode valor = no.path(campo);
        if (!valor.isTextual()) {
            return null;
        }
        String lido = valor.asText();
        return lido.isBlank() ? null : lido;
    }
}
