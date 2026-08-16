package com.synapse.crm.atendimento.application.canal;

import java.util.Set;

/** Estado persistido dos canais ativos desta instancia, sem qualquer referencia de segredo. */
public record ConfiguracaoCanalAtivo(
        int quantidadeCanaisAtivos,
        int quantidadeSemIdentificadorExterno,
        Set<String> identificadoresExternos) {

    public ConfiguracaoCanalAtivo {
        identificadoresExternos = Set.copyOf(identificadoresExternos);
    }

    public boolean completa() {
        return quantidadeCanaisAtivos > 0 && quantidadeSemIdentificadorExterno == 0;
    }
}
