package com.synapse.crm.equipe.domain.usuario;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.synapse.crm.sharedkernel.identidade.PapelUsuario;
import com.synapse.crm.sharedkernel.identidade.UsuarioAutenticado;

/** Usuario do CRM. Java puro: sem JPA, sem Spring. */
public record Usuario(
        UUID id, String nome, String email, String senhaHash, PapelUsuario papel,
        StatusPresenca statusPresenca, boolean ativo, boolean disponivelParaIa,
        String telefone, String cargo,
        String fotoReferencia,
        /**
         * {@code null} = senha provisoria, nunca trocada pelo dono (E29). Usuario recem-criado ou
         * que teve a senha redefinida por um gestor comeca assim, de proposito.
         */
        Instant senhaAlteradaEm) {

    /** Compatibilidade para os casos que só precisam de identidade e autenticação. */
    public Usuario(
            UUID id, String nome, String email, String senhaHash, PapelUsuario papel,
            StatusPresenca statusPresenca, boolean ativo, boolean disponivelParaIa,
            Instant senhaAlteradaEm) {
        this(id, nome, email, senhaHash, papel, statusPresenca, ativo, disponivelParaIa,
                null, null, null, senhaAlteradaEm);
    }

    /** Compatibilidade para os adaptadores que ainda nao precisam da referencia da foto. */
    public Usuario(
            UUID id, String nome, String email, String senhaHash, PapelUsuario papel,
            StatusPresenca statusPresenca, boolean ativo, boolean disponivelParaIa,
            String telefone, String cargo, Instant senhaAlteradaEm) {
        this(id, nome, email, senhaHash, papel, statusPresenca, ativo, disponivelParaIa,
                telefone, cargo, null, senhaAlteradaEm);
    }

    public Usuario {
        Objects.requireNonNull(id, "id e obrigatorio");
        Objects.requireNonNull(email, "email e obrigatorio");
        Objects.requireNonNull(senhaHash, "senhaHash e obrigatorio");
        Objects.requireNonNull(papel, "papel e obrigatorio");
        Objects.requireNonNull(statusPresenca, "statusPresenca e obrigatorio");
    }

    /** {@code true} enquanto a senha nao foi trocada pelo proprio dono nem uma vez. */
    public boolean precisaTrocarSenha() {
        return senhaAlteradaEm == null;
    }

    public UsuarioAutenticado comoAutenticado() {
        return new UsuarioAutenticado(id, papel, precisaTrocarSenha());
    }
}
