package com.synapse.crm.equipe.application.chat;

import java.util.UUID;

import com.synapse.crm.equipe.domain.chat.EventoDeSistemaChat;

/** Monta o JSON de mensagens SISTEMA do chat interno (sem dependencia de Jackson no modulo). */
final class ConteudoDeSistemaChat {

    private ConteudoDeSistemaChat() {}

    static String grupoCriado(String nome) {
        return "{\"evento\":\"" + EventoDeSistemaChat.GRUPO_CRIADO.name()
                + "\",\"nome\":" + jsonString(nome) + "}";
    }

    static String participanteAdicionado(UUID alvoId, String alvoNome) {
        return alvo(EventoDeSistemaChat.PARTICIPANTE_ADICIONADO, alvoId, alvoNome);
    }

    static String participanteRemovido(UUID alvoId, String alvoNome) {
        return alvo(EventoDeSistemaChat.PARTICIPANTE_REMOVIDO, alvoId, alvoNome);
    }

    static String participanteSaiu(UUID alvoId, String alvoNome) {
        return alvo(EventoDeSistemaChat.PARTICIPANTE_SAIU, alvoId, alvoNome);
    }

    static String nomeAlterado(String anterior, String novo) {
        return "{\"evento\":\"" + EventoDeSistemaChat.NOME_ALTERADO.name()
                + "\",\"nomeAnterior\":" + jsonString(anterior)
                + ",\"nome\":" + jsonString(novo) + "}";
    }

    private static String alvo(EventoDeSistemaChat evento, UUID alvoId, String alvoNome) {
        return "{\"evento\":\"" + evento.name()
                + "\",\"alvoId\":" + jsonString(alvoId.toString())
                + ",\"alvoNome\":" + jsonString(alvoNome) + "}";
    }

    private static String jsonString(String valor) {
        if (valor == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder(valor.length() + 2);
        sb.append('"');
        for (int i = 0; i < valor.length(); i++) {
            char c = valor.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
        return sb.toString();
    }
}
