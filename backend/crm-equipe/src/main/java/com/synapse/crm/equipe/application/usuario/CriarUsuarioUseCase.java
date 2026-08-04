package com.synapse.crm.equipe.application.usuario;
import org.springframework.security.access.prepost.PreAuthorize;
@Service public class CriarUsuarioUseCase{private final EquipeRepositorio equipe;private final CodificadorDeSenha senhas;public CriarUsuarioUseCase(EquipeRepositorio e,CodificadorDeSenha s){equipe=e;senhas=s;}@PreAuthorize("hasAnyRole('GESTOR','ADMINISTRADOR')")@Transactional public Usuario executar(String nome,String email,String senha,PapelGerenciavel papel){return equipe.criar(nome.trim(),email.trim().toLowerCase(),senhas.codificar(senha),papel);}}
