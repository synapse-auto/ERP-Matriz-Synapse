package com.synapse.crm.atendimento.infrastructure.particao;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Parametros da manutencao de particoes de {@code mensagem}.
 *
 * <p>Nada aqui e constante no codigo: quanto criar a frente, quanto exigir no boot e quando rodar o
 * job sao decisoes operacionais que mudam por instalacao.
 *
 * @param mesesAFrente quantos meses alem do corrente o job cria a cada execucao
 * @param mesesMinimos quantos meses alem do corrente precisam existir para a aplicacao subir
 * @param verificarNaInicializacao se falso, o boot nao confere a janela (usado em teste)
 * @param cron quando o job mensal de criacao roda
 * @param cronAlertaDefault quando o job diario confere a particao DEFAULT
 */
@Validated
@ConfigurationProperties(prefix = "synapse.atendimento.particao")
public record ParticaoMensagemProperties(
        @Min(1) int mesesAFrente,
        @Min(0) int mesesMinimos,
        boolean verificarNaInicializacao,
        @NotBlank String cron,
        @NotBlank String cronAlertaDefault) {}
