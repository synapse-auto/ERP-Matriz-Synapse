package com.synapse.crm.equipe.infrastructure.seguranca;

import java.time.Clock;
import java.util.List;

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
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.synapse.crm.equipe.application.autenticacao.CodificadorDeSenha;
import com.synapse.crm.sharedkernel.identidade.ClaimsJwt;

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
    SecurityFilterChain filtros(
            HttpSecurity http,
            SynapseTokenAuthenticationFilter filtroSynapseToken,
            RequisicaoContextSpring filtroRequisicaoContext,
            SenhaProvisoriaFilter filtroSenhaProvisoria,
            CorsConfigurationSource corsConfigurationSource)
            throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // addFilterBefore so aceita uma classe de filtro PADRAO do Spring Security como
                // referencia — nao da para posicionar um filtro custom relativo a outro filtro
                // custom (SynapseTokenAuthenticationFilter nao tem ordem registrada). Por isso os
                // dois apontam para a mesma classe padrao, e a ordem de chamada abaixo e que decide
                // a posicao relativa entre eles: o IP precisa estar disponivel antes da
                // autenticacao, entao o filtro de contexto e adicionado primeiro.
                .addFilterBefore(filtroRequisicaoContext, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(filtroSynapseToken, UsernamePasswordAuthenticationFilter.class)
                // E29: precisa vir DEPOIS que o resource server OAuth2 autentica o JWT — so ali a
                // claim senha_provisoria esta disponivel no SecurityContextHolder para este filtro
                // ler. BearerTokenAuthenticationFilter e a classe padrao que o oauth2ResourceServer
                // registra abaixo; referencia-la aqui so fixa a posicao relativa, nao exige que o
                // bean dela seja injetado.
                .addFilterAfter(filtroSenhaProvisoria, BearerTokenAuthenticationFilter.class)
                .authorizeHttpRequests(rotas -> rotas
                        // E29: trocar a propria senha exige um Bearer token valido — sem isto a
                        // rota cairia no permitAll de /api/v1/auth/** logo abaixo, e qualquer
                        // requisicao (sem token nenhum) poderia tentar trocar a senha de qualquer
                        // um. A regra precisa vir ANTES da mais generica: a primeira que casar
                        // decide.
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/senha")
                        .authenticated()
                        .requestMatchers("/api/v1/auth/**")
                        .permitAll()
                        // E10: tema e textos precisam estar disponiveis ANTES do login — a
                        // propria tela de login e themeable, e "zero cor/texto literal em
                        // componente" nao tem excecao para telas pre-autenticacao. Nenhum dos
                        // dois carrega dado sensivel: e paleta de cor e rotulo de UI.
                        .requestMatchers(HttpMethod.GET, "/api/v1/config/tema", "/api/v1/config/textos")
                        .permitAll()
                        // O contrato da Automacao (E07): sem JWT de usuario, autenticado por
                        // X-Synapse-Token no filtro registrado acima. "hasRole" aqui e
                        // cinto-e-suspensorio — o filtro ja recusa com 401 antes de chegar
                        // aqui quando o token nao bate; isto so garante que, mesmo que o
                        // filtro um dia deixasse passar sem autenticar, a rota continuaria
                        // fechada por padrao.
                        .requestMatchers("/internal/v1/**")
                        .hasRole("SERVICO")
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
                        // O handshake do WebSocket e uma requisicao HTTP comum antes de
                        // virar upgrade, e chega aqui sem o cabecalho Authorization — a
                        // API nativa de WebSocket do navegador nao consegue defini-lo. A
                        // autenticacao desta rota e o proprio JWT, lido da query string
                        // pelo interceptador de handshake ANTES de aceitar a conexao; se
                        // o token faltar ou for invalido, o handshake e recusado ali, nao
                        // aqui. "permitAll" e "esta cadeia nao autentica esta rota", igual
                        // ao webhook.
                        .requestMatchers("/ws/**")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/health/**", "/info")
                        .permitAll()
                        // Documentacao gerada, nao segredo (E07 §2): a forma das rotas,
                        // nao dado de cliente. Publica para poder virar artefato do
                        // release sem autenticar o pipeline de CI contra a API.
                        .requestMatchers(
                                HttpMethod.GET,
                                "/v3/api-docs",
                                "/v3/api-docs.yaml",
                                "/v3/api-docs/**",
                                "/swagger-ui/**")
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
        autoridades.setAuthoritiesClaimName(ClaimsJwt.PAPEL);
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

    /**
     * Libera a origem do frontend (porta diferente da API em dev) para chamadas diretas do
     * browser com {@code Authorization: Bearer ...}. Sem credenciais de cookie aqui — o cookie
     * httpOnly de refresh (E10) e trocado com o proprio Next.js, nunca chega ate aqui.
     */
    @Bean
    CorsConfigurationSource corsConfigurationSource(SegurancaProperties propriedades) {
        CorsConfiguration configuracao = new CorsConfiguration();
        configuracao.setAllowedOrigins(List.of(propriedades.frontendOrigem()));
        configuracao.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuracao.setAllowedHeaders(List.of("Authorization", "Content-Type"));

        UrlBasedCorsConfigurationSource fonte = new UrlBasedCorsConfigurationSource();
        fonte.registerCorsConfiguration("/**", configuracao);
        return fonte;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    CodificadorDeSenha codificadorDeSenha(PasswordEncoder encoder) {
        // O adaptador BCrypt da porta de senha. Assinatura null-safe: senha ausente
        // e senha errada seguem o mesmo caminho.
        return new CodificadorDeSenha() {
            @Override
            public boolean confere(String senhaEmTexto, String hashArmazenado) {
                return senhaEmTexto != null && encoder.matches(senhaEmTexto, hashArmazenado);
            }

            @Override
            public String codificar(String senhaEmTexto) {
                return encoder.encode(senhaEmTexto);
            }
        };
    }

    /** Relogio injetavel: os testes de expiracao precisam controlar o tempo. */
    @Bean
    Clock relogio() {
        return Clock.systemUTC();
    }
}
