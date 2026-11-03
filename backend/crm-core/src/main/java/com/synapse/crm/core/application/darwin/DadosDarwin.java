package com.synapse.crm.core.application.darwin;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Contratos próprios do CRM. Não transportam resumo, mensagens nem payload do provedor. */
public final class DadosDarwin {
    private DadosDarwin() {}
    public record Candidato(String sessaoId, String nome, String cpf, boolean cpfInvalido) {
        @Override public String toString() { return "CandidatoDarwin[redigido]"; }
    }
    public record Resultado(List<Candidato> candidatos, boolean limiteAtingido) {
        public Resultado { candidatos = List.copyOf(candidatos); }
    }
    public enum Estado { ENCONTRADO, SEM_RESULTADO, AMBIGUO, SEM_IDENTIDADE }
    public record Previa(Estado estado, List<Candidato> candidatos, boolean limiteAtingido,
                         String nomeAtual, String cpfAtual, String confirmacao) {
        @Override public String toString() { return "PreviaDarwin[redigido]"; }
    }
    public record Recibo(UUID usuarioId, UUID leadId, String telefone, String nomeAnterior,
                         String cpfAnterior, Candidato candidato, Instant expiraEm,
                         String campoNome, String campoCpf, Long canalId) {
        @Override public String toString() { return "ReciboDarwin[redigido]"; }
    }
    public record Aplicacao(UUID leadId, String origem, List<String> camposAlterados) {}
}
