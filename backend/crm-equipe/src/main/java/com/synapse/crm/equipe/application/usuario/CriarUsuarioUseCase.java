package com.synapse.crm.equipe.application.usuario;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.equipe.application.autenticacao.CodificadorDeSenha;
import com.synapse.crm.equipe.application.autenticacao.PoliticaDeSenha;
import com.synapse.crm.equipe.domain.usuario.PapelGerenciavel;
import com.synapse.crm.equipe.domain.usuario.SenhaInvalidaException;
import com.synapse.crm.equipe.domain.usuario.Usuario;
import com.synapse.crm.sharedkernel.auditoria.Auditable;
/**
 * E31b: usa a mesma PoliticaDeSenha de AlterarSenhaUseCase/DefinirSenhaProvisoriaUseCase — antes o minimo era fixo (@Size(min=8)) e nao respeitava SYNAPSE_SENHA_TAMANHO_MINIMO configurado maior.
 *
 * <p>Gestao (docs/47): SUBGESTOR so cria, e so ATENDENTE, com {@code equipe.criar} concedida por um
 * superior. O login novo nasce fora do rodizio da IA (disponibilidade OFF), decidido no repositorio.
 */
@Service public class CriarUsuarioUseCase{private final EquipeRepositorio equipe;private final CodificadorDeSenha senhas;private final PoliticaDeSenha politica;private final AlcadaSobreIntegrantes alcada;public CriarUsuarioUseCase(EquipeRepositorio e,CodificadorDeSenha s,PoliticaDeSenha p,AlcadaSobreIntegrantes a){equipe=e;senhas=s;politica=p;alcada=a;}@PreAuthorize("hasAnyRole('SUBGESTOR','GESTOR','ADMINISTRADOR') and @capacidades.permite('equipe.criar')")@Transactional @Auditable(acao="CRIAR_USUARIO",entidadeTipo="USUARIO") public Usuario executar(String nome,String email,String senha,PapelGerenciavel papel){alcada.exigirPapelNovo(papel);if(senha==null||senha.length()<politica.tamanhoMinimo()){throw SenhaInvalidaException.foraDaPolitica(politica.tamanhoMinimo());}return equipe.criar(nome.trim(),email.trim().toLowerCase(),senhas.codificar(senha),papel);}}
