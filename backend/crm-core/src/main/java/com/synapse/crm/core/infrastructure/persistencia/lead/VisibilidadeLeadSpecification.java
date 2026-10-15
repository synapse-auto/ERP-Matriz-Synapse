package com.synapse.crm.core.infrastructure.persistencia.lead;

import org.springframework.data.jpa.domain.Specification;

import com.synapse.crm.core.domain.lead.StatusBasicoLead;
import com.synapse.crm.core.domain.lead.VisibilidadeLead;

/**
 * Traduz {@link VisibilidadeLead} (regra de negocio, Java puro) para SQL.
 *
 * <p>O dominio define o recorte da carteira. A excecao contextual de participacao ativa (docs/51)
 * exige estado persistido que um Lead isolado nao possui: {@link #comParticipacao} compoe esse
 * recorte na leitura pontual, ainda sob RLS. A listagem da Agenda preserva a carteira base.
 *
 * <p>O {@code switch} e exaustivo sobre um tipo selado: acrescentar um modo de visibilidade novo
 * quebra a compilacao aqui ate que alguem escreva o predicado correspondente. Nao existe caminho em
 * que uma variante nova vire "sem filtro" por omissao.
 *
 * <p>E componivel de proposito: retorna uma {@code Specification} comum, entao o filtro modular da
 * E03 entra com {@code .and(...)} por cima sem tocar nesta classe.
 */
final class VisibilidadeLeadSpecification {

    private VisibilidadeLeadSpecification() {}

    static Specification<LeadEntity> de(VisibilidadeLead visibilidade) {
        return switch (visibilidade) {
            case VisibilidadeLead.Ampla ignorado -> (raiz, consulta, cb) -> cb.conjunction();

            // O atendente ve o que e dele MAIS Potenciais (IA) MAIS finalizados (E145:
            // balcao — paridade com rls_lead). Sem as pernas de
            // balcao, ninguem pegaria lead novo nem reativaria o de colega; sem a primeira,
            // todos veriam conversas em andamento.
            case VisibilidadeLead.DoAtendente visao -> (raiz, consulta, cb) -> cb.or(
                    cb.equal(raiz.get(LeadEntity.Campos.ATENDENTE_RESPONSAVEL_ID), visao.atendenteId()),
                    cb.equal(raiz.get(LeadEntity.Campos.STATUS_BASICO), StatusBasicoLead.IA),
                    cb.equal(raiz.get(LeadEntity.Campos.STATUS_BASICO), StatusBasicoLead.FINALIZADO));
        };
    }

    /** Excecao contextual para a ficha do recurso, sem ampliar a listagem da Agenda. */
    static Specification<LeadEntity> comParticipacao(VisibilidadeLead visibilidade) {
        return switch (visibilidade) {
            case VisibilidadeLead.Ampla ignorado -> de(visibilidade);
            case VisibilidadeLead.DoAtendente visao -> de(visibilidade).or((raiz, consulta, cb) -> {
                var participacao = consulta.subquery(Integer.class);
                var participante = participacao.from(ParticipacaoVisivelLeadEntity.class);
                participacao.select(cb.literal(1)).where(
                        cb.equal(participante.get("leadId"), raiz.get(LeadEntity.Campos.ID)),
                        cb.equal(participante.get("usuarioId"), visao.atendenteId()));
                return cb.exists(participacao);
            });
        };
    }
}
