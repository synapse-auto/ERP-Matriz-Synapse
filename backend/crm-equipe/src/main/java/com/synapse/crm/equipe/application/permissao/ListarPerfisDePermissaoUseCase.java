package com.synapse.crm.equipe.application.permissao;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.equipe.application.usuario.EquipeRepositorio;
import com.synapse.crm.equipe.application.usuario.FiltroEquipe;
import com.synapse.crm.equipe.domain.permissao.ConfiguracaoDePermissoes;
import com.synapse.crm.equipe.domain.permissao.PermissoesEfetivas;
import com.synapse.crm.equipe.domain.permissao.PoliticaDeConcessao;
import com.synapse.crm.equipe.domain.permissao.PoliticaDePermissoes;
import com.synapse.crm.equipe.domain.usuario.Usuario;
import com.synapse.crm.sharedkernel.identidade.PapelUsuario;

/**
 * Perfis exibidos em Gestao: GESTOR (fixo), SUBGESTOR e ATENDENTE, com contagem real de usuarios
 * ativos e "N de M" calculado do catalogo — nada de numero fixo.
 *
 * <p>ADMINISTRADOR fica fora de proposito, como ja fica da grade de Equipe: e o acesso tecnico da
 * Synapse, fixo, e nao faz parte da operacao do cliente.
 */
@Service
public class ListarPerfisDePermissaoUseCase {

    static final List<PapelUsuario> PAPEIS_EXIBIDOS = List.of(PapelUsuario.GESTOR, PapelUsuario.SUBGESTOR, PapelUsuario.ATENDENTE);

    private final PermissaoRepositorio repositorio;
    private final EquipeRepositorio equipe;
    private final ResolvedorDePermissoesEfetivas resolvedor;
    private final AtorDePermissoes atores;

    public ListarPerfisDePermissaoUseCase(PermissaoRepositorio repositorio, EquipeRepositorio equipe,
            ResolvedorDePermissoesEfetivas resolvedor, AtorDePermissoes atores) {
        this.repositorio = repositorio;
        this.equipe = equipe;
        this.resolvedor = resolvedor;
        this.atores = atores;
    }

    @PreAuthorize(AutorizacaoDeGestao.LER)
    @Transactional(readOnly = true)
    public List<Visoes.Perfil> executar() {
        PoliticaDeConcessao.Ator ator = atores.atual();
        Set<String> flags = resolvedor.flagsHabilitadas();
        Map<PapelUsuario, Long> ativosPorPapel = equipe.listar(new FiltroEquipe(false)).stream()
                .collect(Collectors.groupingBy(Usuario::papel, Collectors.counting()));
        List<Visoes.Perfil> perfis = new ArrayList<>();
        for (PapelUsuario papel : PAPEIS_EXIBIDOS) {
            perfis.add(montar(papel, ator, flags, ativosPorPapel.getOrDefault(papel, 0L).intValue()));
        }
        return perfis;
    }

    Visoes.Perfil montar(PapelUsuario papel, PoliticaDeConcessao.Ator ator, Set<String> flags, int usuarios) {
        boolean fixo = PoliticaDePermissoes.perfilFixo(papel);
        PermissaoRepositorio.Armazenado armazenado = fixo
                ? new PermissaoRepositorio.Armazenado(0, ConfiguracaoDePermissoes.vazia())
                : repositorio.perfil(papel);
        PermissoesEfetivas efetivas = PoliticaDePermissoes.calcular(
                papel, armazenado.configuracao(), ConfiguracaoDePermissoes.vazia(), flags);
        boolean editavel = PoliticaDeConcessao.podeEditarPerfil(ator, papel);
        return new Visoes.Perfil(papel, fixo, armazenado.revisao(), usuarios,
                efetivas.totalPermitido(), efetivas.totalConfiguravel(), editavel,
                MontadorDeVisoes.modulos(papel, armazenado.configuracao(), ConfiguracaoDePermissoes.vazia(), efetivas, flags),
                MontadorDeVisoes.capacidades(papel, armazenado.configuracao(), ConfiguracaoDePermissoes.vazia(),
                        efetivas, flags, ator, editavel));
    }

    /** Edita ao menos um dos perfis exibidos (SUBGESTOR delegado: so o de ATENDENTE). */
    static boolean editaPerfis(PoliticaDeConcessao.Ator ator) {
        return PAPEIS_EXIBIDOS.stream().anyMatch(papel -> PoliticaDeConcessao.podeEditarPerfil(ator, papel));
    }
}
