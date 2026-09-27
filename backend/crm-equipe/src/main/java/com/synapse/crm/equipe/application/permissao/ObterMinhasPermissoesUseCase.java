package com.synapse.crm.equipe.application.permissao;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import com.synapse.crm.equipe.domain.permissao.Capacidade;
import com.synapse.crm.equipe.domain.permissao.ConfiguracaoDePermissoes;
import com.synapse.crm.equipe.domain.permissao.PoliticaDeConcessao;
import com.synapse.crm.equipe.domain.permissao.PoliticaDePermissoes;
import com.synapse.crm.sharedkernel.identidade.PapelUsuario;

/**
 * Efetivas do autenticado, em lote — a tela consulta uma vez e decide todos os botoes a partir
 * daqui, sem uma chamada por botao. A decisao de verdade continua no backend, em cada caso de uso.
 */
@Service
public class ObterMinhasPermissoesUseCase {

    private final AtorDePermissoes atores;
    private final ResolvedorDePermissoesEfetivas resolvedor;
    private final PermissaoRepositorio repositorio;

    public ObterMinhasPermissoesUseCase(AtorDePermissoes atores, ResolvedorDePermissoesEfetivas resolvedor,
            PermissaoRepositorio repositorio) {
        this.atores = atores;
        this.resolvedor = resolvedor;
        this.repositorio = repositorio;
    }

    @PreAuthorize("isAuthenticated()")
    public Visoes.Minhas executar() {
        PoliticaDeConcessao.Ator ator = atores.atual();
        var flags = resolvedor.flagsHabilitadas();
        boolean acessaGestao = PoliticaDePermissoes.perfilFixo(ator.papel())
                || (ator.papel() == PapelUsuario.SUBGESTOR && ator.efetivas().permite(Capacidade.EQUIPE_VER));
        boolean editaExcecoes = PoliticaDePermissoes.perfilFixo(ator.papel())
                || ator.efetivas().permite(Capacidade.EQUIPE_EXCECOES_ATENDENTES);
        return new Visoes.Minhas(ator.id(), ator.papel(), repositorio.revisaoGlobal(),
                MontadorDeVisoes.capacidades(ator.papel(), ConfiguracaoDePermissoes.vazia(),
                        ConfiguracaoDePermissoes.vazia(), ator.efetivas(), flags, null, false),
                acessaGestao, ListarPerfisDePermissaoUseCase.editaPerfis(ator), editaExcecoes);
    }
}
