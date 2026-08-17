package com.synapse.crm.equipe.infrastructure.seguranca;

import java.time.Duration;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import com.synapse.crm.equipe.application.autenticacao.PoliticaDeSenha;
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
        Duration validadeRefreshToken,
        String tokenInterno,
        String frontendOrigem,
        PoliticaSenha politicaSenha)
        implements PoliticaDeSessao, PoliticaDeSenha {

    @Override
    public Duration validadeDoRefreshToken() {
        return validadeRefreshToken;
    }

    @Override
    public int tamanhoMinimo() {
        return politicaSenha.tamanhoMinimo();
    }

    /** E29: sem numero fixo no codigo — o default mora no application.yml, nao aqui. */
    public record PoliticaSenha(int tamanhoMinimo) {}

    /**
     * O segredo de {@code X-Synapse-Token} (E07 §1), que autentica a Automacao em
     * {@code /internal/v1}. Sem default, como {@code jwtSegredo}: um valor de conveniencia aqui
     * seria uma porta destrancada em todo filho que esquecesse de configurar o proprio.
     */
    public boolean temTokenInterno() {
        return tokenInterno != null && !tokenInterno.isBlank();
    }
}
