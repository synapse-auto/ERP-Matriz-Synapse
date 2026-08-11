package com.synapse.crm.core.application.etapa;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.core.domain.etapa.EtapaAtendimento;
import com.synapse.crm.core.domain.etapa.ResultadoEtapa;

/**
 * Casos de uso das etapas do funil.
 *
 * <p>Leitura para todos — o atendente precisa das etapas para montar o Kanban. Escrita so para
 * gestor: mudar o funil muda a operacao inteira, e a Automacao le essas etapas.
 */
@Service
public class GestaoDeEtapasUseCases {

    private static final String SO_GESTOR = "hasAnyRole('GESTOR', 'ADMINISTRADOR')";

    private final EtapaRepositorio etapas;

    public GestaoDeEtapasUseCases(EtapaRepositorio etapas) {
        this.etapas = etapas;
    }

    @PreAuthorize("isAuthenticated()")
    public List<EtapaAtendimento> listar() {
        return etapas.listarEmOrdem();
    }

    @PreAuthorize(SO_GESTOR)
    @Transactional
    public EtapaAtendimento criar(
            String nome, short ordem, String corVisual, ResultadoEtapa resultado) {
        ResultadoEtapa resultadoEfetivo =
                resultado == null ? ResultadoEtapa.EM_ANDAMENTO : resultado;
        return etapas.salvar(EtapaAtendimento.nova(nome, ordem, corVisual, resultadoEfetivo));
    }

    @PreAuthorize(SO_GESTOR)
    @Transactional
    public Optional<EtapaAtendimento> atualizar(
            UUID id,
            String nome,
            short ordem,
            String corVisual,
            ResultadoEtapa resultado) {
        return etapas.porId(id)
                .map(existente -> etapas.salvar(existente.com(nome, ordem, corVisual, resultado)));
    }

    @PreAuthorize(SO_GESTOR)
    @Transactional
    public boolean remover(UUID id) {
        if (etapas.porId(id).isEmpty()) {
            return false;
        }
        etapas.remover(id);
        return true;
    }
}
