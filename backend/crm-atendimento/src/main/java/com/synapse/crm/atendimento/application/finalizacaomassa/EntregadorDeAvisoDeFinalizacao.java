package com.synapse.crm.atendimento.application.finalizacaomassa;

import com.synapse.crm.atendimento.application.finalizacaomassa.AvisosDeFinalizacaoEmMassaRepositorio.AvisoPendente;

/** Porta de saida do aviso (hoje: backplane Redis e fila pessoal de WebSocket). Lanca se nao conseguiu entregar. */
public interface EntregadorDeAvisoDeFinalizacao {

    void entregar(AvisoPendente aviso);
}
