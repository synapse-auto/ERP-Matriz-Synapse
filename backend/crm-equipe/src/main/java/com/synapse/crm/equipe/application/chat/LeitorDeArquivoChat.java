package com.synapse.crm.equipe.application.chat;

/** Leitura binária protegida, separada da autorização e da transação de histórico. */
public interface LeitorDeArquivoChat {
    byte[] baixar(String referencia);
}
