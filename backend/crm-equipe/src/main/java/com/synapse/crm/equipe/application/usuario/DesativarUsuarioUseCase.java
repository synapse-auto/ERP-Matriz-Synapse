package com.synapse.crm.equipe.application.usuario;
import java.util.UUID;
@Service public class DesativarUsuarioUseCase{private final EquipeRepositorio equipe;public DesativarUsuarioUseCase(EquipeRepositorio e){equipe=e;}@PreAuthorize("hasAnyRole('GESTOR','ADMINISTRADOR')")@Transactional public boolean executar(UUID id){return equipe.desativar(id);}}
