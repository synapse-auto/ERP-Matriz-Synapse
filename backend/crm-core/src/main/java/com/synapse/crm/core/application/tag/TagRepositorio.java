package com.synapse.crm.core.application.tag;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.synapse.crm.core.domain.tag.Tag;

/**
 * Porta de acesso a tags.
 *
 * <p>Aqui "todas" e realmente o escopo certo: tags pertencem a operacao, nao a um atendente, e nao
 * ha o que filtrar por usuario. O nome do metodo diz isso em voz alta — {@code listarTodas} — para
 * que ninguem confunda com o {@code listar} de lead, onde "todas" seria um vazamento.
 */
public interface TagRepositorio {

    List<Tag> listarTodas();

    Optional<Tag> porId(UUID id);

    Tag salvar(Tag tag);

    void remover(UUID id);

    boolean existeComNome(String nome, UUID idParaIgnorar);
}
