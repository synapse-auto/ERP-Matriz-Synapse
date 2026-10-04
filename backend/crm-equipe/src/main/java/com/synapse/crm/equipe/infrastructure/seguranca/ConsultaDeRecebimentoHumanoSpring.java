package com.synapse.crm.equipe.infrastructure.seguranca;

import java.util.Locale;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.synapse.crm.equipe.application.permissao.ResolvedorDePermissoesEfetivas;
import com.synapse.crm.equipe.domain.permissao.Capacidade;
import com.synapse.crm.sharedkernel.identidade.PapelUsuario;
import com.synapse.crm.sharedkernel.permissao.ConsultaDeRecebimentoHumano;

@Component
class ConsultaDeRecebimentoHumanoSpring implements ConsultaDeRecebimentoHumano {
    private final ResolvedorDePermissoesEfetivas resolvedor;

    ConsultaDeRecebimentoHumanoSpring(ResolvedorDePermissoesEfetivas resolvedor) {
        this.resolvedor = resolvedor;
    }

    @Override
    public boolean permitido(UUID destinatario, PapelUsuario origem) {
        Capacidade capacidade = Capacidade.porId("atendimentos.receber_de_" + origem.name().toLowerCase(Locale.ROOT))
                .orElseThrow();
        return resolvedor.de(destinatario)
                .filter(ResolvedorDePermissoesEfetivas.Resolvido::ativo)
                .filter(r -> r.papel() == PapelUsuario.OPERADOR)
                .map(r -> r.efetivas().permite(capacidade))
                .orElse(false);
    }
}
