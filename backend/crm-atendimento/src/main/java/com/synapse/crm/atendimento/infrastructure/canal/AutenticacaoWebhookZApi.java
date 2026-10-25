package com.synapse.crm.atendimento.infrastructure.canal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Posse de segredo independente; nao e assinatura do corpo nem protocolo nativo presumido. */
public final class AutenticacaoWebhookZApi {
    private final boolean habilitada;
    private final byte[] esperado;

    public AutenticacaoWebhookZApi(boolean habilitada, String segredo) {
        if (habilitada && (segredo == null || !segredo.matches("[A-Za-z0-9_-]{43,256}"))) {
            throw new IllegalArgumentException("Z_API_WEBHOOK_SEGREDO_INVALIDO");
        }
        this.habilitada = habilitada;
        this.esperado = hash(segredo == null ? "" : segredo);
    }

    public boolean confere(String recebido) {
        // Ambos os operandos tem 32 bytes: diferenca de comprimento do token nao causa
        // retorno antecipado na comparacao. Limite HTTP de headers/URL continua no container.
        boolean igual = MessageDigest.isEqual(esperado, hash(recebido == null ? "" : recebido));
        return habilitada && recebido != null && igual;
    }

    private static byte[] hash(String valor) {
        try { return MessageDigest.getInstance("SHA-256").digest(valor.getBytes(StandardCharsets.UTF_8)); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException("JVM_SEM_SHA256", e); }
    }

    @Override public String toString() { return "AutenticacaoWebhookZApi[segredo=REDACTED]"; }
}
