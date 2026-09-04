package com.synapse.crm.sharedkernel.identidade;

import java.util.function.Supplier;

/**
 * Marca uma transação autenticada que está servindo a Agenda colaborativa.
 *
 * <p>O contexto não concede papel nem substitui o usuário. Ele apenas permite que as políticas
 * de leitura da Agenda alcancem contatos de toda a instância; operações de negócio continuam
 * passando pelos casos de uso e pelas autorizações normais.
 */
public final class ContextoDeAgenda {

    private static final ThreadLocal<Boolean> ATIVO = new ThreadLocal<>();

    private ContextoDeAgenda() {}

    /** Executa a ação com o marcador ativo, restaurando o estado anterior ao sair. */
    public static <T> T buscarComo(Supplier<T> acao) {
        Boolean anterior = ATIVO.get();
        ATIVO.set(Boolean.TRUE);
        try {
            return acao.get();
        } finally {
            if (anterior == null) {
                ATIVO.remove();
            } else {
                ATIVO.set(anterior);
            }
        }
    }

    public static boolean ativo() {
        return Boolean.TRUE.equals(ATIVO.get());
    }
}
