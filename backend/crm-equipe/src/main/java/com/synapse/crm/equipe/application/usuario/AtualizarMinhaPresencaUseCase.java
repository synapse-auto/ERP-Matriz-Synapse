package com.synapse.crm.equipe.application.usuario;
import java.util.Optional;
@Service public class AtualizarMinhaPresencaUseCase{private final EquipeRepositorio equipe;private final UsuarioContext usuario;public AtualizarMinhaPresencaUseCase(EquipeRepositorio e,UsuarioContext u){equipe=e;usuario=u;}@PreAuthorize("isAuthenticated()")@Transactional public Optional<StatusPresenca> executar(StatusPresenca status){return equipe.atualizarPresenca(usuario.atual().id(),status);}}
