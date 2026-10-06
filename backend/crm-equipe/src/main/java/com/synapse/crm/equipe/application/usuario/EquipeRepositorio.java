package com.synapse.crm.equipe.application.usuario;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.synapse.crm.equipe.domain.avaliacao.ResumoAvaliacoes;
import com.synapse.crm.equipe.domain.usuario.MudancaDePresenca;
import com.synapse.crm.equipe.domain.usuario.OrigemDaPresenca;
import com.synapse.crm.equipe.domain.usuario.PapelGerenciavel;
import com.synapse.crm.equipe.domain.usuario.StatusPresenca;
import com.synapse.crm.equipe.domain.usuario.Usuario;
public interface EquipeRepositorio{List<Usuario> listar(FiltroEquipe filtro);Optional<Usuario> porId(UUID id);
    /** Mesmo que {@link #porId}, com FOR UPDATE: serializa mudanca de papel, desativacao e gravacao de excecoes. */
    Optional<Usuario> travarParaAlteracao(UUID id);Usuario criar(String nome,String email,String senhaHash,PapelGerenciavel papel);Optional<Usuario> atualizar(UUID id,String nome,String email,PapelGerenciavel papel);Optional<Usuario> atualizarNomeDoProprio(UUID id,String nome);Optional<Usuario> atualizarMeuPerfil(UUID id,String nome,String email,String telefone,String cargo);boolean atualizarFoto(UUID id,String fotoReferencia);boolean desativar(UUID id);Optional<StatusPresenca> obterPresenca(UUID id);Optional<Boolean> atualizarDisponibilidadeParaIa(UUID id, boolean disponivel);ResumoAvaliacoes resumirAvaliacoes();
    /** Grava um novo hash e limpa {@code senha_alterada_em} (E29): o alvo volta ao primeiro acesso. */
    boolean definirSenhaProvisoria(UUID id, String novoHash);

    /**
     * Muda a presenca de um usuario ativo e, se o estado de fato mudou, grava a linha de historico na mesma
     * transacao. Vazio = usuario inexistente ou inativo. Mesmo estado = devolve a mudanca sem gravar historico.
     */
    Optional<MudancaDePresenca> registrarPresenca(UUID id, StatusPresenca novo, OrigemDaPresenca origem, String motivo);

    /**
     * Como {@link #registrarPresenca}, mas so grava se o estado atual for um dos {@code anterioresPermitidos}; fora
     * deles devolve a "mudanca" sem alteracao (mesmo estado nos dois lados). A checagem e a gravacao acontecem sob o
     * mesmo {@code FOR UPDATE}, entao um clique concorrente do usuario nunca e sobrescrito por uma decisao baseada
     * em leitura antiga.
     */
    Optional<MudancaDePresenca> registrarPresencaSe(
            UUID id, StatusPresenca novo, OrigemDaPresenca origem, String motivo, Set<StatusPresenca> anterioresPermitidos);

    /** Ids dos usuarios ativos com presenca ONLINE ou AUSENTE: quem a presenca automatica precisa conferir. */
    List<UUID> idsComPresencaAtiva();}
