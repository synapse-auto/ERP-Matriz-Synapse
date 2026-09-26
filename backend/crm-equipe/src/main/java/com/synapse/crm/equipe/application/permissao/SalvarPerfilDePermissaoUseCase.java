package com.synapse.crm.equipe.application.permissao;

import java.time.Clock;
import java.util.Map;
import java.util.Set;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.equipe.domain.permissao.ConfiguracaoDePermissoes;
import com.synapse.crm.equipe.domain.permissao.PermissaoInvalidaException;
import com.synapse.crm.equipe.domain.permissao.PoliticaDeConcessao;
import com.synapse.crm.equipe.domain.permissao.PoliticaDeCopia;
import com.synapse.crm.equipe.domain.permissao.PoliticaDePermissoes;
import com.synapse.crm.equipe.domain.permissao.Violacao;
import com.synapse.crm.sharedkernel.auditoria.Auditable;
import com.synapse.crm.sharedkernel.identidade.PapelUsuario;
import com.synapse.crm.sharedkernel.permissao.AcessoDeUsuariosAlterado;

/**
 * Substitui o perfil inteiro (niveis e interruptores dos modulos disponiveis) numa transacao.
 *
 * <p>Tudo e validado antes de qualquer escrita; a escrita confere a revisao lida e grava o
 * historico junto. Herdeiros passam a valer o perfil novo imediatamente; as excecoes explicitas
 * deles ficam intactas.
 */
@Service
public class SalvarPerfilDePermissaoUseCase {

    private final PermissaoRepositorio repositorio;
    private final ResolvedorDePermissoesEfetivas resolvedor;
    private final AtorDePermissoes atores;
    private final ApplicationEventPublisher eventos;
    private final Clock relogio;

    public SalvarPerfilDePermissaoUseCase(PermissaoRepositorio repositorio, ResolvedorDePermissoesEfetivas resolvedor,
            AtorDePermissoes atores, ApplicationEventPublisher eventos, Clock relogio) {
        this.repositorio = repositorio;
        this.resolvedor = resolvedor;
        this.atores = atores;
        this.eventos = eventos;
        this.relogio = relogio;
    }

    @PreAuthorize(AutorizacaoDeGestao.EDITAR_PERFIS)
    @Transactional
    @Auditable(acao = "SALVAR_PERFIL_DE_PERMISSAO", entidadeTipo = "PERMISSAO")
    public Visoes.Gravacao executar(PapelUsuario papel, long revisaoEsperada, Map<String, String> niveis,
            Map<String, Boolean> acoes, PapelUsuario copiadoDe) {
        PoliticaDeConcessao.Ator ator = atores.atual();
        PoliticaDeConcessao.exigirEdicaoDePerfil(ator);
        Set<String> flags = resolvedor.flagsHabilitadas();
        ConfiguracaoDePermissoes novo = ConfiguracaoDePermissoes.interpretar(niveis, acoes);
        PoliticaDePermissoes.validarPerfil(papel, novo, flags);
        if (copiadoDe != null) {
            PoliticaDeCopia.exigirOrigemValida(copiadoDe);
            if (copiadoDe == papel) {
                throw new PermissaoInvalidaException(new Violacao("origem", Violacao.Codigo.ORIGEM_INVALIDA));
            }
        }

        PermissaoRepositorio.Armazenado antes = repositorio.perfil(papel);
        long revisao = repositorio.substituirPerfil(
                papel, revisaoEsperada, novo, ModulosDisponiveis.com(flags), ator.id());
        long global = repositorio.incrementarRevisaoGlobal();
        PermissaoRepositorio.Operacao operacao = copiadoDe == null
                ? PermissaoRepositorio.Operacao.SALVAR : PermissaoRepositorio.Operacao.COPIAR;
        repositorio.registrarHistorico(new PermissaoRepositorio.RegistroDeHistorico(
                PermissaoRepositorio.Escopo.PERFIL, papel, null, operacao, antes.revisao(), revisao,
                antes.configuracao(), repositorio.perfil(papel).configuracao(), copiadoDe, null, ator.id(),
                relogio.instant()));
        eventos.publishEvent(new AcessoDeUsuariosAlterado(repositorio.usuariosAtivosComPapel(papel), false, global));
        return new Visoes.Gravacao(papel, null, operacao, antes.revisao(), revisao, novo.quantidade());
    }
}
