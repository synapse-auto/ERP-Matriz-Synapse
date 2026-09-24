package com.synapse.crm.atendimento.infrastructure.canal;

import java.util.Locale;
import java.util.Set;

/**
 * Vocabulario fechado para registrar o tipo de um item descartado.
 *
 * <p>O {@code type} vem do provedor e e texto livre: gravado cru, um tipo novo ou malformado viraria
 * uma serie nova a cada variacao, e um valor absurdo poderia carregar conteudo. Aqui ficam apenas os
 * tipos documentados pela Meta Cloud API e pelo Swagger da Uzapi/Autotic; o resto vira
 * {@link #OUTRO}. Aumentar a lista e seguro — ela so afeta a leitura operacional do descarte.
 */
final class TipoDeItemDoProvedor {

    static final String OUTRO = "outro";

    private static final Set<String> DOCUMENTADOS = Set.of(
            "text", "image", "audio", "video", "document", "sticker", "location", "contacts",
            "interactive", "button", "button_reply", "list_reply", "reaction", "order", "system",
            "unsupported", "unknown", "request_welcome", "edit", "revoke", "poll");

    private TipoDeItemDoProvedor() {}

    static String normalizar(String tipoDoProvedor) {
        if (tipoDoProvedor == null) {
            return OUTRO;
        }
        String normalizado = tipoDoProvedor.trim().toLowerCase(Locale.ROOT);
        return DOCUMENTADOS.contains(normalizado) ? normalizado : OUTRO;
    }
}
