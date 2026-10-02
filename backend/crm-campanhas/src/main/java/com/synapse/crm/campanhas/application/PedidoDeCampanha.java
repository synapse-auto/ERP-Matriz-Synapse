package com.synapse.crm.campanhas.application;

import java.time.Instant;

import com.synapse.crm.campanhas.domain.FiltroDePublico;
import com.synapse.crm.campanhas.domain.JanelaDeEnvio;
import com.synapse.crm.campanhas.domain.MapeamentoDeVariaveis;
import com.synapse.crm.campanhas.domain.PlanoDeLimite;

/**
 * O que o assistente "Nova campanha" envia para criar ou atualizar um rascunho. Campos nulos assumem o padrao
 * da instancia: limite diario padrao, janela das 9h as 18h em dias uteis, 20 envios por minuto.
 *
 * @param agendadaPara nulo = comecar assim que for iniciada
 */
public record PedidoDeCampanha(
        String nome,
        String templateNome,
        String templateIdioma,
        MapeamentoDeVariaveis mapeamento,
        FiltroDePublico filtro,
        Integer limiteDiario,
        JanelaDeEnvio janela,
        Integer ritmoPorMinuto,
        PlanoDeLimite.Rampa rampa,
        Instant agendadaPara) {}
