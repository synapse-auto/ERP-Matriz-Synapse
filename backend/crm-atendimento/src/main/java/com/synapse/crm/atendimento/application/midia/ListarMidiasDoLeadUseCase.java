package com.synapse.crm.atendimento.application.midia;

import java.util.List;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.sharedkernel.persistencia.Pools;

@Service
public class ListarMidiasDoLeadUseCase {
    private final MidiasDoLeadRepositorio repositorio;

    public ListarMidiasDoLeadUseCase(MidiasDoLeadRepositorio repositorio) {
        this.repositorio = repositorio;
    }

    @PreAuthorize("isAuthenticated()")
    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER, readOnly = true)
    public List<MidiaDoLead> executar(UUID leadId, int pagina, int tamanho) {
        int limite = Math.min(50, Math.max(1, tamanho));
        int deslocamento = Math.max(0, pagina) * limite;
        return repositorio.listar(leadId, limite, deslocamento);
    }

    @PreAuthorize("isAuthenticated()")
    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER, readOnly = true)
    public MidiaDoLead executar(UUID leadId, UUID mensagemId) {
        return repositorio.porMensagem(leadId, mensagemId).orElseThrow(
                () -> new MidiaDoLeadNaoEncontradaException(leadId, mensagemId));
    }
}
