package com.synapse.crm.atendimento.application;

import java.util.Optional;
import java.util.UUID;

/**
 * Atendente vinculado a um lead (D3b da Disbrel): o último humano que recebeu ou assumiu a conversa. É só uma
 * preferência do rodízio da IA — não é permissão, não é responsável e nenhuma política de visibilidade o lê.
 *
 * <p>Tudo atrás da chave {@code ia.distribuicao.atendente_vinculado}, que nasce {@code false}: desligada, nada é
 * gravado e nada é honrado.
 */
public interface VinculoDoLeadRepositorio {

    /**
     * O atendente vinculado ao lead deste atendimento, <b>somente</b> se a chave está ligada e existe vínculo.
     * Uma consulta só: a chave desligada custa uma busca por chave primária e devolve vazio.
     */
    Optional<UUID> vinculadoDoAtendimento(UUID atendimentoId);

    /** A chave está ligada? Consultada pelo gravador antes de qualquer escrita. */
    boolean habilitado();

    /** Grava (ou troca) o vínculo do lead. Idempotente. */
    void registrar(UUID leadId, UUID atendenteId, String origem);
}
