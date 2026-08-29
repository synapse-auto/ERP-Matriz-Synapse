package com.synapse.crm.atendimento.application.referencia;

import java.util.UUID;

import com.synapse.crm.atendimento.domain.mensagem.Mensagem;

/** Mensagem de origem com o minimo para montar a citacao, sem telefone. */
public record OrigemDeMensagem(Mensagem mensagem, UUID leadId, String leadNome, String remetenteNome) {}
