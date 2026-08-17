package com.synapse.crm.app.seguranca;

import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_ANA;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_GESTOR;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.SENHA_ATENDENTE;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.SENHA_GESTOR;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.synapse.crm.app.PostgresIT;

/**
 * E29: primeiro acesso e troca de senha.
 *
 * <p>O teste central desta suite e o negativo do bloco 1: prova, pelo endpoint real com o token
 * real, que o bloqueio de senha provisoria e do servidor — nao um redirecionamento de tela que um
 * cliente HTTP direto ignoraria.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
class PrimeiroAcessoETrocaDeSenhaIT extends PostgresIT {

    private static final String NOVA_SENHA_VALIDA = "senha-nova-e29-valida";

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("usuario com senha provisoria autentica, mas GET /api/v1/atendimentos recebe 403")
    void senhaProvisoria_login_ok_masEndpointComumRecusa403() {
        String token = criarProvisorioELogar().accessToken();

        var resposta = comBearer(token, HttpMethod.GET, "/api/v1/atendimentos?visao=ATIVOS", null);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(resposta.getBody()).doesNotContain(NOVA_SENHA_VALIDA).doesNotContain("$2a$");
    }

    @Test
    @DisplayName("mesmo com senha provisoria, POST /api/v1/auth/senha e /logout continuam acessiveis")
    void senhaProvisoria_trocarSenhaELogout_permitidos() {
        var sessao = criarProvisorioELogar();

        var troca = comBearer(
                sessao.accessToken(),
                HttpMethod.POST,
                "/api/v1/auth/senha",
                Map.of("senhaAtual", SENHA_ATENDENTE, "novaSenha", NOVA_SENHA_VALIDA));
        assertThat(troca.getStatusCode()).isEqualTo(HttpStatus.OK);

        var logout = http.postForEntity(
                "/api/v1/auth/logout", Map.of("refreshToken", sessao.refreshToken()), Void.class);
        assertThat(logout.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    @DisplayName("apos trocar, o token novo alcanca endpoints normais")
    void aposTrocar_tokenNovoAlcancaEndpointsNormalmente() throws Exception {
        var sessao = criarProvisorioELogar();

        var troca = comBearer(
                sessao.accessToken(),
                HttpMethod.POST,
                "/api/v1/auth/senha",
                Map.of("senhaAtual", SENHA_ATENDENTE, "novaSenha", NOVA_SENHA_VALIDA));
        assertThat(troca.getStatusCode()).isEqualTo(HttpStatus.OK);
        String tokenNovo = campo(troca.getBody(), "accessToken");

        var resposta = comBearer(tokenNovo, HttpMethod.GET, "/api/v1/atendimentos?visao=ATIVOS", null);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("senha atual errada e recusada com 400")
    void trocarSenha_senhaAtualErrada_recusa400() {
        var sessao = criarProvisorioELogar();

        var resposta = comBearer(
                sessao.accessToken(),
                HttpMethod.POST,
                "/api/v1/auth/senha",
                Map.of("senhaAtual", "senha-errada", "novaSenha", NOVA_SENHA_VALIDA));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resposta.getBody()).containsIgnoringCase("atual");
    }

    @Test
    @DisplayName("nova senha igual a atual e recusada com 400")
    void trocarSenha_novaIgualAAtual_recusa400() {
        var sessao = criarProvisorioELogar();

        var resposta = comBearer(
                sessao.accessToken(),
                HttpMethod.POST,
                "/api/v1/auth/senha",
                Map.of("senhaAtual", SENHA_ATENDENTE, "novaSenha", SENHA_ATENDENTE));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("nova senha fora da politica e recusada com a regra na mensagem")
    void trocarSenha_foraDaPolitica_recusaComRegraNaMensagem() {
        var sessao = criarProvisorioELogar();

        var resposta = comBearer(
                sessao.accessToken(),
                HttpMethod.POST,
                "/api/v1/auth/senha",
                Map.of("senhaAtual", SENHA_ATENDENTE, "novaSenha", "curta"));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resposta.getBody()).contains("8");
    }

    @Test
    @DisplayName("trocar a senha revoga as demais sessoes; a sessao nova (devolvida na troca) continua valida")
    void trocarSenha_revogaAsDemaisSessoes() throws Exception {
        var provisorio = criarProvisorio();
        var sessaoA = ApoioAutenticacao.login(http, provisorio.email(), SENHA_ATENDENTE);
        var sessaoB = ApoioAutenticacao.login(http, provisorio.email(), SENHA_ATENDENTE);

        var troca = comBearer(
                sessaoA.accessToken(),
                HttpMethod.POST,
                "/api/v1/auth/senha",
                Map.of("senhaAtual", SENHA_ATENDENTE, "novaSenha", NOVA_SENHA_VALIDA));
        assertThat(troca.getStatusCode()).isEqualTo(HttpStatus.OK);
        String refreshNovo = campo(troca.getBody(), "refreshToken");

        assertThat(ApoioAutenticacao.refresh(http, sessaoA.refreshToken()).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(ApoioAutenticacao.refresh(http, sessaoB.refreshToken()).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(ApoioAutenticacao.refresh(http, refreshNovo).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("atendente nao consegue gerar senha provisoria de outro usuario")
    void resetPorGestor_atendente_recebe403() {
        UUID bruno = jdbc.queryForObject(
                "SELECT id FROM usuario WHERE email = ?", UUID.class, ApoioAutenticacao.EMAIL_BRUNO);
        String token = ApoioAutenticacao.login(http, EMAIL_ANA, SENHA_ATENDENTE).accessToken();

        var resposta = comBearer(
                token, HttpMethod.POST, "/api/v1/usuarios/" + bruno + "/senha-provisoria", null);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("gestor gera senha provisoria: alvo cai em NULL e perde as sessoes")
    void resetPorGestor_ok_alvoCaiNoPrimeiroAcessoEPerdeSessoes() throws Exception {
        var alvo = criarProvisorio();
        // O alvo comeca com senha_alterada_em preenchido, como um usuario ja onboardado.
        jdbc.update("UPDATE usuario SET senha_alterada_em = now() WHERE id = ?", alvo.id());
        var sessaoAntiga = ApoioAutenticacao.login(http, alvo.email(), SENHA_ATENDENTE);

        String tokenGestor = ApoioAutenticacao.login(http, EMAIL_GESTOR, SENHA_GESTOR).accessToken();
        var resposta = comBearer(
                tokenGestor, HttpMethod.POST, "/api/v1/usuarios/" + alvo.id() + "/senha-provisoria", null);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        String senhaGerada = campo(resposta.getBody(), "senha");
        assertThat(senhaGerada).isNotBlank();

        assertThat(jdbc.queryForObject(
                        "SELECT senha_alterada_em FROM usuario WHERE id = ?", java.sql.Timestamp.class, alvo.id()))
                .isNull();
        assertThat(ApoioAutenticacao.refresh(http, sessaoAntiga.refreshToken()).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);

        // A senha nova gerada realmente autentica, e nasce provisoria de novo.
        var novoLogin = ApoioAutenticacao.tentarLogin(http, alvo.email(), senhaGerada);
        assertThat(novoLogin.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("gerar senha provisoria nao deixa dados_antes/dados_depois em audit_log")
    void resetPorGestor_naoVazaSenhaNaAuditoria() {
        var alvo = criarProvisorio();
        jdbc.update("UPDATE usuario SET senha_alterada_em = now() WHERE id = ?", alvo.id());
        String tokenGestor = ApoioAutenticacao.login(http, EMAIL_GESTOR, SENHA_GESTOR).accessToken();

        var resposta = comBearer(
                tokenGestor, HttpMethod.POST, "/api/v1/usuarios/" + alvo.id() + "/senha-provisoria", null);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);

        var linhas = jdbc.queryForList(
                "SELECT dados_antes, dados_depois FROM audit_log "
                        + "WHERE acao = 'GERAR_SENHA_PROVISORIA' AND entidade_id = ? "
                        + "ORDER BY criado_em DESC LIMIT 1",
                alvo.id());
        assertThat(linhas).hasSize(1);
        assertThat(linhas.get(0).get("dados_antes")).isNull();
        assertThat(linhas.get(0).get("dados_depois")).isNull();
    }

    // --- apoio ---------------------------------------------------------------

    private record ProvisorioCriado(UUID id, String email) {}

    /** Mesmo hash BCrypt de 'atendente123' usado no seed (ver AutenticacaoIT). */
    private ProvisorioCriado criarProvisorio() {
        UUID id = UUID.randomUUID();
        String email = "provisorio-" + id + "@dev.local";
        jdbc.update(
                """
                INSERT INTO usuario (id, nome, email, senha_hash, papel, ativo)
                VALUES (?, 'Provisorio E29', ?,
                        '$2a$10$3RQQjf4jsEx11gmaTnUkkeky8yurpHKdl5UPkuWlVe7tsphWGmj42',
                        'ATENDENTE', TRUE)
                """,
                id,
                email);
        return new ProvisorioCriado(id, email);
    }

    private ApoioAutenticacao.Tokens criarProvisorioELogar() {
        ProvisorioCriado criado = criarProvisorio();
        return ApoioAutenticacao.login(http, criado.email(), SENHA_ATENDENTE);
    }

    private ResponseEntity<String> comBearer(String token, HttpMethod metodo, String url, Object corpo) {
        HttpHeaders cabecalhos = new HttpHeaders();
        if (token != null) {
            cabecalhos.setBearerAuth(token);
        }
        cabecalhos.setContentType(MediaType.APPLICATION_JSON);
        return http.exchange(url, metodo, new HttpEntity<>(corpo, cabecalhos), String.class);
    }

    private String campo(String corpoJson, String nome) throws Exception {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        return mapper.readTree(corpoJson).path(nome).asText();
    }
}
