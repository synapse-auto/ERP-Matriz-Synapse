package com.synapse.crm.equipe.domain.sessao;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Refresh token: valor opaco aleatorio, guardado no banco apenas como hash.
 *
 * <p>Opaco, e nao JWT, porque refresh precisa ser revogavel — um JWT so deixa de valer quando
 * expira. E so o hash e persistido: um backup vazado nao vira sessao valida, mesmo principio de
 * {@code canal_credencial.token_ref}.
 *
 * <p>Java puro (apenas JDK), entao continua sendo dominio.
 */
public final class TokenOpaco {

    private static final SecureRandom ALEATORIO = new SecureRandom();
    private static final int BYTES = 32;

    private TokenOpaco() {}

    public static String gerar() {
        byte[] bytes = new byte[BYTES];
        ALEATORIO.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** SHA-256 em hexadecimal minusculo — 64 caracteres, casa com {@code refresh_token.token_hash}. */
    public static String hash(String token) {
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(sha256.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 deveria existir em qualquer JVM", e);
        }
    }
}
