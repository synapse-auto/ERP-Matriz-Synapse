package com.synapse.crm.app.observabilidade;

import java.util.Optional;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import com.synapse.crm.atendimento.application.painel.EstadoDoPool;
import com.synapse.crm.atendimento.application.painel.MedidorDoPoolDoChat;
import com.synapse.crm.sharedkernel.persistencia.Pools;

/** Implementa a porta do caso de uso da listagem lendo o pool Hikari {@code synapse-chat} (E225, PR 5). */
@Component
class MedidorDoPoolDoChatHikari implements MedidorDoPoolDoChat {

    private final DataSource chat;

    MedidorDoPoolDoChatHikari(@Qualifier(Pools.CHAT_DATA_SOURCE) DataSource chat) {
        this.chat = chat;
    }

    @Override
    public Optional<EstadoDoPool> estadoAtual() {
        return LeituraDePoolHikari.ler(chat);
    }
}
