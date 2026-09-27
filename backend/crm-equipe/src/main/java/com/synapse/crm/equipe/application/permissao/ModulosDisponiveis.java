package com.synapse.crm.equipe.application.permissao;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

import com.synapse.crm.equipe.domain.permissao.Modulo;
import com.synapse.crm.equipe.domain.permissao.PoliticaDePermissoes;

final class ModulosDisponiveis {

    private ModulosDisponiveis() {}

    static Set<Modulo> com(Set<String> flags) {
        return Arrays.stream(Modulo.values())
                .filter(m -> PoliticaDePermissoes.disponivel(m, flags))
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(Modulo.class)));
    }
}
