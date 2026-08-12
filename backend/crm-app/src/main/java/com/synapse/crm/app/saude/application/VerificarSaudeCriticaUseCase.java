package com.synapse.crm.app.saude.application;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;

/** Consolida os seis sinais do caminho de mensagens sem contaminar o liveness. */
@Service
public class VerificarSaudeCriticaUseCase {

    private final List<VerificadorDeComponente> verificadores;
    private final Clock relogio;

    public VerificarSaudeCriticaUseCase(
            List<VerificadorDeComponente> verificadores, Clock relogio) {
        this.verificadores = verificadores.stream()
                .sorted(Comparator.comparingInt(VerificarSaudeCriticaUseCase::ordem))
                .toList();
        this.relogio = relogio;
    }

    public ResultadoDaSaudeCritica executar() {
        List<ComponenteDaSaude> componentes = new ArrayList<>();
        boolean bancoDisponivel = true;

        for (VerificadorDeComponente verificador : verificadores) {
            if (!bancoDisponivel
                    && verificador.dependenciaDoBancoChat()
                            == DependenciaDoBancoChat.DEPENDE_DO_BANCO) {
                componentes.add(ComponenteDaSaude.naoVerificado(
                        verificador.nome(),
                        verificador.severidadeDaFalha(),
                        "nao verificado porque banco-chat esta indisponivel"));
                continue;
            }
            ComponenteDaSaude componente = verificador.verificar();
            componentes.add(componente);
            if (verificador.dependenciaDoBancoChat()
                    == DependenciaDoBancoChat.VERIFICA_O_BANCO) {
                bancoDisponivel = !componente.falhou();
            }
        }

        SeveridadeSaude severidade = componentes.stream()
                .filter(ComponenteDaSaude::falhou)
                .map(ComponenteDaSaude::severidade)
                .max(Comparator.naturalOrder())
                .orElse(SeveridadeSaude.NORMAL);
        EstadoDaSaude estado = switch (severidade) {
            case NORMAL -> EstadoDaSaude.UP;
            case DEGRADADO -> EstadoDaSaude.DEGRADED;
            case CRITICO -> EstadoDaSaude.DOWN;
        };
        return new ResultadoDaSaudeCritica(
                estado, severidade, Instant.now(relogio), componentes);
    }

    private static int ordem(VerificadorDeComponente verificador) {
        return switch (verificador.dependenciaDoBancoChat()) {
            case VERIFICA_O_BANCO -> 0;
            case INDEPENDENTE -> 1;
            case DEPENDE_DO_BANCO -> 2;
        };
    }
}
