package com.synapse.crm.equipe.application.usuario;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.sharedkernel.identidade.UsuarioContext;

/** A tentativa negada e auditada e commitada antes de o filtro devolver 403. */
@Service
public class AutorizarOperacaoDeContaUseCase {
    private final UsuarioContext contexto;
    private final ClassificacaoDeTesteRepositorio repositorio;
    public AutorizarOperacaoDeContaUseCase(UsuarioContext contexto, ClassificacaoDeTesteRepositorio repositorio) {
        this.contexto=contexto; this.repositorio=repositorio;
    }
    @Transactional(transactionManager = com.synapse.crm.sharedkernel.persistencia.Pools.CHAT_TRANSACTION_MANAGER)
    @org.springframework.security.access.prepost.PreAuthorize("isAuthenticated()")
    public boolean executar(String operacao) {
        var usuario=contexto.atual().id();
        if (!repositorio.contaDeTeste(usuario)) return true;
        repositorio.registrarBloqueio(usuario, operacao);
        return false;
    }
}
