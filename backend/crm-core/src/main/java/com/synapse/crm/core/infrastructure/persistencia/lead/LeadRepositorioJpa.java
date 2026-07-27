package com.synapse.crm.core.infrastructure.persistencia.lead;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

import com.synapse.crm.core.application.lead.FiltroLead;
import com.synapse.crm.core.application.lead.LeadRepositorio;
import com.synapse.crm.core.domain.lead.Lead;
import com.synapse.crm.core.domain.lead.VisibilidadeLead;
import com.synapse.crm.sharedkernel.identidade.UsuarioContext;

/**
 * Unica porta de leitura de lead do sistema.
 *
 * <p>Toda consulta comeca por {@link #visibilidadeAtual()} e recebe o predicado da RN-CRM-01 antes
 * de qualquer outro criterio. Nao existe caminho neste adaptador que chegue ao
 * {@link LeadJpaRepository} sem passar por ali — sao tres metodos, todos visiveis nesta tela, e o
 * teste de arquitetura garante que nenhuma outra classe consiga falar com o repositorio JPA.
 *
 * <p>A visibilidade e derivada do {@link UsuarioContext}, nao recebida por parametro: quem chama
 * nao escolhe o proprio nivel de acesso.
 */
@Repository
class LeadRepositorioJpa implements LeadRepositorio {

    private final LeadJpaRepository jpa;
    private final UsuarioContext usuarioContext;

    LeadRepositorioJpa(LeadJpaRepository jpa, UsuarioContext usuarioContext) {
        this.jpa = jpa;
        this.usuarioContext = usuarioContext;
    }

    @Override
    public List<Lead> listar(FiltroLead filtro) {
        return jpa.findAll(visivel(filtro)).stream().map(LeadEntity::paraDominio).toList();
    }

    @Override
    public Optional<Lead> porId(UUID id) {
        // Consulta por id passa pelo MESMO predicado da listagem. Buscar primeiro e
        // conferir depois deixaria o lead do colega chegar ate a memoria do processo;
        // aqui ele nem sai do banco. Esse e o vetor que mais escapa em revisao.
        Specification<LeadEntity> porIdVisivel =
                visibilidade().and((raiz, consulta, cb) -> cb.equal(raiz.get("id"), id));
        return jpa.findOne(porIdVisivel).map(LeadEntity::paraDominio);
    }

    @Override
    public long contar(FiltroLead filtro) {
        return jpa.count(visivel(filtro));
    }

    /** Visibilidade do usuario da requisicao, sempre. */
    private Specification<LeadEntity> visibilidade() {
        return VisibilidadeLeadSpecification.de(visibilidadeAtual());
    }

    private VisibilidadeLead visibilidadeAtual() {
        return VisibilidadeLead.de(usuarioContext.atual());
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
