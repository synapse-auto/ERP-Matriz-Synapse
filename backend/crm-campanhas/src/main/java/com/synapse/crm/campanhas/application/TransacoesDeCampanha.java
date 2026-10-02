package com.synapse.crm.campanhas.application;

import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.synapse.crm.sharedkernel.persistencia.Pools;

/**
 * Transacoes programaticas para os casos de uso que precisam chamar o provedor (rede) ANTES de gravar. Uma
 * chamada de rede dentro de {@code @Transactional} seguraria uma conexao do pool do chat enquanto a Meta
 * responde; aqui a rede fica fora, e so o trecho de banco abre transacao.
 *
 * <p>O contexto de RLS e aplicado no inicio de cada transacao pelo gerente, igual a {@code @Transactional}.
 */
@Component
public class TransacoesDeCampanha {

    private final TransactionTemplate chat;
    private final TransactionTemplate chatSomenteLeitura;
    private final TransactionTemplate geralSomenteLeitura;

    public TransacoesDeCampanha(
            @Qualifier(Pools.CHAT_TRANSACTION_MANAGER) PlatformTransactionManager gerenteDoChat,
            PlatformTransactionManager gerenteGeral) {
        this.chat = new TransactionTemplate(gerenteDoChat);
        this.chatSomenteLeitura = new TransactionTemplate(gerenteDoChat);
        this.chatSomenteLeitura.setReadOnly(true);
        this.geralSomenteLeitura = new TransactionTemplate(gerenteGeral);
        this.geralSomenteLeitura.setReadOnly(true);
    }

    public <T> T noChat(Supplier<T> acao) {
        return chat.execute(status -> acao.get());
    }

    public void noChatSemRetorno(Runnable acao) {
        chat.executeWithoutResult(status -> acao.run());
    }

    public <T> T noChatSomenteLeitura(Supplier<T> acao) {
        return chatSomenteLeitura.execute(status -> acao.get());
    }

    /** Leituras pesadas (previa do publico, exportacao): pool geral, para nao disputar com o chat. */
    public <T> T noGeralSomenteLeitura(Supplier<T> acao) {
        return geralSomenteLeitura.execute(status -> acao.get());
    }
}
