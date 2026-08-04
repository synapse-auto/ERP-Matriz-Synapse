package com.synapse.crm.core.application.mensagemrapida;
import java.util.UUID;
@Service public class RemoverMensagemRapidaUseCase{private final MensagemRapidaRepositorio repo;private final UsuarioContext usuario;public RemoverMensagemRapidaUseCase(MensagemRapidaRepositorio repo,UsuarioContext usuario){this.repo=repo;this.usuario=usuario;}
 @PreAuthorize("hasAnyRole('ATENDENTE','SUBGESTOR','GESTOR','ADMINISTRADOR')") @Transactional public boolean executar(UUID id){var u=usuario.atual();return repo.remover(id,new EscopoMensagensRapidas(u.id(),u.papel().enxergaTodosOsLeads()));}}
