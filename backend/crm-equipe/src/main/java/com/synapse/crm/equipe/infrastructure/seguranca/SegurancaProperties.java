package com.synapse.crm.equipe.infrastructure.seguranca;

import java.time.Duration;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import com.synapse.crm.equipe.application.autenticacao.PoliticaDeSessao;

/**
 * Configuracao de seguranca da instancia.
 *
 * <p>O segredo vem do ambiente e nao tem default: sem ele a aplicacao nao sobe. Um default de
 * desenvolvimento aqui e como um cadeado que vem com a chave — se vazar para producao por descuido,
 * qualquer um assina um token de administrador.
 *
 * <p>O tamanho minimo nao e capricho: HMAC-SHA256 exige chave de pelo menos 256 bits, e uma chave
 * curta reduz a seguranca ao tamanho dela.
 */
@Validated
@ConfigurationProperties(prefix = "synapse.seguranca")
public record SegurancaProperties(
        @NotBlank @Size(min = 32, message = "o segredo do JWT precisa de ao menos 32 caracteres") String jwtSegredo,
        Duration validadeAccessToken,
        Duration validadeRefreshToken)
        implements PoliticaDeSessao {

    @Override
    public Duration validadeDoRefreshToken() {
        return validadeRefreshToken;
    }
}
