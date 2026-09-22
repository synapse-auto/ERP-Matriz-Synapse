package com.synapse.crm.atendimento.infrastructure.tempo_real;

/** Snapshot sem dados pessoais das conexões STOMP mantidas por uma instância do backend. */
record MetricasDeSessoesWebSocket(int sessoesAtivas, int usuariosAutenticadosUnicos) {}
