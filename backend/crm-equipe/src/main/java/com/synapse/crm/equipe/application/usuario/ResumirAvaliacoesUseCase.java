package com.synapse.crm.equipe.application.usuario;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.equipe.domain.avaliacao.ResumoAvaliacoes;
@Service public class ResumirAvaliacoesUseCase{private final EquipeRepositorio equipe;public ResumirAvaliacoesUseCase(EquipeRepositorio e){equipe=e;}@PreAuthorize("hasAnyRole('GESTOR','ADMINISTRADOR')")@Transactional(readOnly=true)public ResumoAvaliacoes executar(){return equipe.resumirAvaliacoes();}}
