package com.synapse.crm.automacaoconfig.domain.regras;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class NomeDaRegraFollowUp {
    private NomeDaRegraFollowUp() {}
    public static String derivar(int minutos) {
        if (minutos <= 0) throw new RegraAutomacaoInvalidaException("O tempo deve ser maior que zero");
        if (minutos % 1440 == 0) {
            int dias = minutos / 1440;
            return dias + (dias == 1 ? " dia sem resposta" : " dias sem resposta");
        }
        BigDecimal horas = BigDecimal.valueOf(minutos).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP).stripTrailingZeros();
        return horas.toPlainString().replace('.', ',') + " horas sem resposta";
    }
}
