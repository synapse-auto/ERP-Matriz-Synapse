package com.synapse.crm.relatorios.application.cancelamentos;

import java.util.Optional;
import java.util.UUID;

/** Leitura pontual autorizada, sem correlacao por nome/telefone nem copia historica. */
public interface IdentificacaoDoLeadCancelamento {
    Optional<DadosDeIdentificacaoDoCancelamento> obter(UUID leadId);

    record DadosDeIdentificacaoDoCancelamento(String cpf, String telefone) {
        @Override public String toString() { return "Dados[identificacao protegida]"; }
    }
}
