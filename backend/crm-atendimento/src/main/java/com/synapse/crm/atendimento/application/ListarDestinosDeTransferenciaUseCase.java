package com.synapse.crm.atendimento.application;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
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
    private final DestinoHumanoAutorizado autorizacao;

    public ListarDestinosDeTransferenciaUseCase(AtendenteParaTransferenciaRepositorio destinos) {
        this(destinos, null);
    }

    @Autowired
    public ListarDestinosDeTransferenciaUseCase(AtendenteParaTransferenciaRepositorio destinos,
            DestinoHumanoAutorizado autorizacao) {
        this.destinos = destinos;
        this.autorizacao = autorizacao;
    }

    @PreAuthorize("hasAnyRole('ATENDENTE','GESTOR','SUBGESTOR','ADMINISTRADOR','OPERADOR')")
    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER, readOnly = true)
    public List<AtendenteParaTransferenciaRepositorio.Destino> executar() {
        return destinos.listarAtivos().stream()
                .filter(d -> autorizacao != null ? autorizacao.permitido(d)
                        : d.papel() != com.synapse.crm.sharedkernel.identidade.PapelUsuario.OPERADOR)
                .toList();
    }
}
