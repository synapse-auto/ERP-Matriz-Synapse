package com.synapse.crm.core.infrastructure.persistencia.lead;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

import com.synapse.crm.core.application.lead.FiltroLead;
import com.synapse.crm.core.application.lead.LeadRepositorio;
import com.synapse.crm.core.domain.lead.Lead;
import com.synapse.crm.core.domain.lead.LeadResumo;
import com.synapse.crm.core.domain.lead.VisibilidadeLead;
import com.synapse.crm.sharedkernel.identidade.UsuarioContext;

/**
 * Unica porta de leitura e escrita de lead do sistema.
 *
 * <p>Toda consulta comeca por {@link #visibilidade()} e recebe o predicado da RN-CRM-01 antes de
 * qualquer outro criterio. Nao existe caminho neste adaptador que chegue ao {@link
 * LeadJpaRepository} sem passar por ali, e o teste de arquitetura garante que nenhuma outra classe
 * consiga falar com o repositorio JPA.
 *
 * <p>A visibilidade e derivada do {@link UsuarioContext}, nao recebida por parametro: quem chama
 * nao escolhe o proprio nivel de acesso.
 */
@Repository
class LeadRepositorioJpa implements LeadRepositorio {

    private final LeadJpaRepository jpa;
    private final EntityManager em;
    private final UsuarioContext usuarioContext;

    LeadRepositorioJpa(LeadJpaRepository jpa, EntityManager em, UsuarioContext usuarioContext) {
        this.jpa = jpa;
        this.em = em;
        this.usuarioContext = usuarioContext;
    }

    /**
     * Listagem por projecao: o SELECT nomeia as colunas exibidas, uma a uma.
     *
     * <p>{@code notas} e {@code resumo_ia} nao aparecem porque {@link LeadResumo} nao tem onde
     * guarda-los. Nao e disciplina de quem escreve a query — e o tipo que impede.
     */
    @Override
    public List<LeadResumo> listar(FiltroLead filtro) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<LeadResumo> consulta = cb.createQuery(LeadResumo.class);
        Root<LeadEntity> raiz = consulta.from(LeadEntity.class);

        consulta.select(cb.construct(
                LeadResumo.class,
                raiz.get(LeadEntity.Campos.ID),
                raiz.get(LeadEntity.Campos.NOME),
                raiz.get(LeadEntity.Campos.TELEFONE),
                raiz.get(LeadEntity.Campos.EMPRESA),
                raiz.get(LeadEntity.Campos.STATUS_BASICO),
                raiz.get(LeadEntity.Campos.ETAPA),
                raiz.get(LeadEntity.Campos.ATENDENTE_RESPONSAVEL_ID),
                raiz.get(LeadEntity.Campos.NUM_ATENDIMENTOS),
                raiz.get(LeadEntity.Campos.NUM_MENSAGENS),
                raiz.get(LeadEntity.Campos.CRIADO_EM)));

        consulta.where(visivel(filtro).toPredicate(raiz, consulta, cb));
        consulta.orderBy(cb.asc(raiz.get(LeadEntity.Campos.NOME)));

        return em.createQuery(consulta).getResultList();
    }

    @Override
    public Optional<Lead> porId(UUID id) {
        // Consulta por id passa pelo MESMO predicado da listagem. Buscar primeiro e
        // conferir depois deixaria o lead do colega chegar ate a memoria do processo;
        // aqui ele nem sai do banco. Esse e o vetor que mais escapa em revisao.
        return entidadeVisivel(id).map(LeadEntity::paraDominio);
    }

    @Override
    public long contar(FiltroLead filtro) {
        return jpa.count(visivel(filtro));
    }

    @Override
    public Optional<Lead> salvar(Lead lead) {
        // Carrega pelo predicado de visibilidade: editar lead de colega nao encontra
        // nada para editar, e o caso de uso responde 404 como na leitura.
        return entidadeVisivel(lead.id()).map(entidade -> {
            entidade.aplicar(lead);
            return jpa.save(entidade).paraDominio();
        });
    }

    @Override
    public void somarAtendimentos(UUID leadId, int quantidade) {
        jpa.somarAtendimentos(leadId, quantidade);
    }

    @Override
    public void somarMensagens(UUID leadId, int quantidade) {
        jpa.somarMensagens(leadId, quantidade);
    }

    private Optional<LeadEntity> entidadeVisivel(UUID id) {
        return jpa.findOne(visibilidade()
                .and((raiz, consulta, cb) -> cb.equal(raiz.get(LeadEntity.Campos.ID), id)));
    }

    private Specification<LeadEntity> visibilidade() {
        return VisibilidadeLeadSpecification.de(VisibilidadeLead.de(usuarioContext.atual()));
    }

    /** Visibilidade E o filtro pedido — nessa ordem, e nunca so o filtro. */
    private Specification<LeadEntity> visivel(FiltroLead filtro) {
        Specification<LeadEntity> spec = visibilidade();

        if (filtro.temTermoBusca()) {
            String padrao = "%" + filtro.termoBusca().toLowerCase() + "%";
            spec = spec.and((raiz, consulta, cb) ->
                    cb.like(cb.lower(raiz.get(LeadEntity.Campos.NOME)), padrao));
        }
        if (filtro.status() != null) {
            spec = spec.and((raiz, consulta, cb) ->
                    cb.equal(raiz.get(LeadEntity.Campos.STATUS_BASICO), filtro.status()));
        }
        return spec;
    }
}
