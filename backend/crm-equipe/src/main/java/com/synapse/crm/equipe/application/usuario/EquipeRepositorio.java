package com.synapse.crm.equipe.application.usuario;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.synapse.crm.equipe.domain.avaliacao.ResumoAvaliacoes;
import com.synapse.crm.equipe.domain.usuario.PapelGerenciavel;
import com.synapse.crm.equipe.domain.usuario.StatusPresenca;
import com.synapse.crm.equipe.domain.usuario.Usuario;
public interface EquipeRepositorio{List<Usuario> listar(FiltroEquipe filtro);Optional<Usuario> porId(UUID id);Usuario criar(String nome,String email,String senhaHash,PapelGerenciavel papel);Optional<Usuario> atualizar(UUID id,String nome,String email,PapelGerenciavel papel);Optional<Usuario> atualizarNomeDoProprio(UUID id,String nome);boolean desativar(UUID id);Optional<StatusPresenca> obterPresenca(UUID id);Optional<StatusPresenca> atualizarPresenca(UUID id,StatusPresenca status);Optional<Boolean> atualizarDisponibilidadeParaIa(UUID id, boolean disponivel);ResumoAvaliacoes resumirAvaliacoes();
    /** Grava um novo hash e limpa {@code senha_alterada_em} (E29): o alvo volta ao primeiro acesso. */
    boolean definirSenhaProvisoria(UUID id, String novoHash);}
