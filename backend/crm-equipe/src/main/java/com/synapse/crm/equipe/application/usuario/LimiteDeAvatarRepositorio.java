package com.synapse.crm.equipe.application.usuario;

import java.util.Optional;

/** Le o limite configuravel de imagem; nesta etapa reutiliza a chave de anexos de imagem. */
public interface LimiteDeAvatarRepositorio {

    Optional<Long> limiteEmBytes();
}
