package com.synapse.crm.core.application.mensagemrapida;
import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;

import com.synapse.crm.core.domain.mensagemrapida.MensagemRapida;
@Service public class ListarMensagensRapidasUseCase{private final MensagemRapidaRepositorio repo;private final UsuarioContext usuario;
 public ListarMensagensRapidasUseCase(MensagemRapidaRepositorio repo,UsuarioContext usuario){this.repo=repo;this.usuario=usuario;}
 @PreAuthorize("hasAnyRole('ATENDENTE','SUBGESTOR','GESTOR','ADMINISTRADOR')") @Transactional(readOnly=true)
 public List<MensagemRapida> executar(boolean somenteMinhas){var u=usuario.atual();return repo.listar(new EscopoMensagensRapidas(u.id(),!somenteMinhas&&u.papel().enxergaTodosOsLeads()));}}
