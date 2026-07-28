package com.synapse.crm.equipe.infrastructure.seguranca;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

import com.synapse.crm.equipe.application.autenticacao.GeradorDeAccessToken;
import com.synapse.crm.sharedkernel.identidade.ClaimsJwt;
import com.synapse.crm.sharedkernel.identidade.UsuarioAutenticado;

/** Emite o access token como JWT assinado em HMAC-SHA256. */
@Component
class JwtGeradorDeAccessToken implements GeradorDeAccessToken {

    private final JwtEncoder encoder;
    private final SegurancaProperties propriedades;
    private final Clock relogio;

    JwtGeradorDeAccessToken(JwtEncoder encoder, SegurancaProperties propriedades, Clock relogio) {
        this.encoder = encoder;
        this.propriedades = propriedades;
        this.relogio = relogio;
    }

    @Override
    public String gerar(UsuarioAutenticado usuario) {
        Instant agora = relogio.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(usuario.id().toString())
                .claim(ClaimsJwt.PAPEL, usuario.papel().name())
                .issuedAt(agora)
                .expiresAt(agora.plus(validade()))
                .build();
        // O cabecalho precisa declarar HS256 explicitamente. Sem isso o encoder
        // assume RS256, procura uma chave assimetrica que nao existe e falha com
        // "Failed to select a JWK signing key".
        JwsHeader cabecalho = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(cabecalho, claims)).getTokenValue();
    }

    @Override
    public Duration validade() {
        return propriedades.validadeAccessToken();
    }
}
