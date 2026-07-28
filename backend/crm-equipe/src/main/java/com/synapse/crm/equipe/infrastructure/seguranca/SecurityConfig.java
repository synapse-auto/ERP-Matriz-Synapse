package com.synapse.crm.equipe.infrastructure.seguranca;

import java.time.Clock;

import javax.crypto.spec.SecretKeySpec;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

import com.synapse.crm.equipe.application.autenticacao.CodificadorDeSenha;

/**
 * Cadeia de filtros e emissao/validacao de JWT.
 *
 * <p>Stateless de ponta a ponta: sem sessao HTTP, sem nada em memoria de instancia. Qualquer no
 * atende qualquer requisicao, que e o que permite escalar horizontalmente.
 *
 * <p>Autorizacao por rota aqui e proposital e minima — so separa "aberto" de "precisa estar
 * autenticado". Quem pode executar cada operacao e declarado no proprio caso de uso, via
 * {@code @PreAuthorize}, porque um mapa central de rotas vive longe do codigo que protege e
 * envelhece sem ninguem notar.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain filtros(HttpSecurity http) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(rotas -> rotas
                        .requestMatchers("/api/v1/auth/**")
                        .permitAll()
                        // Sem isto, todo 400/404/500 vira 401 vazio: o container
                        // encaminha o erro para /error, esse encaminhamento passa
                        // pela cadeia de novo e cai em anyRequest().authenticated().
                        // O sintoma engana — parece falha de autenticacao quando e
                        // payload invalido ou rota inexistente.
                        .requestMatchers("/error")
                        .permitAll()
                        // O provedor de canal nao tem como apresentar JWT. A autenticacao
                        // desta rota e a assinatura HMAC do corpo, conferida no controller
                        // ANTES de qualquer processamento — e sem segredo configurado o
                        // verificador recusa tudo. "permitAll" aqui e "a cadeia de filtros
                        // nao autentica", nao "qualquer um passa".
                        .requestMatchers("/webhook/**")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/health/**", "/info")
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(conversor())))
                .build();
    }

    /**
     * Converte o claim {@code papel} em {@code ROLE_<PAPEL>}, que e o formato que
     * {@code hasAnyRole(...)} espera nos casos de uso.
     */
    private JwtAuthenticationConverter conversor() {
        JwtGrantedAuthoritiesConverter autoridades = new JwtGrantedAuthoritiesConverter();
        autoridades.setAuthoritiesClaimName(JwtGeradorDeAccessToken.CLAIM_PAPEL);
        autoridades.setAuthorityPrefix("ROLE_");

        JwtAuthenticationConverter conversor = new JwtAuthenticationConverter();
        conversor.setJwtGrantedAuthoritiesConverter(autoridades);
        return conversor;
    }

    @Bean
    JwtEncoder jwtEncoder(SegurancaProperties propriedades) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(chave(propriedades)));
    }

    @Bean
    JwtDecoder jwtDecoder(SegurancaProperties propriedades) {
        // Rejeita token expirado por padrao; nao afrouxamos a tolerancia de relogio.
        return NimbusJwtDecoder.withSecretKey(chave(propriedades))
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }

    private SecretKeySpec chave(SegurancaProperties propriedades) {
        return new SecretKeySpec(propriedades.jwtSegredo().getBytes(), "HmacSHA256");
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    CodificadorDeSenha codificadorDeSenha(PasswordEncoder encoder) {
        // O adaptador BCrypt da porta de senha. Assinatura null-safe: senha ausente
        // e senha errada seguem o mesmo caminho.
        return (senhaEmTexto, hashArmazenado) ->
                senhaEmTexto != null && encoder.matches(senhaEmTexto, hashArmazenado);
    }

    /** Relogio injetavel: os testes de expiracao precisam controlar o tempo. */
    @Bean
    Clock relogio() {
        return Clock.systemUTC();
    }
}
