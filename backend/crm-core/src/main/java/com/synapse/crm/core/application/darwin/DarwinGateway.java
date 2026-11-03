package com.synapse.crm.core.application.darwin;

import java.util.UUID;

import com.synapse.crm.core.domain.lead.Lead;

/** Porta de consulta e comprovante de prévia; nenhuma escrita na Darwin. */
public interface DarwinGateway {
    boolean habilitado();
    void exigirDisponivel();
    DadosDarwin.Resultado consultar(String cpf);
    String comprovar(UUID usuarioId, Lead anterior, DadosDarwin.Candidato candidato);
    DadosDarwin.Recibo verificar(String confirmacao);
}
