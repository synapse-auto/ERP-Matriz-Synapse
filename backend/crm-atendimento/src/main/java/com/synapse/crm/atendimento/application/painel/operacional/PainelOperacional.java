package com.synapse.crm.atendimento.application.painel.operacional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PainelOperacional(Instant atualizadoEm, Instant finalizadosInicio, Instant finalizadosFim,
        String fuso, int limiteMinutos, Instant proximaTransicaoEm, List<Linha> equipe) {
    public record Contagens(long novasMensagens, long novasConversas, long ativos, long pendentes,
            long semResposta, long finalizados, long emAberto, long potenciais, Instant esperaDesde) {}
    public record Linha(UUID atendenteId, String nome, String fotoUrl, String papel, String presenca,
            boolean entrada, boolean elegivel, Instant proximaTransicaoEm, Contagens contagens) {}
    public record Conversa(UUID atendimentoId, UUID leadId, String leadNome, String leadFotoUrl,
            String leadEmpresa, String status, UUID atendenteId, String atendenteNome,
            long naoLidas, Instant atividadeEm, Instant esperaDesde, String etapaNome, String etapaCor,
            String situacao, String ultimaMensagemPreview, String ultimaMensagemTipo,
            Instant ultimaMensagemEm, boolean respondido, String canalTipo, boolean convitePendente) {}
    public record Pagina(List<Conversa> itens, String proximoCursor) {}
}
