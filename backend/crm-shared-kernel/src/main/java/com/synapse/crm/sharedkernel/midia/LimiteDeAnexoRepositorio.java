package com.synapse.crm.sharedkernel.midia;

import java.util.Optional;

/**
 * Le o limite de tamanho de anexo configurado.
 * A interface e neutra para injecao em crm-atendimento ou crm-equipe.
 */
public interface LimiteDeAnexoRepositorio {

    Optional<Long> limiteEmBytes(CategoriaDeMidia tipo);

    /** Limite da captura no navegador; vazio indica configuracao incompleta. */
    Optional<Long> duracaoMaximaAudioEmSegundos();
}
