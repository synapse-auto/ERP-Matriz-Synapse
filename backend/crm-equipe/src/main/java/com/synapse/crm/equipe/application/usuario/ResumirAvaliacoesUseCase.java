package com.synapse.crm.equipe.application.usuario;
import org.springframework.security.access.prepost.PreAuthorize;
@Service public class ResumirAvaliacoesUseCase{private final EquipeRepositorio equipe;public ResumirAvaliacoesUseCase(EquipeRepositorio e){equipe=e;}@PreAuthorize("hasAnyRole('GESTOR','ADMINISTRADOR')")@Transactional(readOnly=true)public ResumoAvaliacoes executar(){return equipe.resumirAvaliacoes();}}
