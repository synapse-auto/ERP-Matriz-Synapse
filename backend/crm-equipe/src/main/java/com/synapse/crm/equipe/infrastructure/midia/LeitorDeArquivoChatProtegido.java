package com.synapse.crm.equipe.infrastructure.midia;

import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.stereotype.Component;

import com.synapse.crm.equipe.application.chat.LeitorDeArquivoChat;
import com.synapse.crm.sharedkernel.midia.ArmazenamentoDeMidia;

/** O storage nunca ocupa uma conexão SQL enquanto entrega o arquivo. */
@Component
class LeitorDeArquivoChatProtegido implements LeitorDeArquivoChat {
    private final ArmazenamentoDeMidia armazenamento;

    LeitorDeArquivoChatProtegido(ArmazenamentoDeMidia armazenamento) {
        this.armazenamento = armazenamento;
    }

    @Override
    @Bulkhead(name = "chat-interno-download")
    @CircuitBreaker(name = "chat-interno-download")
    public byte[] baixar(String referencia) {
        return armazenamento.baixar(referencia);
    }
}
