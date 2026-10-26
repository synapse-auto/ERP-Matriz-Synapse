package com.synapse.crm.atendimento.infrastructure.canal;

import java.time.Duration;
import java.util.UUID;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

/** Configuracao opcional de saida. Nao autentica callbacks nem habilita entrada. */
@ConfigurationProperties("synapse.canal.z-api")
public record ZApiProperties(
        boolean habilitado, String instanciaId, String token, String clientToken, boolean enviarClientToken,
        UUID credencialId, Duration timeout, int limiteRespostaBytes, int concorrencia) {

    @ConstructorBinding
    public ZApiProperties { }

    /** Mantem o contrato anterior para testes e composicoes que usam o modo protegido da conta. */
    public ZApiProperties(boolean habilitado, String instanciaId, String token, String clientToken,
            UUID credencialId, Duration timeout, int limiteRespostaBytes, int concorrencia) {
        this(habilitado, instanciaId, token, clientToken, true, credencialId, timeout, limiteRespostaBytes, concorrencia);
    }

    public boolean configurada() {
        return habilitado && segmento(instanciaId) && segmento(token)
                && clientTokenConfiguradoCorretamente()
                && credencialId != null && timeout != null && timeout.isPositive()
                && limiteRespostaBytes > 0 && limiteRespostaBytes < Integer.MAX_VALUE
                && concorrencia > 0;
    }

    private boolean clientTokenConfiguradoCorretamente() {
        if (!enviarClientToken) return clientToken == null || clientToken.isBlank();
        return clientToken != null && !clientToken.isBlank()
                && clientToken.chars().noneMatch(c -> c <= 32 || c >= 127);
    }

    private static boolean segmento(String valor) {
        return valor != null && valor.matches("[A-Za-z0-9_-]+");
    }

    @Override
    public String toString() {
        return "ZApiProperties[habilitado=" + habilitado + ", segredos=REDACTED]";
    }
}
