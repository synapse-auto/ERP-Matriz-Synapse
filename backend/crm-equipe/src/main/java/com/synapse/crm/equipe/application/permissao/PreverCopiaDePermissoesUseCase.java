package com.synapse.crm.equipe.application.permissao;

import java.util.Set;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.equipe.domain.permissao.ConfiguracaoDePermissoes;
import com.synapse.crm.equipe.domain.permissao.PermissaoInvalidaException;
import com.synapse.crm.equipe.domain.permissao.PermissoesEfetivas;
import com.synapse.crm.equipe.domain.permissao.PoliticaDeConcessao;
import com.synapse.crm.equipe.domain.permissao.PoliticaDeCopia;
import com.synapse.crm.equipe.domain.permissao.PoliticaDePermissoes;
import com.synapse.crm.equipe.domain.permissao.Violacao;
import com.synapse.crm.sharedkernel.identidade.PapelUsuario;

/**
 * Previa de copia validada no servidor: origem e destino conferidos, resultado recortado pelo teto
 * do destino e pela alcada de quem copia, com o que muda e o que foi impedido. Nao grava nada — a
 * confirmacao passa pelo salvamento normal, com revisao e historico.
 */
@Service
public class PreverCopiaDePermissoesUseCase {

    private final PermissaoRepositorio repositorio;
    private final ResolvedorDePermissoesEfetivas resolvedor;
    private final AtorDePermissoes atores;
    private final GravadorDeExcecoes gravador;

    public PreverCopiaDePermissoesUseCase(PermissaoRepositorio repositorio, ResolvedorDePermissoesEfetivas resolvedor,
            AtorDePermissoes atores, GravadorDeExcecoes gravador) {
        this.repositorio = repositorio;
        this.resolvedor = resolvedor;
        this.atores = atores;
        this.gravador = gravador;
    }

    @PreAuthorize(AutorizacaoDeGestao.EDITAR_PERFIS)
    @Transactional(readOnly = true)
    public Visoes.PreviaDeCopia paraPerfil(PapelUsuario destino, PapelUsuario origem) {
        PoliticaDeConcessao.Ator ator = atores.atual();
        PoliticaDeConcessao.exigirAlcadaSobrePerfil(ator, destino);
        PoliticaDeCopia.exigirOrigemValida(origem);
        if (origem == destino || PoliticaDePermissoes.perfilFixo(destino)
                || !PoliticaDeConcessao.origemDeCopiaNaAlcada(ator, origem)) {
            throw new PermissaoInvalidaException(new Violacao("origem", Violacao.Codigo.ORIGEM_INVALIDA));
        }
        Set<String> flags = resolvedor.flagsHabilitadas();
        PermissoesEfetivas daOrigem = PoliticaDePermissoes.calcular(
                origem, repositorio.perfil(origem).configuracao(), ConfiguracaoDePermissoes.vazia(), flags);
        ConfiguracaoDePermissoes perfilDestino = repositorio.perfil(destino).configuracao();
        PoliticaDeCopia.Resultado resultado = PoliticaDeCopia.paraPerfil(daOrigem, destino, flags);
        PermissoesEfetivas antes = PoliticaDePermissoes.calcular(destino, perfilDestino, ConfiguracaoDePermissoes.vazia(), flags);
        PermissoesEfetivas depois = PoliticaDePermissoes.calcular(
                destino, resultado.configuracao(), ConfiguracaoDePermissoes.vazia(), flags);
        return previa(resultado, antes, depois, flags);
    }

    @PreAuthorize(AutorizacaoDeGestao.EDITAR_EXCECOES)
    @Transactional(readOnly = true)
    public Visoes.PreviaDeCopia paraUsuario(UUID destinoId, UUID origemId) {
        PoliticaDeConcessao.Ator ator = atores.atual();
        PermissaoRepositorio.AlvoTravado destino = repositorio.alvo(destinoId)
                .orElseThrow(AlvoDePermissaoNaoEncontradoException::new);
        PoliticaDeConcessao.exigirAlcadaSobre(ator, destino.id(), destino.papel());
        Set<String> flags = resolvedor.flagsHabilitadas();
        FuncionalidadeDeExcecoes.exigirHabilitada(flags);
        gravador.exigirOrigemDeCopia(ator, destino, origemId);

        PermissoesEfetivas daOrigem = resolvedor.de(origemId).orElseThrow().efetivas();
        ConfiguracaoDePermissoes perfil = repositorio.perfil(destino.papel()).configuracao();
        ConfiguracaoDePermissoes atuais = repositorio.excecoesDe(destinoId).configuracao()
                .somente(m -> PoliticaDePermissoes.disponivel(m, flags));
        PoliticaDeCopia.Resultado resultado = PoliticaDeCopia.paraUsuario(
                daOrigem, destino.papel(), perfil, atuais, ator, flags);
        PermissoesEfetivas antes = PoliticaDePermissoes.calcular(destino.papel(), perfil, atuais, flags);
        PermissoesEfetivas depois = PoliticaDePermissoes.calcular(destino.papel(), perfil, resultado.configuracao(), flags);
        return previa(resultado, antes, depois, flags);
    }

    private static Visoes.PreviaDeCopia previa(PoliticaDeCopia.Resultado resultado, PermissoesEfetivas antes,
            PermissoesEfetivas depois, Set<String> flags) {
        return new Visoes.PreviaDeCopia(
                MontadorDeVisoes.niveisComoTexto(resultado.configuracao()),
                MontadorDeVisoes.acoesComoTexto(resultado.configuracao()),
                MontadorDeVisoes.alteracoes(antes, depois, flags),
                resultado.impedidos());
    }
}
