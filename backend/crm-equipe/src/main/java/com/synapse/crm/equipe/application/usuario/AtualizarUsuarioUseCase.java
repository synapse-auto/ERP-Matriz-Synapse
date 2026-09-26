package com.synapse.crm.equipe.application.usuario;
import java.time.Clock;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.equipe.application.permissao.PermissaoRepositorio;
import com.synapse.crm.equipe.domain.permissao.ConcessaoNegadaException;
import com.synapse.crm.equipe.domain.permissao.ConfiguracaoDePermissoes;
import com.synapse.crm.equipe.domain.usuario.PapelGerenciavel;
import com.synapse.crm.equipe.domain.usuario.Usuario;
import com.synapse.crm.sharedkernel.auditoria.Auditable;
import com.synapse.crm.sharedkernel.permissao.AcessoDeUsuariosAlterado;
import com.synapse.crm.sharedkernel.permissao.VerificadorDeCapacidades;

/**
 * Edita nome, e-mail e papel de um integrante.
 *
 * <p>Editar dados ({@code equipe.editar}) e mudar papel ({@code equipe.alterar_papel}, so GESTOR e
 * ADMINISTRADOR) sao capacidades distintas. Mudanca de papel descarta todas as excecoes do alvo —
 * nenhum privilegio do papel anterior sobrevive — grava o historico e invalida as sessoes dele: o
 * JWT com o papel antigo passa a receber 401 e o tempo real descarta as assinaturas.
 */
@Service
public class AtualizarUsuarioUseCase {

    private final EquipeRepositorio equipe;
    private final AlcadaSobreIntegrantes alcada;
    private final VerificadorDeCapacidades capacidades;
    private final PermissaoRepositorio permissoes;
    private final ApplicationEventPublisher eventos;
    private final Clock relogio;

    public AtualizarUsuarioUseCase(EquipeRepositorio equipe, AlcadaSobreIntegrantes alcada,
            VerificadorDeCapacidades capacidades, PermissaoRepositorio permissoes,
            ApplicationEventPublisher eventos, Clock relogio) {
        this.equipe = equipe;
        this.alcada = alcada;
        this.capacidades = capacidades;
        this.permissoes = permissoes;
        this.eventos = eventos;
        this.relogio = relogio;
    }

    @PreAuthorize("hasAnyRole('SUBGESTOR','GESTOR','ADMINISTRADOR') and @capacidades.permite('equipe.editar')")
    @Transactional
    @Auditable(acao = "ATUALIZAR_USUARIO", entidadeTipo = "USUARIO")
    public Optional<Usuario> executar(UUID id, String nome, String email, PapelGerenciavel papel) {
        Optional<Usuario> atual = equipe.travarParaAlteracao(id);
        if (atual.isEmpty()) {
            return Optional.empty();
        }
        alcada.exigirAlvo(id, atual.get().papel());
        boolean mudaPapel = atual.get().papel() != papel.comoPapel();
        if (mudaPapel && !capacidades.permite("equipe.alterar_papel")) {
            throw new ConcessaoNegadaException(ConcessaoNegadaException.Codigo.ALVO_FORA_DA_ALCADA, "papel");
        }
        Optional<Usuario> atualizado = equipe.atualizar(id, nome.trim(), email.trim().toLowerCase(), papel);
        if (atualizado.isPresent() && mudaPapel) {
            revalidarAcessoPorMudancaDePapel(atual.get(), atualizado.get());
        }
        return atualizado;
    }

    private void revalidarAcessoPorMudancaDePapel(Usuario antes, Usuario depois) {
        UUID autor = alcada.atual().id();
        PermissaoRepositorio.Descartadas descartadas = permissoes.descartarExcecoesPorMudancaDePapel(depois.id(), autor);
        long revisao = permissoes.incrementarRevisaoGlobal();
        permissoes.registrarHistorico(new PermissaoRepositorio.RegistroDeHistorico(
                PermissaoRepositorio.Escopo.USUARIO, antes.papel(), depois.id(),
                PermissaoRepositorio.Operacao.MUDANCA_DE_PAPEL, descartadas.revisaoAnterior(),
                descartadas.revisaoNova(), descartadas.antes(), ConfiguracaoDePermissoes.vazia(),
                depois.papel(), null, autor, relogio.instant()));
        eventos.publishEvent(new AcessoDeUsuariosAlterado(Set.of(depois.id()), true, revisao));
    }
}
