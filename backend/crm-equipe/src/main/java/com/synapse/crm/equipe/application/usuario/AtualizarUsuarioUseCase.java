package com.synapse.crm.equipe.application.usuario;
import java.util.Optional;
@Service public class AtualizarUsuarioUseCase{private final EquipeRepositorio equipe;public AtualizarUsuarioUseCase(EquipeRepositorio e){equipe=e;}@PreAuthorize("hasAnyRole('GESTOR','ADMINISTRADOR')")@Transactional public Optional<Usuario> executar(UUID id,String nome,String email,PapelGerenciavel papel){return equipe.atualizar(id,nome.trim(),email.trim().toLowerCase(),papel);}}
