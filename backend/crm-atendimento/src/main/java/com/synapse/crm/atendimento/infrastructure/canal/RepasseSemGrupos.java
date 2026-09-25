package com.synapse.crm.atendimento.infrastructure.canal;

import java.util.Iterator;
import java.util.function.Predicate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Remove as mensagens de grupo do envelope {@code entry[].changes[].value.messages[]} antes do
 * repasse a Automacao. Formato comum a Meta e Uzapi; quem sabe o que e grupo e o adaptador.
 */
final class RepasseSemGrupos {

    private RepasseSemGrupos() {}

    sealed interface Resultado {
        /** Nenhum item de grupo: o repasse usa o corpo original, byte a byte. */
        record Intacto() implements Resultado {}

        /** So havia grupo (e nada mais a repassar): nao repassa. */
        record SemConteudo() implements Resultado {}

        /** POST misto: novo corpo, no mesmo envelope, sem os itens de grupo. */
        record Filtrado(String payload) implements Resultado {}
    }

    static Resultado filtrar(String payloadCru, ObjectMapper json, Predicate<JsonNode> ehGrupo) {
        JsonNode raiz;
        try {
            raiz = json.readTree(payloadCru);
        } catch (JsonProcessingException | RuntimeException e) {
            // Ilegivel: o comportamento anterior (repassar cru) e preservado.
            return new Resultado.Intacto();
        }
        int removidos = 0;
        boolean sobrouAlgo = false;
        for (JsonNode entrada : raiz.path("entry")) {
            for (JsonNode mudanca : entrada.path("changes")) {
                JsonNode valor = mudanca.path("value");
                JsonNode mensagens = valor.path("messages");
                if (mensagens.isArray()) {
                    Iterator<JsonNode> itens = mensagens.elements();
                    while (itens.hasNext()) {
                        if (ehGrupo.test(itens.next())) {
                            itens.remove();
                            removidos++;
                        }
                    }
                    sobrouAlgo |= !mensagens.isEmpty();
                }
                sobrouAlgo |= valor.path("statuses").isArray() && !valor.path("statuses").isEmpty();
            }
        }
        if (removidos == 0) {
            return new Resultado.Intacto();
        }
        if (!sobrouAlgo) {
            return new Resultado.SemConteudo();
        }
        try {
            return new Resultado.Filtrado(json.writeValueAsString(raiz));
        } catch (JsonProcessingException e) {
            // Nao da para reescrever com seguranca: melhor nao repassar grupo nenhum.
            return new Resultado.SemConteudo();
        }
    }
}
