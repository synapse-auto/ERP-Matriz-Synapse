package com.synapse.crm.core.infrastructure.persistencia.lead;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

import com.synapse.crm.core.application.lead.FiltroLead;
import com.synapse.crm.core.application.lead.LeadRepositorio;
import com.synapse.crm.core.application.lead.PaginaDeLeads;
import com.synapse.crm.core.domain.filtro.FiltroDeLeads;
import com.synapse.crm.core.domain.lead.FotoDoLead;
import com.synapse.crm.core.domain.lead.Lead;
import com.synapse.crm.core.domain.lead.LeadResumo;
import com.synapse.crm.core.domain.lead.VisibilidadeLead;
import com.synapse.crm.sharedkernel.identidade.ContextoDeServico;
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
    private final Clock relogio;

    LeadRepositorioJpa(
            LeadJpaRepository jpa, EntityManager em, UsuarioContext usuarioContext, Clock relogio) {
        this.jpa = jpa;
        this.em = em;
        this.usuarioContext = usuarioContext;
        // O filtro "sem retorno ha N dias" precisa saber que horas sao. Vem do relogio
        // injetado, e nao de Instant.now() espalhado, para o teste conseguir fixar a data.
        this.relogio = relogio;
    }

    /**
     * Listagem por projecao: o SELECT nomeia as colunas exibidas, uma a uma.
     *
     * <p>{@code notas} e {@code resumo_ia} nao aparecem porque {@link LeadResumo} nao tem onde
     * guarda-los. Nao e disciplina de quem escreve a query — e o tipo que impede.
     */
    @Override
    public List<LeadResumo> listar(FiltroLead filtro) {
        return projetarResumos(visivel(filtro));
    }

    /**
     * Filtro modular (E03b), paginado (E16 §Bloco 1). Mesma projecao e mesma visibilidade da
     * listagem comum.
     *
     * <p>Busca {@code tamanho + 1} linhas e corta a extra para saber {@code temMais} sem um segundo
     * {@code COUNT} — o mesmo truque de {@code LembreteRepositorioJdbc}. O total exato, quando a tela
     * precisa dele, e {@link #contar(FiltroDeLeads)}.
     */
    @Override
    public PaginaDeLeads listar(FiltroDeLeads filtro, int pagina, int tamanho) {
        List<LeadResumo> linhas = projetarResumosPaginado(visivel(filtro), pagina, tamanho);
        boolean temMais = linhas.size() > tamanho;
        List<LeadResumo> itens = temMais ? linhas.subList(0, tamanho) : linhas;
        return new PaginaDeLeads(List.copyOf(itens), pagina, temMais);
    }

    @Override
    public long contar(FiltroDeLeads filtro) {
        // COUNT no banco, nunca listar().size(): a tela chama isto a cada mudanca de
        // criterio, e trazer as linhas so para medi-las e o caminho para a lentidao.
        return jpa.count(visivel(filtro));
    }

    private List<LeadResumo> projetarResumos(Specification<LeadEntity> especificacao) {
        return montarConsulta(especificacao).getResultList();
    }

    private List<LeadResumo> projetarResumosPaginado(
            Specification<LeadEntity> especificacao, int pagina, int tamanho) {
        return montarConsulta(especificacao)
                .setFirstResult(Math.multiplyExact(pagina, tamanho))
                .setMaxResults(tamanho + 1)
                .getResultList();
    }

    private TypedQuery<LeadResumo> montarConsulta(Specification<LeadEntity> especificacao) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<LeadResumo> consulta = cb.createQuery(LeadResumo.class);
        Root<LeadEntity> raiz = consulta.from(LeadEntity.class);

        consulta.select(cb.construct(
                LeadResumo.class,
                raiz.get(LeadEntity.Campos.ID),
                raiz.get(LeadEntity.Campos.NOME),
                raiz.get(LeadEntity.Campos.TELEFONE),
                raiz.get(LeadEntity.Campos.EMPRESA),
                raiz.get(LeadEntity.Campos.LOCALIZACAO),
                raiz.get(LeadEntity.Campos.STATUS_BASICO),
                raiz.get(LeadEntity.Campos.ETAPA),
                raiz.get(LeadEntity.Campos.ATENDENTE_RESPONSAVEL_ID),
                raiz.get(LeadEntity.Campos.NUM_ATENDIMENTOS),
                raiz.get(LeadEntity.Campos.NUM_MENSAGENS),
                raiz.get(LeadEntity.Campos.CRIADO_EM),
                raiz.get(LeadEntity.Campos.ULTIMA_INTERACAO_EM)));

        consulta.where(especificacao.toPredicate(raiz, consulta, cb));
        consulta.orderBy(cb.asc(raiz.get(LeadEntity.Campos.NOME)));

        return em.createQuery(consulta);
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

    /**
     * So a coluna id, sob {@link #visibilidade()} — nunca {@link #visivel(FiltroLead)} ou {@link
     * #visivel(FiltroDeLeads)}, que agregam um criterio extra que aqui nao faz sentido: quem chama
     * isto quer o recorte de visibilidade inteiro, para escopar uma consulta em outra tabela.
     */
    @Override
    public List<UUID> idsVisiveis() {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<UUID> consulta = cb.createQuery(UUID.class);
        Root<LeadEntity> raiz = consulta.from(LeadEntity.class);
        consulta.select(raiz.get(LeadEntity.Campos.ID));
        consulta.where(visibilidade().toPredicate(raiz, consulta, cb));
        return em.createQuery(consulta).getResultList();
    }

    @Override
    public List<String> localizacoesVisiveis() {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<String> consulta = cb.createQuery(String.class);
        Root<LeadEntity> raiz = consulta.from(LeadEntity.class);
        var localizacao = raiz.<String>get(LeadEntity.Campos.LOCALIZACAO);
        consulta.select(localizacao).distinct(true);
        consulta.where(visibilidade()
                .and((lead, ignorada, builder) -> builder.and(
                        builder.isNotNull(lead.get(LeadEntity.Campos.LOCALIZACAO)),
                        builder.notEqual(builder.trim(lead.get(LeadEntity.Campos.LOCALIZACAO)), "")))
                .toPredicate(raiz, consulta, cb));
        consulta.orderBy(cb.asc(localizacao));
        return em.createQuery(consulta).getResultList();
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

    /** Mesmo predicado da leitura de ficha: a foto de um lead de colega nao sai do banco. */
    @Override
    public Optional<FotoDoLead> fotoDoLead(UUID leadId) {
        return entidadeVisivel(leadId).map(LeadEntity::foto);
    }

    @Override
    public boolean atualizarFoto(UUID leadId, String referencia, String hash, Instant quando) {
        return entidadeVisivel(leadId).map(entidade -> {
            entidade.trocarFoto(referencia, hash, quando);
            jpa.save(entidade);
            return true;
        }).orElse(false);
    }

    private Optional<LeadEntity> entidadeVisivel(UUID id) {
        return jpa.findOne(visibilidade()
                .and((raiz, consulta, cb) -> cb.equal(raiz.get(LeadEntity.Campos.ID), id)));
    }

    private Specification<LeadEntity> visibilidade() {
        VisibilidadeLead visibilidade = ContextoDeServico.ativo()
                ? new VisibilidadeLead.Ampla()
                : VisibilidadeLead.de(usuarioContext.atual());
        return VisibilidadeLeadSpecification.de(visibilidade);
    }

    /**
     * Visibilidade E a arvore de criterios do usuario.
     *
     * <p>Este metodo e o ponto onde o isolamento de agenda poderia ter vazado depois de toda a
     * blindagem da E02, e por isso ele e de uma linha so. A {@code VisibilidadeLeadSpecification} vem
     * primeiro e o filtro entra com {@code .and(...)}: um {@code AND} so consegue <b>tirar</b> linhas
     * do conjunto. Nao existe criterio que um atendente possa montar que devolva o lead de um colega —
     * no maximo ele pede um conjunto que resulta vazio.
     *
     * <p>Trocar este {@code and} por um {@code or}, ou inverter a ordem para o filtro decidir sozinho,
     * seria vazamento de lead entre atendentes. Ha teste de integracao que prova o contrario.
     */
    private Specification<LeadEntity> visivel(FiltroDeLeads filtro) {
        return visibilidade().and(InterpretadorDeCriterio.interpretar(filtro, Instant.now(relogio)));
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
