package com.synapse.crm.atendimento.application.encaminhamentodochat;

import java.util.UUID;

/** Um atendimento aberto que o usuário pode escolher como destino. O telefone sai sempre mascarado. */
public record DestinoDoEncaminhamento(
        UUID atendimentoId, String clienteNome, String telefoneMascarado, String statusAtendimento, String responsavelNome) {}
