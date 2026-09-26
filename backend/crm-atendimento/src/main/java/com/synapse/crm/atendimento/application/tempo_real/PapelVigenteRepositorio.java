package com.synapse.crm.atendimento.application.tempo_real;

import java.util.Optional;
import java.util.UUID;

import com.synapse.crm.sharedkernel.identidade.PapelUsuario;

/**
 * Papel ATUAL do usuario, se ativo. A assinatura de WebSocket captura o papel do JWT no momento do
 * SUBSCRIBE; sem esta leitura, um subgestor rebaixado continuaria recebendo conversas de toda a base
 * enquanto a conexao durasse, porque a revalidacao usava o papel capturado.
 */
public interface PapelVigenteRepositorio {

    Optional<PapelUsuario> papelSeAtivo(UUID usuarioId);
}
