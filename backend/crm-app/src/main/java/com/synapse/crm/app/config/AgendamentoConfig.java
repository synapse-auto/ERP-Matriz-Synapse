package com.synapse.crm.app.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Liga o agendamento por {@code @Scheduled}.
 *
 * <p>Mora na aplicacao, e nao no modulo que agenda, porque ligar o agendador e decisao de
 * composicao: um modulo importado como biblioteca nao deveria decidir que a aplicacao inteira passa
 * a ter um scheduler.
 *
 * <p>O primeiro job a depender disso e a manutencao das particoes de {@code mensagem}.
 */
@Configuration
@EnableScheduling
public class AgendamentoConfig {}
