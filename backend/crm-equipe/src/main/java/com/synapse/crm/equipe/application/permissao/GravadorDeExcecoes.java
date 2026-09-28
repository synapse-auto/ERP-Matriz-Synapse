package com.synapse.crm.equipe.application.permissao;

import java.time.Clock;
import java.util.Set;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import com.synapse.crm.equipe.domain.permissao.ConfiguracaoDePermissoes;
import com.synapse.crm.equipe.domain.permissao.PermissaoInvalidaException;
import com.synapse.crm.equipe.domain.permissao.PoliticaDeConcessao;
import com.synapse.crm.equipe.domain.permissao.PoliticaDeCopia;
import com.synapse.crm.equipe.domain.permissao.PoliticaDePermissoes;
import com.synapse.crm.equipe.domain.permissao.Violacao;
import com.synapse.crm.sharedkernel.permissao.AcessoDeUsuariosAlterado;

/**
 * Caminho unico de gravacao de excecoes (salvar, copiar, restaurar). Sem autorizacao propria: quem
 * chama e um caso de uso com {@code @PreAuthorize} e transacao. Ordem fixa: trava o alvo, confere
 * alcada, valida o payload inteiro, confere a concessao item a item, grava com revisao, historico e
 * evento.
 */
@Component
class GravadorDeExcecoes {

    private final PermissaoRepositorio repositorio;
    private final ResolvedorDePermissoesEfetivas resolvedor;
    private final AtorDePermissoes atores;
    private final ApplicationEventPublisher eventos;
    private final Clock relogio;

    GravadorDeExcecoes(PermissaoRepositorio repositorio, ResolvedorDePermissoesEfetivas resolvedor,
            AtorDePermissoes atores, ApplicationEventPublisher eventos, Clock relogio) {
        this.repositorio = repositorio;
        this.resolvedor = resolvedor;
        this.atores = atores;
        this.eventos = eventos;
        this.relogio = relogio;
    }

    Visoes.Gravacao gravar(UUID usuarioId, long revisaoEsperada, ConfiguracaoDePermissoes novas,
            UUID copiadoDe, PermissaoRepositorio.Operacao operacao) {
        PoliticaDeConcessao.Ator ator = atores.atual();
        PermissaoRepositorio.AlvoTravado alvo = repositorio.travarAlvo(usuarioId)
                .orElseThrow(AlvoDePermissaoNaoEncontradoException::new);
        PoliticaDeConcessao.exigirAlcadaSobre(ator, alvo.id(), alvo.papel());

        Set<String> flags = resolvedor.flagsHabilitadas();
        PermissaoRepositorio.Armazenado perfil = repositorio.perfil(alvo.papel());
        PoliticaDePermissoes.validarExcecoes(alvo.papel(), perfil.configuracao(), novas, flags);

        PermissaoRepositorio.Armazenado atuais = repositorio.excecoesDe(usuarioId);
        PoliticaDeConcessao.exigirConcessao(ator, alvo.id(), alvo.papel(),
                atuais.configuracao().somente(m -> PoliticaDePermissoes.disponivel(m, flags)), novas);
        if (copiadoDe != null) {
            exigirOrigemDeCopia(ator, alvo, copiadoDe);
        }

        long revisao = repositorio.substituirExcecoes(
                usuarioId, revisaoEsperada, novas, ModulosDisponiveis.com(flags), ator.id());
        long global = repositorio.incrementarRevisaoGlobal();
        ConfiguracaoDePermissoes depois = repositorio.excecoesDe(usuarioId).configuracao();
        repositorio.registrarHistorico(new PermissaoRepositorio.RegistroDeHistorico(
                PermissaoRepositorio.Escopo.USUARIO, alvo.papel(), usuarioId, operacao,
                atuais.revisao(), revisao, atuais.configuracao(), depois, null, copiadoDe, ator.id(),
                relogio.instant()));
        eventos.publishEvent(new AcessoDeUsuariosAlterado(Set.of(usuarioId), false, global));
        return new Visoes.Gravacao(alvo.papel(), usuarioId, operacao, atuais.revisao(), revisao, depois.quantidade());
    }

    void exigirOrigemDeCopia(PoliticaDeConcessao.Ator ator, PermissaoRepositorio.AlvoTravado alvo, UUID origemId) {
        ResolvedorDePermissoesEfetivas.Resolvido origem = resolvedor.de(origemId)
                .orElseThrow(() -> new PermissaoInvalidaException(new Violacao("origem", Violacao.Codigo.ORIGEM_INVALIDA)));
        PoliticaDeCopia.exigirOrigemValida(origem.papel());
        if (origemId.equals(alvo.id()) || !PoliticaDeConcessao.origemDeCopiaNaAlcada(ator, origem.papel())) {
            throw new PermissaoInvalidaException(new Violacao("origem", Violacao.Codigo.ORIGEM_INVALIDA));
        }
    }
}
