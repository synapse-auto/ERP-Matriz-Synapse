package com.synapse.crm.equipe.application.usuario;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.sharedkernel.persistencia.Pools;

/** A negacao sobrevive ao rollback do comando enfileirado, sem persistir seu conteudo. */
@Service
public class AuditarBloqueioContaTesteUseCase {
    private final ClassificacaoDeTesteRepositorio repositorio;
    public AuditarBloqueioContaTesteUseCase(ClassificacaoDeTesteRepositorio repositorio){this.repositorio=repositorio;}
    @Transactional(transactionManager=Pools.CHAT_TRANSACTION_MANAGER,propagation=Propagation.REQUIRES_NEW)
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('SERVICO')")
    public void executar(UUID usuario,String operacao){repositorio.registrarBloqueio(usuario,operacao);}
}
