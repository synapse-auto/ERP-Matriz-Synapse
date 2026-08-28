package com.synapse.crm.atendimento.application;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.sharedkernel.identidade.UsuarioContext;
import com.synapse.crm.sharedkernel.persistencia.Pools;

/** Registra a abertura da conversa para o usuario autenticado. */
@Service
public class MarcarAtendimentoComoLidoUseCase {

    private final AtendimentoRepositorio atendimentos;
    private final UsuarioContext usuarioContext;
    private final Clock relogio;

    public MarcarAtendimentoComoLidoUseCase(
            AtendimentoRepositorio atendimentos, UsuarioContext usuarioContext, Clock relogio) {
        this.atendimentos = atendimentos;
        this.usuarioContext = usuarioContext;
        this.relogio = relogio;
    }

    @PreAuthorize("isAuthenticated()")
    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER)
    public void executar(UUID atendimentoId) {
        UUID usuarioId = usuarioContext.atual().id();
        // #region agent log
        try {
            String linha = "{\"sessionId\":\"ec4265\",\"hypothesisId\":\"B\",\"runId\":\"post-fix\",\"location\":\"MarcarAtendimentoComoLidoUseCase.executar\",\"message\":\"pedido de leitura\",\"data\":{\"atendimentoId\":\""
                    + atendimentoId
                    + "\"},\"timestamp\":"
                    + System.currentTimeMillis()
                    + "}\n";
            java.nio.file.Files.writeString(
                    java.nio.file.Path.of(
                            "C:/Users/marcondes/Desktop/projeto_matriz/debug-ec4265.log"),
                    linha,
                    java.nio.charset.StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.APPEND);
        } catch (Exception ignored) {
        }
        // #endregion
        atendimentos.marcarComoLido(atendimentoId, usuarioId, Instant.now(relogio));
    }
}
