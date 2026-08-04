package com.synapse.crm.core.application.mensagemrapida;
import java.util.Optional;import java.util.UUID;import org.springframework.security.access.prepost.PreAuthorize;import org.springframework.stereotype.Service;import org.springframework.transaction.annotation.Transactional;
import com.synapse.crm.core.domain.mensagemrapida.MensagemRapida;import com.synapse.crm.sharedkernel.identidade.UsuarioContext;
@Service public class AtualizarMensagemRapidaUseCase{private final MensagemRapidaRepositorio repo;private final UsuarioContext usuario;
 public AtualizarMensagemRapidaUseCase(MensagemRapidaRepositorio repo,UsuarioContext usuario){this.repo=repo;this.usuario=usuario;}
 @PreAuthorize("hasAnyRole('ATENDENTE','SUBGESTOR','GESTOR','ADMINISTRADOR')") @Transactional public Optional<MensagemRapida> executar(UUID id,String chave,String conteudo){var u=usuario.atual();return repo.atualizar(id,new EscopoMensagensRapidas(u.id(),u.papel().enxergaTodosOsLeads()),chave.trim().toLowerCase(),conteudo.trim());}}
