package com.synapse.crm.atendimento.domain.canal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Set;

/** Identidade do provedor, nunca intercambiavel com um ID global ou com outro namespace. */
public record IdentidadeDeMensagemDoCanal(String provedor, String instancia, String namespace, String valor) {
    public IdentidadeDeMensagemDoCanal {
        for (String campo : new String[] {provedor, instancia, namespace, valor}) {
            if (campo == null || campo.isBlank() || campo.chars().anyMatch(Character::isISOControl)) {
                throw new IllegalArgumentException("IDENTIDADE_DE_CANAL_INVALIDA");
            }
        }
        if (!Set.of("WHATSAPP", "PROCESSAMENTO").contains(namespace)) {
            throw new IllegalArgumentException("NAMESPACE_DE_CANAL_INVALIDO");
        }
    }

    /** Chave tecnica interna de deduplicacao; valor bruto continua preservado separadamente. */
    public String chaveInterna() {
        return "canal:" + digest(provedor + '\0' + instancia + '\0' + namespace + '\0' + valor);
    }

    private static String digest(String valor) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(valor.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
