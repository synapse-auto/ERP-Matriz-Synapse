package com.synapse.crm.automacaoconfig.application;

import java.util.List;
import java.util.Optional;

import com.synapse.crm.automacaoconfig.domain.ConfiguracaoAutomacao;

/**
 * Porta de acesso a {@code configuracao_automacao}.
 *
 * <p>Tabela global da instancia, sem recorte de visibilidade — nao ha RLS aqui, ao contrario de
 * {@code lead}/{@code atendimento}: parametro de automacao nao pertence a ninguem em particular.
 */
public interface ConfiguracaoAutomacaoRepositorio {

    /** Todos os parametros, para {@code /internal/v1/automation-config}. */
    List<ConfiguracaoAutomacao> listarTodas();

    /** Um parametro especifico, por chave. */
    Optional<ConfiguracaoAutomacao> porChave(String chave);

    /** Atualiza (a linha ja existe sempre — nao ha criacao pela API; fase 1 e so leitura+edicao). */
    ConfiguracaoAutomacao salvar(ConfiguracaoAutomacao configuracao);
}
