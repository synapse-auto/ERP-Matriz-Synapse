package com.synapse.crm.core.application.lead;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.synapse.crm.core.domain.lead.Lead;

/**
 * Porta de consulta de lead.
 *
 * <p><strong>Nao existe metodo que retorne lead sem filtro de visibilidade.</strong> Nao ha
 * {@code findAll()}, nao ha {@code findById()} cru: o vocabulario desta interface simplesmente nao
 * consegue expressar "me da todos os leads". Um caso de uso futuro nao precisa lembrar de aplicar a
 * regra — ele nao tem como pedir uma consulta sem ela.
 *
 * <p>A visibilidade tambem nao e parametro. Se fosse, alguem em algum momento passaria
 * {@code VisibilidadeLead.Ampla} "so para testar" e isso chegaria em producao. Ela e derivada do
 * usuario autenticado dentro do adaptador, que e o unico lugar do sistema que fala com o JPA de
 * lead — e o teste de arquitetura reprova qualquer outro que tente.
 */
public interface LeadRepositorio {

    /** Leads visiveis ao usuario da requisicao que casem com o filtro. */
    List<Lead> listar(FiltroLead filtro);

    /**
     * Lead por id, <em>se</em> visivel ao usuario da requisicao.
     *
     * <p>Vazio significa "nao existe ou nao e seu", e os dois casos viram 404. Distinguir os dois
     * na resposta contaria ao atendente que o lead existe e e de um colega — que e justamente a
     * informacao que a RN-CRM-01 protege.
     */
    Optional<Lead> porId(UUID id);

    long contar(FiltroLead filtro);
}
