package com.synapse.crm.core.infrastructure.persistencia.lead;

import org.springframework.data.jpa.domain.Specification;

import com.synapse.crm.core.domain.lead.StatusBasicoLead;
import com.synapse.crm.core.domain.lead.VisibilidadeLead;

/**
 * Traduz {@link VisibilidadeLead} (regra de negocio, Java puro) para SQL.
 *
 * <p>A regra em si nao mora aqui — mora no dominio. Aqui mora so a traducao, e e por isso que a
 * mesma decisao vale para consulta em banco e para checagem em memoria sem risco de divergirem.
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

            // O atendente ve o que e dele MAIS o que ainda nao tem dono (grupo
            // "Potenciais"). O OR e a regra inteira: sem a segunda perna, ninguem
            // pegaria lead novo; sem a primeira, todos veriam tudo.
            case VisibilidadeLead.DoAtendente visao -> (raiz, consulta, cb) -> cb.or(
                    cb.equal(raiz.get(LeadEntity.Campos.ATENDENTE_RESPONSAVEL_ID), visao.atendenteId()),
                    cb.equal(raiz.get(LeadEntity.Campos.STATUS_BASICO), StatusBasicoLead.IA));
        };
    }
}
