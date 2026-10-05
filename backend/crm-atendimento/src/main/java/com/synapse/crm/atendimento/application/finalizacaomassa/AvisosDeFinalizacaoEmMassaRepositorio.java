package com.synapse.crm.atendimento.application.finalizacaomassa;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Outbox do aviso de conclusao: uma linha por usuario efetivamente afetado, gravada na mesma transacao que
 * conclui a operacao (ver {@link FinalizacaoEmMassaRepositorio#concluirSeNaoHaPendentes}). Contexto de servico.
 */
public interface AvisosDeFinalizacaoEmMassaRepositorio {

    record UsuarioAfetado(UUID id, String nome, int finalizados) {}

    /**
     * Tudo o que o destinatario precisa para a mensagem, ja resolvido: quanto foi dele, o total, o parcial
     * (ignorados e falhas) e quem mais foi afetado.
     */
    record AvisoPendente(
            UUID operacaoId,
            UUID usuarioId,
            int finalizadosDoUsuario,
            int totalFinalizados,
            int ignorados,
            int falhas,
            List<UsuarioAfetado> afetados,
            int tentativas) {

        public boolean parcial() {
            return ignorados > 0 || falhas > 0;
        }
    }

    /** Proximo aviso devido, com lock de linha (SKIP LOCKED): dois publicadores nunca pegam o mesmo. */
    Optional<AvisoPendente> reservarProximo(Instant agora);

    void marcarEnviado(UUID operacaoId, UUID usuarioId, Instant quando);

    /** Registra a falha e agenda nova tentativa; apos o maximo, desiste (ESGOTADO). @return se esgotou. */
    boolean registrarFalha(UUID operacaoId, UUID usuarioId, String erro, Instant agora, int maximoDeTentativas);
}
