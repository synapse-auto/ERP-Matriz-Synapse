package com.synapse.crm.core.domain.etapa;

import java.util.Objects;
import java.util.UUID;

/**
 * Etapa do funil de atendimento.
 *
 * <p>E tabela de configuracao, nao enum: a operacao muda o funil sem deploy, e cada filho da Base
 * PAI tem o seu. Transformar isso em enum no codigo seria hardcode de regra de negocio do cliente.
 *
 * <p>{@code ordem} e a posicao no Kanban e tem indice unico no banco: duas etapas na mesma posicao
 * deixariam a tela nao-determinista.
 */
public record EtapaAtendimento(UUID id, String nome, short ordem, String corVisual) {

    public EtapaAtendimento {
        Objects.requireNonNull(id, "id da etapa e obrigatorio");
        Objects.requireNonNull(nome, "nome da etapa e obrigatorio");
        if (nome.isBlank()) {
            throw new IllegalArgumentException("nome da etapa nao pode ser vazio");
        }
        if (ordem < 1) {
            throw new IllegalArgumentException("ordem da etapa comeca em 1");
        }
    }

    public static EtapaAtendimento nova(String nome, short ordem, String corVisual) {
        return new EtapaAtendimento(UUID.randomUUID(), nome, ordem, corVisual);
    }

    public EtapaAtendimento com(String nome, short ordem, String corVisual) {
        return new EtapaAtendimento(id, nome, ordem, corVisual);
    }
}
