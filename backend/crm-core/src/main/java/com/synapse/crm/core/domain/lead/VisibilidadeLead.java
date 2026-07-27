package com.synapse.crm.core.domain.lead;

import java.util.Objects;
import java.util.UUID;

import com.synapse.crm.sharedkernel.identidade.UsuarioAutenticado;

/**
 * RN-CRM-01: o que cada usuario pode enxergar da base de leads.
 *
 * <p>Os atendentes trabalham por comissao e disputam leads entre si. Um atendente enxergar o lead
 * de outro nao e bug de tela, e problema comercial na casa do cliente. Por isso a regra vive aqui,
 * em Java puro, num tipo unico — e nao espalhada em {@code if}s pelos casos de uso ou, pior, no
 * frontend.
 *
 * <p>O tipo e <strong>selado</strong> de proposito. A traducao para SQL
 * ({@code VisibilidadeLeadSpecification}) casa exaustivamente sobre estas alternativas: no dia em
 * que alguem acrescentar um modo de visibilidade — "subgestor ve so a propria equipe", por exemplo
 * — o compilador para o build ate que a traducao correspondente exista. Uma variante nova nao tem
 * como escapar silenciosamente para o banco como "sem filtro".
 */
public sealed interface VisibilidadeLead {

    /** Visao ampla: gestor, subgestor e administrador enxergam a base inteira. */
    record Ampla() implements VisibilidadeLead {}

    /**
     * Visao do atendente: os leads que sao dele, mais os que ainda nao tem dono.
     *
     * @param atendenteId dono dos leads que este usuario pode ver
     */
    record DoAtendente(UUID atendenteId) implements VisibilidadeLead {
        public DoAtendente {
            Objects.requireNonNull(atendenteId, "atendenteId e obrigatorio na visao de atendente");
        }
    }

    /**
     * Deriva a visibilidade do usuario da requisicao.
     *
     * <p>Unico ponto do sistema que decide isso. Se um papel novo aparecer, e aqui que ele entra.
     */
    static VisibilidadeLead de(UsuarioAutenticado usuario) {
        Objects.requireNonNull(usuario, "nao ha visibilidade de lead sem usuario autenticado");
        return usuario.enxergaTodosOsLeads() ? new Ampla() : new DoAtendente(usuario.id());
    }

    /**
     * Mesma regra da consulta, aplicavel a um lead ja carregado.
     *
     * <p>Existe para que a regra possa ser testada sem banco. A fonte da verdade continua sendo uma
     * so: as alternativas deste tipo.
     */
    default boolean permite(Lead lead) {
        Objects.requireNonNull(lead, "lead e obrigatorio");
        return switch (this) {
            case Ampla ignorado -> true;
            case DoAtendente visao ->
                lead.pertenceA(visao.atendenteId()) || lead.estaDisponivelParaTodos();
        };
    }
}
