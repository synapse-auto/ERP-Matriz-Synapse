package com.synapse.crm.equipe.application.permissao;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.equipe.application.usuario.EquipeRepositorio;
import com.synapse.crm.equipe.application.usuario.FiltroEquipe;
import com.synapse.crm.equipe.domain.permissao.ConcessaoNegadaException;
import com.synapse.crm.equipe.domain.permissao.ConfiguracaoDePermissoes;
import com.synapse.crm.equipe.domain.permissao.PermissoesEfetivas;
import com.synapse.crm.equipe.domain.permissao.PoliticaDeConcessao;
import com.synapse.crm.equipe.domain.permissao.PoliticaDePermissoes;
import com.synapse.crm.equipe.domain.usuario.Usuario;
import com.synapse.crm.sharedkernel.identidade.PapelUsuario;

/**
 * Integrantes e suas excecoes. GESTOR/ADMINISTRADOR veem todos (menos ADMINISTRADOR, como na grade
 * de Equipe); SUBGESTOR ve os ATENDENTES e a si mesmo — as proprias permissoes em modo leitura.
 */
@Service
public class ConsultarPermissoesDeUsuariosUseCase {

    private final PermissaoRepositorio repositorio;
    private final EquipeRepositorio equipe;
    private final ResolvedorDePermissoesEfetivas resolvedor;
    private final AtorDePermissoes atores;

    public ConsultarPermissoesDeUsuariosUseCase(PermissaoRepositorio repositorio, EquipeRepositorio equipe,
            ResolvedorDePermissoesEfetivas resolvedor, AtorDePermissoes atores) {
        this.repositorio = repositorio;
        this.equipe = equipe;
        this.resolvedor = resolvedor;
        this.atores = atores;
    }

    @PreAuthorize(AutorizacaoDeGestao.LER)
    @Transactional(readOnly = true)
    public List<Visoes.ResumoDeUsuario> listar() {
        PoliticaDeConcessao.Ator ator = atores.atual();
        Set<String> flags = resolvedor.flagsHabilitadas();
        Map<UUID, ConfiguracaoDePermissoes> excecoes = repositorio.todasAsExcecoes();
        return equipe.listar(new FiltroEquipe(true)).stream()
                .filter(u -> u.papel() != PapelUsuario.ADMINISTRADOR)
                .filter(u -> enxerga(ator, u))
                .sorted(Comparator.comparing(Usuario::papel).reversed()
                        .thenComparing(Usuario::ativo, Comparator.reverseOrder())
                        .thenComparing(Usuario::nome))
                .map(u -> resumo(u, contarValidas(excecoes.get(u.id()), flags), ator))
                .toList();
    }

    @PreAuthorize(AutorizacaoDeGestao.LER)
    @Transactional(readOnly = true)
    public Visoes.Usuario obter(UUID usuarioId) {
        PoliticaDeConcessao.Ator ator = atores.atual();
        Usuario usuario = equipe.porId(usuarioId)
                .filter(u -> u.papel() != PapelUsuario.ADMINISTRADOR)
                .orElseThrow(AlvoDePermissaoNaoEncontradoException::new);
        if (!enxerga(ator, usuario)) {
            throw new ConcessaoNegadaException(ConcessaoNegadaException.Codigo.ALVO_FORA_DA_ALCADA);
        }
        Set<String> flags = resolvedor.flagsHabilitadas();
        boolean fixo = PoliticaDePermissoes.perfilFixo(usuario.papel());
        PermissaoRepositorio.Armazenado perfil = fixo
                ? new PermissaoRepositorio.Armazenado(0, ConfiguracaoDePermissoes.vazia())
                : repositorio.perfil(usuario.papel());
        PermissaoRepositorio.Armazenado excecoes = repositorio.excecoesDe(usuarioId);
        PermissoesEfetivas efetivas = PoliticaDePermissoes.calcular(
                usuario.papel(), perfil.configuracao(), excecoes.configuracao(), flags);
        boolean editavel = PoliticaDeConcessao.podeEditarExcecoesDe(ator, usuario.id(), usuario.papel());
        ConfiguracaoDePermissoes excecoesVisiveis = excecoes.configuracao()
                .somente(m -> PoliticaDePermissoes.disponivel(m, flags));
        return new Visoes.Usuario(
                resumo(usuario, excecoesVisiveis.quantidade(), ator),
                excecoes.revisao(), perfil.revisao(), fixo,
                MontadorDeVisoes.modulos(usuario.papel(), perfil.configuracao(), excecoes.configuracao(), efetivas, flags),
                MontadorDeVisoes.capacidades(usuario.papel(), perfil.configuracao(), excecoes.configuracao(),
                        efetivas, flags, ator, editavel));
    }

    /** Badge: excecoes persistidas de modulos disponiveis — flag desligada nao conta nem some. */
    private static int contarValidas(ConfiguracaoDePermissoes excecoes, Set<String> flags) {
        return excecoes == null ? 0 : excecoes.somente(m -> PoliticaDePermissoes.disponivel(m, flags)).quantidade();
    }

    private static boolean enxerga(PoliticaDeConcessao.Ator ator, Usuario usuario) {
        if (PoliticaDePermissoes.perfilFixo(ator.papel())) {
            return true;
        }
        return usuario.id().equals(ator.id()) || usuario.papel() == PapelUsuario.ATENDENTE;
    }

    private static Visoes.ResumoDeUsuario resumo(Usuario u, int excecoes, PoliticaDeConcessao.Ator ator) {
        return new Visoes.ResumoDeUsuario(u.id(), u.nome(), u.email(), u.papel(), u.ativo(), u.fotoReferencia(),
                excecoes, PoliticaDeConcessao.podeEditarExcecoesDe(ator, u.id(), u.papel()));
    }
}
