package com.synapse.crm.equipe.application.usuario;
import java.util.Optional;
@Service public class ObterMinhaPresencaUseCase{private final EquipeRepositorio equipe;private final UsuarioContext usuario;public ObterMinhaPresencaUseCase(EquipeRepositorio e,UsuarioContext u){equipe=e;usuario=u;}@PreAuthorize("isAuthenticated()")@Transactional(readOnly=true)public Optional<StatusPresenca> executar(){return equipe.obterPresenca(usuario.atual().id());}}
