package com.synapse.crm.campanhas.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Porta de {@code contato_optout}: quem pediu para nao receber campanhas, respeitado por todas elas. */
public interface OptOutRepositorio {

    /** Idempotente: registrar quem ja esta registrado nao muda a data original. */
    void registrar(UUID leadId, Origem origem, String motivo, UUID registradoPor);

    /** Devolve se havia opt-out a remover. */
    boolean remover(UUID leadId);

    boolean existe(UUID leadId);

    long contar();

    List<Registro> listar(int pagina, int tamanho);

    enum Origem {
        MANUAL,
        RESPOSTA_DO_CLIENTE,
        IMPORTACAO
    }

    record Registro(UUID leadId, String nome, String telefone, Instant desde, Origem origem, String motivo) {}
}
