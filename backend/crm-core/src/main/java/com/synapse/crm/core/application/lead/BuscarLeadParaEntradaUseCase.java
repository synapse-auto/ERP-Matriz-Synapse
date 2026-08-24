package com.synapse.crm.core.application.lead;
import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.sharedkernel.identidade.UsuarioContext;
@Service
public class BuscarLeadParaEntradaUseCase {
 private final LeadParaEntradaRepositorio repositorio; private final UsuarioContext usuario;
 public BuscarLeadParaEntradaUseCase(LeadParaEntradaRepositorio r, UsuarioContext u){repositorio=r;usuario=u;}
 @PreAuthorize("hasRole('ATENDENTE')") @Transactional(readOnly=true)
 public List<LeadParaEntrada> executar(String termo){ if(termo==null||termo.isBlank()||termo.length()<2)return List.of(); return repositorio.buscar(termo.trim(),usuario.atual().id()); }
}
