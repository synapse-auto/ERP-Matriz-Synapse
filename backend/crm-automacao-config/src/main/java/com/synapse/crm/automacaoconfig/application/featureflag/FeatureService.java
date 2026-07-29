package com.synapse.crm.automacaoconfig.application.featureflag;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.automacaoconfig.domain.featureflag.FeatureFlag;

/**
 * {@code GET /api/v1/config/features} — o frontend decide o menu a partir daqui (E07 §4).
 *
 * <p>So habilitadas, e nao o array inteiro com o booleano: uma flag desligada precisa <b>sumir</b> da
 * resposta, nao aparecer como {@code false} — e a diferenca entre o frontend nao desenhar a aba
 * (Nivel 2 da Base PAI) e ter que lembrar de checar um campo por aba toda vez que monta o menu.
 */
@Service
public class FeatureService {

    private final FeatureFlagRepositorio flags;

    public FeatureService(FeatureFlagRepositorio flags) {
        this.flags = flags;
    }

    @PreAuthorize("isAuthenticated()")
    @Transactional(readOnly = true)
    public List<String> habilitadas() {
        return flags.listarTodas().stream()
                .filter(FeatureFlag::habilitado)
                .map(FeatureFlag::chave)
                .toList();
    }
}
