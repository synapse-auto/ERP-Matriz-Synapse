package com.synapse.crm.core.infrastructure.persistencia.tag;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Repository;

import com.synapse.crm.core.application.tag.TagRepositorio;
import com.synapse.crm.core.domain.tag.Tag;

/**
 * Adaptador JPA das tags.
 *
 * <p>Segue o mesmo formato do adaptador de lead — porta sem acesso cru, interface Spring Data
 * pacote-privada, adaptador unico — mesmo sem regra de visibilidade para aplicar. Manter o formato
 * uniforme e o que faz a regra do ArquiteturaTest valer para todo agregado sem excecao a lembrar.
 */
@Repository
class TagRepositorioJpa implements TagRepositorio {

    private final TagJpaRepository jpa;

    TagRepositorioJpa(TagJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public List<Tag> listarTodas() {
        return jpa.findAllByOrderByNomeAsc().stream().map(TagEntity::paraDominio).toList();
    }

    @Override
    public Optional<Tag> porId(UUID id) {
        return jpa.findById(id).map(TagEntity::paraDominio);
    }

    @Override
    public Tag salvar(Tag tag) {
        TagEntity entidade = jpa.findById(tag.id()).orElseGet(() -> new TagEntity(tag));
        entidade.aplicar(tag);
        return jpa.save(entidade).paraDominio();
    }

    @Override
    public void remover(UUID id) {
        jpa.deleteById(id);
    }

    @Override
    public boolean existeComNome(String nome, UUID idParaIgnorar) {
        return idParaIgnorar == null
                ? jpa.existsByNomeIgnoreCase(nome)
                : jpa.existsByNomeIgnoreCaseAndIdNot(nome, idParaIgnorar);
    }
}
