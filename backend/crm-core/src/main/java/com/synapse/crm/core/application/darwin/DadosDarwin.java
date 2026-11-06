package com.synapse.crm.core.application.darwin;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Contratos próprios do CRM. Não transportam resumo, mensagens nem payload do provedor. */
public final class DadosDarwin {
    private DadosDarwin() {}
    // Campo legado apenas por compatibilidade do JSON CRM; médico não retorna sessão.
    public record Candidato(@io.swagger.v3.oas.annotations.media.Schema(deprecated = true, nullable = true) String sessaoId,
                            String nome, String cpf, boolean cpfInvalido, String email, boolean emailInvalido,
                            java.time.LocalDate nascimento) {
        public Candidato(String sessaoId, String nome, String cpf, boolean cpfInvalido, String email, boolean emailInvalido) {
            this(sessaoId, nome, cpf, cpfInvalido, email, emailInvalido, null);
        }
        @Override public String toString() { return "CandidatoDarwin[redigido]"; }
    }
    public record Resultado(List<Candidato> candidatos, boolean limiteAtingido) {
        public Resultado { candidatos = List.copyOf(candidatos); }
    }
    public enum Estado { ENCONTRADO, SEM_RESULTADO, AMBIGUO, SEM_IDENTIDADE }
    public enum EstadoIntegracao { DESABILITADA, CONFIGURACAO_INCOMPLETA, DISPONIVEL }
    public record Previa(Estado estado, List<Candidato> candidatos, boolean limiteAtingido,
                         String nomeAtual, String cpfAtual, String emailAtual, String confirmacao) {
        @Override public String toString() { return "PreviaDarwin[redigido]"; }
    }
    public record Recibo(UUID usuarioId, UUID leadId, String telefone, String nomeAnterior,
                         String cpfAnterior, String emailAnterior, Candidato candidato, Instant expiraEm,
                         String origem) {
        @Override public String toString() { return "ReciboDarwin[redigido]"; }
    }
    public record Aplicacao(UUID leadId, String origem, List<String> camposAlterados) {}
}
