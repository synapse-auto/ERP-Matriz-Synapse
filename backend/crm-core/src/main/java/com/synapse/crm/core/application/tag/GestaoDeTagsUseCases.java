package com.synapse.crm.core.application.tag;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.core.domain.tag.Tag;

/**
 * Casos de uso de tag (RN-CRM-03).
 *
 * <p>Leitura para todo mundo; escrita so para gestao. A autorizacao e declarada aqui, metodo a
 * metodo, e nao num mapa central de rotas: quem le este arquivo ve quem pode executar o que.
 *
 * <p>Tags sao da operacao inteira. Deixar atendente criar tag pareceria conveniente e produziria,
 * em duas semanas, quinze variacoes de "orcamento" — e relatorio por tag deixaria de significar
 * alguma coisa.
 */
@Service
public class GestaoDeTagsUseCases {

    private static final String SO_GESTAO = "hasAnyRole('GESTOR', 'SUBGESTOR', 'ADMINISTRADOR')";

    private final TagRepositorio tags;

    public GestaoDeTagsUseCases(TagRepositorio tags) {
        this.tags = tags;
    }

    @PreAuthorize("isAuthenticated()")
    public List<Tag> listar() {
        return tags.listarTodas();
    }

    @PreAuthorize("isAuthenticated()")
    public Optional<Tag> obter(UUID id) {
        return tags.porId(id);
    }

    @PreAuthorize(SO_GESTAO)
    @Transactional
    public Tag criar(String nome, String cor, String icone) {
        garantirNomeLivre(nome, null);
        return tags.salvar(Tag.nova(nome, cor, icone));
    }

    @PreAuthorize(SO_GESTAO)
    @Transactional
    public Optional<Tag> atualizar(UUID id, String nome, String cor, String icone) {
        return tags.porId(id).map(existente -> {
            garantirNomeLivre(nome, id);
            return tags.salvar(existente.com(nome, cor, icone));
        });
    }

    @PreAuthorize(SO_GESTAO)
    @Transactional
    public boolean remover(UUID id) {
        if (tags.porId(id).isEmpty()) {
            return false;
        }
        tags.remover(id);
        return true;
    }

    /**
     * O banco ja tem UNIQUE em {@code nome}. A checagem aqui existe para responder 409 com uma
     * mensagem util em vez de deixar vazar uma violacao de constraint como erro 500.
     */
    private void garantirNomeLivre(String nome, UUID idParaIgnorar) {
        if (tags.existeComNome(nome, idParaIgnorar)) {
            throw new NomeDeTagEmUsoException(nome);
        }
    }
}
