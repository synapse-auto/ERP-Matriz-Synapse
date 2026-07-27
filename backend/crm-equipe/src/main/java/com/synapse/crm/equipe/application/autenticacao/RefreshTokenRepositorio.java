package com.synapse.crm.equipe.application.autenticacao;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Porta do armazenamento de refresh tokens.
 *
 * <p>O estado mora no banco, nao em memoria de instancia: a aplicacao precisa escalar
 * horizontalmente e uma renovacao pode cair em qualquer no.
 */
public interface RefreshTokenRepositorio {

    /** @param tokenHash SHA-256 do token; o token em si nunca chega aqui */
    RefreshTokenArmazenado salvar(UUID usuarioId, UUID familia, String tokenHash, Instant expiraEm);

    Optional<RefreshTokenArmazenado> porHash(String tokenHash);

    void revogar(UUID id);

    /** Reuso de token ja revogado e sinal de roubo: derruba a cadeia inteira daquele login. */
    void revogarFamilia(UUID familia);

    void revogarTodosDoUsuario(UUID usuarioId);

    record RefreshTokenArmazenado(
            UUID id, UUID usuarioId, UUID familia, Instant expiraEm, Instant revogadoEm) {

        public boolean estaRevogado() {
            return revogadoEm != null;
        }

        public boolean estaExpirado(Instant agora) {
            return expiraEm.isBefore(agora);
        }
    }
}
