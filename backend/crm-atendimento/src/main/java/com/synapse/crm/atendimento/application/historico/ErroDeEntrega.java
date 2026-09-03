package com.synapse.crm.atendimento.application.historico;

/** Motivo informado pelo provedor para uma mensagem cuja entrega falhou. */
public record ErroDeEntrega(Integer codigo, String titulo) {}
