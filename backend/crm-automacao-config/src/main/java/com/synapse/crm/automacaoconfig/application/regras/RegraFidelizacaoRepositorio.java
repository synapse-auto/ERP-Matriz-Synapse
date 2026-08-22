package com.synapse.crm.automacaoconfig.application.regras;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.synapse.crm.automacaoconfig.domain.regras.RegraFidelizacao;

public interface RegraFidelizacaoRepositorio {
    List<RegraFidelizacao> listarAtivas();
    List<RegraFidelizacao> listarTodas();
    Optional<RegraFidelizacao> porId(UUID id);
    RegraFidelizacao salvar(RegraFidelizacao regra);
    void excluir(UUID id);
}
