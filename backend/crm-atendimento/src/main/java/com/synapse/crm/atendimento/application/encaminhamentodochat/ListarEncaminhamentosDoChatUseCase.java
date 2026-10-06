package com.synapse.crm.atendimento.application.encaminhamentodochat;

import java.util.List;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.atendimento.application.encaminhamentodochat.EncaminhamentoDoChatRepositorio.EncaminhamentoComStatus;
import com.synapse.crm.sharedkernel.identidade.UsuarioContext;
import com.synapse.crm.sharedkernel.persistencia.Pools;

/**
 * O que o usuário já encaminhou de uma mensagem, com o estado atual da entrega: é o que mantém a tela
 * atualizada depois do envio, sem F5. Só as linhas do próprio usuário, e o estado vem de {@code mensagem},
 * lida sob a RLS do atendimento.
 */
@Service
public class ListarEncaminhamentosDoChatUseCase {

    private final EncaminhamentoDoChatRepositorio encaminhamentos;
    private final UsuarioContext usuario;

    public ListarEncaminhamentosDoChatUseCase(EncaminhamentoDoChatRepositorio encaminhamentos, UsuarioContext usuario) {
        this.encaminhamentos = encaminhamentos;
        this.usuario = usuario;
    }

    @PreAuthorize("isAuthenticated()")
    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER, readOnly = true)
    public List<EncaminhamentoComStatus> executar(UUID mensagemInternaId) {
        return encaminhamentos.daMensagem(usuario.atual().id(), mensagemInternaId);
    }
}
