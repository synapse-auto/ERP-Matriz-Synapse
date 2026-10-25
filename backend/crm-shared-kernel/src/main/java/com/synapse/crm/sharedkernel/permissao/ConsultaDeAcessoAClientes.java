package com.synapse.crm.sharedkernel.permissao;

import java.util.UUID;

/** Acesso humano a dados de clientes, independente de Chat Interno, presenca ou participacao. */
public interface ConsultaDeAcessoAClientes {
    String CAPACIDADE = "clientes.acessar";

    boolean permitido(UUID usuarioId);
}
