package com.synapse.crm.app.equipe;

import java.time.Duration;

import org.awaitility.Awaitility;

import com.synapse.crm.atendimento.application.VinculoDoLeadRepositorio;
import com.synapse.crm.equipe.application.permissao.ResolvedorDePermissoesEfetivas;

/**
 * O rodizio da IA le "setores" e a chave do vinculado de memoria (E231), com o atraso do intervalo de revalidacao
 * (50ms nos ITs). Quem liga ou desliga isso por SQL espera o valor chegar antes de agir, como
 * {@code GestaoPermissoesIT} faz com as flags de permissao.
 */
final class CachesDeConfiguracao {

    private CachesDeConfiguracao() {}

    static void aguardarSetores(ResolvedorDePermissoesEfetivas flags, boolean ligado) {
        Awaitility.await().atMost(Duration.ofSeconds(10)).until(() -> flags.flagsHabilitadas().contains("setores") == ligado);
    }

    static void aguardarVinculado(VinculoDoLeadRepositorio vinculos, boolean ligado) {
        Awaitility.await().atMost(Duration.ofSeconds(10)).until(() -> vinculos.habilitado() == ligado);
    }
}
