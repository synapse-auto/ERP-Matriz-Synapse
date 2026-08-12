package com.synapse.crm.app.saude.application;

/** Porta de um diagnostico operacional; cada dependencia tem seu adaptador. */
public interface VerificadorDeComponente {

    String nome();

    DependenciaDoBancoChat dependenciaDoBancoChat();

    SeveridadeSaude severidadeDaFalha();

    ComponenteDaSaude verificar();
}
