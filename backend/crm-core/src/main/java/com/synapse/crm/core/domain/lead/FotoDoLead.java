package com.synapse.crm.core.domain.lead;

import java.time.Instant;
import java.util.UUID;

/**
 * Estado da foto de perfil que a integracao externa entregou para um lead.
 *
 * <p>Nao carrega bytes: {@code referencia} aponta para o objeto ja reprocessado no storage e
 * {@code hash} e o SHA-256 dos bytes <b>originais</b> recebidos. O hash existe para o polling da
 * integracao sair barato — reenvio do mesmo arquivo e reconhecido antes de qualquer escrita.
 *
 * <p>Um lead sem foto e representado por {@code referencia} e {@code hash} nulos, nao pela ausencia
 * do registro: quem pergunta precisa distinguir "lead nao visivel" (vazio) de "lead visivel sem
 * foto" (presente e vazio por dentro), e os dois levam a respostas HTTP diferentes.
 */
public record FotoDoLead(UUID leadId, String referencia, String hash, Instant atualizadaEm) {

    public boolean existe() {
        return referencia != null && !referencia.isBlank();
    }

    /** {@code true} quando o hash recebido e exatamente o que ja esta gravado. */
    public boolean mesmoConteudo(String hashRecebido) {
        return existe() && hash != null && hash.equalsIgnoreCase(hashRecebido);
    }
}
