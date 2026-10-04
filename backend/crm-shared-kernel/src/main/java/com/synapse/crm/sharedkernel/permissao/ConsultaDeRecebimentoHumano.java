package com.synapse.crm.sharedkernel.permissao;

import java.util.UUID;

import com.synapse.crm.sharedkernel.identidade.PapelUsuario;

/** Politica do destinatario, calculada pela Gestao; nao substitui autorizacao da origem nem RLS. */
public interface ConsultaDeRecebimentoHumano {
    boolean permitido(UUID destinatario, PapelUsuario origem);
}
