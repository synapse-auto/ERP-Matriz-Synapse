package com.synapse.crm.atendimento.domain.mensagem;

/** Quem mandou. Espelha o ENUM {@code remetente_tipo} do banco. */
public enum RemetenteTipo {

    /** O cliente. Mensagem recebida. */
    LEAD,

    /** Um humano da equipe. E o unico tipo que dispara a RN-CRM-06. */
    ATENDENTE,

    /** O proprio CRM: aviso automatico, mensagem de sistema. */
    SISTEMA,

    /** A automacao. */
    IA;

    /** Se este remetente identifica uma pessoa da equipe — ou seja, exige {@code id}. */
    public boolean exigeIdentificacao() {
        return this == ATENDENTE;
    }
}
