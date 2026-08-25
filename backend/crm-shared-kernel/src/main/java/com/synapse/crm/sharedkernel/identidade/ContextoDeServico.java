package com.synapse.crm.sharedkernel.identidade;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Marca um trecho de codigo como execucao de servico, nao de usuario.
 *
 * <p>Jobs {@code @Scheduled}, consumidores de fila e o publisher da outbox nao tem usuario
 * autenticado, mas precisam enxergar a base inteira. Sem uma forma de dizer isso, restariam duas
 * saidas ruins: afrouxar a politica para quem nao tem contexto (que e exatamente o "falha aberto"
 * que nao queremos) ou fazer o job se passar por algum usuario real.
 *
 * <p>O marcador e explicito e de escopo curto de proposito — vale so durante a chamada. Deixar um
 * processo inteiro em modo servico transformaria a excecao na regra.
 */
public final class ContextoDeServico {

    /** Valor gravado em {@code app.papel}; a politica SQL reconhece este literal. */
    public static final String PAPEL = "SERVICO";

    private static final ThreadLocal<String> SERVICO_ATIVO = new ThreadLocal<>();
    private static volatile PonteDeAutoridade ponteDeAutoridade = PonteDeAutoridade.NAO_INSTALADA;

    private ContextoDeServico() {}

    /**
     * Ponte para a camada de seguranca: o shared kernel continua Java puro, mas o ponto que abre o
     * contexto de RLS tambem abre a autoridade equivalente do Spring Security.
     */
    @FunctionalInterface
    public interface PonteDeAutoridade {

        Escopo entrar(String nomeDoServico);

        PonteDeAutoridade NAO_INSTALADA = nome -> {
            throw new IllegalStateException(
                    "ponte de autoridade do ContextoDeServico nao foi instalada");
        };
    }

    /** Escopo retornado pela camada de seguranca; fechar restaura a autoridade anterior. */
    @FunctionalInterface
    public interface Escopo {
        void fechar();
    }

    /** Instala a ponte da infraestrutura de seguranca ao subir a aplicacao. */
    public static void instalarPonteDeAutoridade(PonteDeAutoridade ponte) {
        ponteDeAutoridade = Objects.requireNonNull(ponte, "ponte de autoridade obrigatoria");
    }

    /** Executa {@code acao} em contexto de servico e restaura o contexto anterior ao terminar. */
    public static void executarComo(String nomeDoServico, Runnable acao) {
        buscarComo(nomeDoServico, () -> {
            acao.run();
            return null;
        });
    }

    public static <T> T buscarComo(String nomeDoServico, Supplier<T> acao) {
        Objects.requireNonNull(nomeDoServico, "o servico precisa se identificar");
        String anterior = SERVICO_ATIVO.get();
        SERVICO_ATIVO.set(nomeDoServico);
        Escopo autoridade;
        try {
            autoridade = ponteDeAutoridade.entrar(nomeDoServico);
        } catch (RuntimeException erro) {
            restaurar(anterior);
            throw erro;
        }
        try {
            return acao.get();
        } finally {
            try {
                autoridade.fechar();
            } finally {
                restaurar(anterior);
            }
        }
    }

    private static void restaurar(String anterior) {
        // Restaura em vez de limpar: chamadas aninhadas nao podem se anular.
        if (anterior == null) {
            SERVICO_ATIVO.remove();
        } else {
            SERVICO_ATIVO.set(anterior);
        }
    }

    public static boolean ativo() {
        return SERVICO_ATIVO.get() != null;
    }

    public static String nomeAtual() {
        return SERVICO_ATIVO.get();
    }
}
