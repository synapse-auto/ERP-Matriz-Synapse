package com.synapse.crm.automacaoconfig.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.automacaoconfig.domain.ConfiguracaoAutomacao;
import com.synapse.crm.automacaoconfig.domain.ConfiguracaoAutomacaoInvalidaException;
import com.synapse.crm.automacaoconfig.domain.ConfiguracaoAutomacaoNaoEncontradaException;
import com.synapse.crm.automacaoconfig.domain.evento.ConfiguracaoAutomacaoAtualizada;
import com.synapse.crm.sharedkernel.identidade.UsuarioContext;

/** Configuracao administrativa da secao Fidelizacao, sem disparar mensagens. */
@Service
public class ConfiguracaoFidelizacaoUseCase {

    private static final String PREFIXO = "fidelizacao.";
    private static final Set<String> CHAVES = Set.of(
            "fidelizacao.aniversario.habilitado",
            "fidelizacao.aniversario.mensagem");

    private final ConfiguracaoAutomacaoRepositorio configuracoes;
    private final UsuarioContext usuarioContext;
    private final ApplicationEventPublisher eventos;
    private final Clock relogio;

    public ConfiguracaoFidelizacaoUseCase(
            ConfiguracaoAutomacaoRepositorio configuracoes,
            UsuarioContext usuarioContext,
            ApplicationEventPublisher eventos,
            Clock relogio) {
        this.configuracoes = configuracoes;
        this.usuarioContext = usuarioContext;
        this.eventos = eventos;
        this.relogio = relogio;
    }

    @PreAuthorize("hasAnyRole('GESTOR', 'ADMINISTRADOR')")
    @Transactional(readOnly = true)
    public List<ConfiguracaoAutomacao> listar() {
        return configuracoes.listarTodas().stream()
                .filter(configuracao -> CHAVES.contains(configuracao.chave()))
                .sorted(Comparator.comparing(ConfiguracaoAutomacao::chave))
                .toList();
    }

    @PreAuthorize("hasAnyRole('GESTOR', 'ADMINISTRADOR')")
    @Transactional
    public ConfiguracaoAutomacao atualizar(String chave, String valor) {
        if (!CHAVES.contains(chave)) {
            throw new ConfiguracaoAutomacaoNaoEncontradaException(chave);
        }
        ConfiguracaoAutomacao atual = configuracoes.porChave(chave)
                .orElseThrow(() -> new ConfiguracaoAutomacaoNaoEncontradaException(chave));
        validarValorEspecifico(chave, valor);
        UUID quemAlterou = usuarioContext.atual().id();
        Instant agora = Instant.now(relogio);
        ConfiguracaoAutomacao salva = configuracoes.salvar(atual.comNovoValor(valor, quemAlterou, agora));
        eventos.publishEvent(new ConfiguracaoAutomacaoAtualizada(
                chave, atual.valor(), salva.valor(), quemAlterou, agora));
        return salva;
    }

    private static void validarValorEspecifico(String chave, String valor) {
        if (valor == null || valor.isBlank()) {
            throw new ConfiguracaoAutomacaoInvalidaException("O valor de '" + chave + "' nao pode ser vazio");
        }
        if (chave.endsWith(".mensagem") && valor.length() > 2000) {
            throw new ConfiguracaoAutomacaoInvalidaException(
                    "'" + chave + "' excede o limite de 2000 caracteres");
        }
    }

    public static boolean eChaveDeFidelizacao(String chave) {
        return chave != null && chave.startsWith(PREFIXO) && CHAVES.contains(chave);
    }
}
