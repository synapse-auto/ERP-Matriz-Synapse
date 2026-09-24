package com.synapse.crm.atendimento.infrastructure.canal;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Le o cartao de contato de {@code messages[].contacts[]} e devolve os metadados do CRM.
 *
 * <p>Meta e Uzapi/Autotic documentam o mesmo formato ({@code name.formatted_name} e
 * {@code phones[].phone}); a Meta acrescenta {@code phones[].wa_id} quando o numero tem WhatsApp.
 * Os aliases em camelCase absorvem a variacao que a Uzapi ja mostrou em outros eventos.
 *
 * <p>Atencao ao nome parecido: {@code value.contacts[]} do envelope identifica o <b>remetente</b> e
 * nunca passa por aqui. Quem chama entrega somente o array de dentro da mensagem.
 *
 * <p>Formato gravado em {@code midia_metadados}:
 *
 * <pre>{"contatos":[{"nome":"...","telefones":[{"numero":"...","waId":"...","tipo":"..."}]}]}</pre>
 *
 * {@code nome}, {@code waId} e {@code tipo} so aparecem quando o provedor os enviou. Contato sem
 * telefone e preservado com {@code telefones: []}; contato sem nome e sem telefone nao carrega nada
 * que o atendente possa usar e fica de fora.
 */
final class ContatoCompartilhado {

    private ContatoCompartilhado() {}

    /** Vazio quando nenhum contato do array tem nome ou telefone. */
    static Optional<String> metadados(JsonNode contatosDaMensagem, ObjectMapper json) {
        if (contatosDaMensagem == null || !contatosDaMensagem.isArray()) {
            return Optional.empty();
        }
        ArrayNode contatos = json.createArrayNode();
        for (JsonNode contato : contatosDaMensagem) {
            lerContato(contato, json).ifPresent(contatos::add);
        }
        if (contatos.isEmpty()) {
            return Optional.empty();
        }
        ObjectNode metadados = json.createObjectNode();
        metadados.set("contatos", contatos);
        return Optional.of(metadados.toString());
    }

    private static Optional<ObjectNode> lerContato(JsonNode contato, ObjectMapper json) {
        if (contato == null || !contato.isObject()) {
            return Optional.empty();
        }
        String nome = nome(contato.path("name"));
        ArrayNode telefones = telefones(contato.path("phones"), json);
        if (nome == null && telefones.isEmpty()) {
            return Optional.empty();
        }
        ObjectNode lido = json.createObjectNode();
        if (nome != null) {
            lido.put("nome", nome);
        }
        lido.set("telefones", telefones);
        return Optional.of(lido);
    }

    private static String nome(JsonNode nome) {
        String formatado = primeiroTexto(nome, "formatted_name", "formattedName");
        if (formatado != null) {
            return formatado;
        }
        List<String> partes = new ArrayList<>();
        for (String[] aliases : new String[][] {
            {"prefix"}, {"first_name", "firstName"}, {"middle_name", "middleName"},
            {"last_name", "lastName"}, {"suffix"}
        }) {
            String parte = primeiroTexto(nome, aliases);
            if (parte != null) {
                partes.add(parte);
            }
        }
        return partes.isEmpty() ? null : String.join(" ", partes);
    }

    private static ArrayNode telefones(JsonNode telefones, ObjectMapper json) {
        ArrayNode lidos = json.createArrayNode();
        if (!telefones.isArray()) {
            return lidos;
        }
        for (JsonNode telefone : telefones) {
            String waId = primeiroTexto(telefone, "wa_id", "waId");
            String numero = primeiroTexto(telefone, "phone", "number");
            if (numero == null) {
                numero = waId;
            }
            if (numero == null) {
                continue;
            }
            ObjectNode lido = lidos.addObject();
            lido.put("numero", numero);
            if (waId != null) {
                lido.put("waId", waId);
            }
            String tipo = primeiroTexto(telefone, "type");
            if (tipo != null) {
                lido.put("tipo", tipo);
            }
        }
        return lidos;
    }

    private static String primeiroTexto(JsonNode no, String... campos) {
        if (no == null || !no.isObject()) {
            return null;
        }
        for (String campo : campos) {
            JsonNode valor = no.path(campo);
            if (valor.isTextual() && !valor.asText().isBlank()) {
                return valor.asText().trim();
            }
        }
        return null;
    }
}
