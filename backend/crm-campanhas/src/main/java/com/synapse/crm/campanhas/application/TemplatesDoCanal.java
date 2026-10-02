package com.synapse.crm.campanhas.application;

import java.util.List;
import java.util.Optional;

import com.synapse.crm.atendimento.domain.canal.TemplateDoCanal;

/**
 * Templates do canal ativo, com cache curto. Falha do provedor vira {@link TemplatesIndisponiveisException}
 * e nunca "lista vazia": um template que some da resposta por erro de rede nao pode parecer um template
 * removido (e pausar a campanha por isso).
 */
public interface TemplatesDoCanal {

    /** Todos os templates da conta, qualquer status. */
    List<TemplateDoCanal> listar();

    /** O template pelo nome e idioma, ou vazio se a conta nao o tem mais. */
    Optional<TemplateDoCanal> buscar(String nome, String idioma);

    /** Forca a proxima consulta ao provedor (apos editar ou excluir um template, e nos testes). */
    default void descartarCache() {}

    class TemplatesIndisponiveisException extends RuntimeException {

        public TemplatesIndisponiveisException(String detalhe, Throwable causa) {
            super(detalhe, causa);
        }
    }
}
