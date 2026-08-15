package com.synapse.crm.automacaoconfig.application;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.automacaoconfig.domain.ConfiguracaoAutomacao;
import com.synapse.crm.automacaoconfig.domain.ConfiguracaoAutomacaoNaoEncontradaException;
import com.synapse.crm.automacaoconfig.domain.evento.ConfiguracaoAutomacaoAtualizada;
import com.synapse.crm.sharedkernel.identidade.UsuarioContext;

/**
 * Altera um parametro de automacao (E07 §3).
 *
 * <p>Restrito a gestão e administrador: e um parametro que muda o comportamento da automacao para
 * todo mundo, nao uma preferencia pessoal. A validacao de faixa mora em {@link ConfiguracaoAutomacao},
 * nao aqui — este caso de uso so orquestra carregar, validar, salvar e avisar quem precisa reagir.
 */
@Service
public class AtualizarConfiguracaoAutomacaoUseCase {

    private final ConfiguracaoAutomacaoRepositorio configuracoes;
    private final UsuarioContext usuarioContext;
    private final ApplicationEventPublisher eventos;
    private final Clock relogio;

    public AtualizarConfiguracaoAutomacaoUseCase(
            ConfiguracaoAutomacaoRepositorio configuracoes,
            UsuarioContext usuarioContext,
            ApplicationEventPublisher eventos,
            Clock relogio) {
        this.configuracoes = configuracoes;
        this.usuarioContext = usuarioContext;
        this.eventos = eventos;
        this.relogio = relogio;
    }

    @PreAuthorize("hasAnyRole('GESTOR', 'SUBGESTOR', 'ADMINISTRADOR')")
    @Transactional
    public ConfiguracaoAutomacao executar(String chave, String novoValor) {
        ConfiguracaoAutomacao atual = configuracoes
                .porChave(chave)
                .orElseThrow(() -> new ConfiguracaoAutomacaoNaoEncontradaException(chave));

        UUID quemAlterou = usuarioContext.atual().id();
        Instant agora = Instant.now(relogio);
        String valorAntes = atual.valor();

        ConfiguracaoAutomacao salva = configuracoes.salvar(atual.comNovoValor(novoValor, quemAlterou, agora));

        // AFTER_COMMIT: cache e auditoria so devem reagir se a alteracao realmente
        // vingou. Duas reacoes independentes, por isso evento e nao chamada direta.
        eventos.publishEvent(new ConfiguracaoAutomacaoAtualizada(
                chave, valorAntes, salva.valor(), quemAlterou, agora));

        return salva;
    }
}
