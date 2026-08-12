package com.synapse.crm.app.saude.application;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Aplica debounce, severidade, destino e janela antes de publicar um alerta. */
public class MonitorarSaudeCriticaUseCase {

    private final VerificarSaudeCriticaUseCase verificar;
    private final PublicadorDeAlertaDeSaude publicador;
    private final HorarioComercialDoCliente horarioCliente;
    private final PoliticaDeMonitoramento politica;

    private String assinaturaAtual;
    private int falhasConsecutivas;
    private String assinaturaJaAlertada;

    public MonitorarSaudeCriticaUseCase(
            VerificarSaudeCriticaUseCase verificar,
            PublicadorDeAlertaDeSaude publicador,
            HorarioComercialDoCliente horarioCliente,
            PoliticaDeMonitoramento politica) {
        this.verificar = verificar;
        this.publicador = publicador;
        this.horarioCliente = horarioCliente;
        this.politica = politica;
    }

    public synchronized ResultadoDaSaudeCritica executar() {
        ResultadoDaSaudeCritica resultado = verificar.executar();
        // Atualiza o cache da janela enquanto o banco esta saudavel; numa queda posterior ainda
        // sabemos se o cliente deve ser avisado sem depender do proprio componente que falhou.
        boolean clienteEmHorario = horarioCliente.permiteAvisarCliente(resultado.verificadoEm());
        if (resultado.status() == EstadoDaSaude.UP) {
            assinaturaAtual = null;
            assinaturaJaAlertada = null;
            falhasConsecutivas = 0;
            return resultado;
        }

        String assinatura = assinatura(resultado);
        if (assinatura.equals(assinaturaAtual)) {
            falhasConsecutivas++;
        } else {
            assinaturaAtual = assinatura;
            falhasConsecutivas = 1;
        }

        if (falhasConsecutivas >= politica.falhasConsecutivasParaAlertar()
                && !assinatura.equals(assinaturaJaAlertada)) {
            publicador.publicar(alerta(resultado, clienteEmHorario));
            assinaturaJaAlertada = assinatura;
        }
        return resultado;
    }

    private AlertaDeSaude alerta(
            ResultadoDaSaudeCritica resultado, boolean clienteEmHorario) {
        Set<DestinoDoAlerta> destinos = new LinkedHashSet<>();
        destinos.add(DestinoDoAlerta.SYNAPSE);
        if (resultado.severidade() == SeveridadeSaude.CRITICO
                && clienteEmHorario) {
            destinos.add(DestinoDoAlerta.CLIENTE);
        }
        List<String> componentes = resultado.componentesComFalha().stream()
                .map(ComponenteDaSaude::nome)
                .sorted()
                .toList();
        return new AlertaDeSaude(
                resultado.severidade(), resultado.verificadoEm(), destinos, componentes);
    }

    private static String assinatura(ResultadoDaSaudeCritica resultado) {
        return resultado.severidade()
                + ":"
                + resultado.componentesComFalha().stream()
                        .map(ComponenteDaSaude::nome)
                        .sorted()
                        .collect(Collectors.joining(","));
    }
}
