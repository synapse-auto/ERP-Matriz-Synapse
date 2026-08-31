package com.synapse.crm.atendimento.application;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.sharedkernel.persistencia.Pools;

/**
 * Lista quem pode receber uma transferência humana: atendentes ativos.
 *
 * <p>Não é {@code GET /api/v1/usuarios}: aquele devolve e-mail, papel e presença, e é de gestão.
 * Também não é {@code GET /internal/v1/atendentes/disponiveis}: aquele é o rodízio da IA
 * (disponível + online), autenticado por token de serviço.
 */
@Service
public class ListarDestinosDeTransferenciaUseCase {

    private final AtendenteParaTransferenciaRepositorio destinos;

    public ListarDestinosDeTransferenciaUseCase(AtendenteParaTransferenciaRepositorio destinos) {
        this.destinos = destinos;
    }

    @PreAuthorize("hasAnyRole('ATENDENTE','GESTOR','SUBGESTOR','ADMINISTRADOR')")
    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER, readOnly = true)
    public List<AtendenteParaTransferenciaRepositorio.Destino> executar() {
        return destinos.listarAtivos();
    }
}
