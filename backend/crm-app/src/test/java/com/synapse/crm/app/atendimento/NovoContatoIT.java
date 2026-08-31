package com.synapse.crm.app.atendimento;

import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_ANA;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_BRUNO;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.SENHA_ATENDENTE;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.test.context.TestPropertySource;

import com.synapse.crm.app.PostgresIT;
import com.synapse.crm.app.canal.CanalFake;
import com.synapse.crm.app.seguranca.ApoioAutenticacao;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
@TestPropertySource(properties = "synapse.canal.whatsapp.provedor=fake")
class NovoContatoIT extends PostgresIT {

    private static final String PREFIXO = "E-novo-contato-";

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private CanalFake canal;

    private UUID idAna;
    private UUID idBruno;

    @BeforeEach
    void preparar() {
        limpar();
        idAna = idDoUsuario(EMAIL_ANA);
        idBruno = idDoUsuario(EMAIL_BRUNO);
        canal.limpar();
    }

    @AfterEach
    void limpar() {
        canal.limpar();
        jdbc.update(
                """
                DELETE FROM mensagem WHERE atendimento_id IN (
                    SELECT a.id FROM atendimento a JOIN lead l ON l.id = a.lead_id
                     WHERE l.nome LIKE ?)
                """,
                PREFIXO + "%");
        jdbc.update(
                "DELETE FROM atendimento WHERE lead_id IN (SELECT id FROM lead WHERE nome LIKE ?)",
                PREFIXO + "%");
        jdbc.update("DELETE FROM lead WHERE nome LIKE ?", PREFIXO + "%");
    }

    @Test
    @DisplayName("cria lead e atendimento sem enviar quando nao ha primeira mensagem")
    void novoContato_semMensagem_criaLeadEAtendimento() {
        String telefone = telefoneNacional();

        var resposta = iniciarComo(EMAIL_ANA, Map.of("nome", PREFIXO + "Maria", "telefone", telefone));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getBody()).contains("\"leadCriado\":true");
        UUID leadId = extrairUuid(resposta.getBody(), "leadId");
        assertThat(jdbc.queryForObject("SELECT atendente_responsavel_id FROM lead WHERE id = ?", UUID.class, leadId))
                .isEqualTo(idAna);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM mensagem WHERE atendimento_id IN "
                        + "(SELECT id FROM atendimento WHERE lead_id = ?)", Integer.class, leadId))
                .isZero();
        assertThat(canal.enviados()).isEmpty();
    }

    @Test
    @DisplayName("reusa o proprio lead e o atendimento aberto")
    void novoContato_telefoneProprio_reusaLead() {
        String telefone = telefoneNacional();
        var primeiro = iniciarComo(EMAIL_ANA, Map.of("nome", PREFIXO + "Maria", "telefone", telefone));
        assertThat(primeiro.getStatusCode()).isEqualTo(HttpStatus.OK);
        String leadId = extrair(primeiro.getBody(), "leadId");

        var segundo = iniciarComo(EMAIL_ANA, Map.of("nome", PREFIXO + "Maria de novo", "telefone", telefone));

        assertThat(segundo.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(segundo.getBody()).contains("\"leadCriado\":false").contains(leadId);
    }

    @Test
    @DisplayName("atendente puxa por telefone o lead em IA oculto em Todos sem criar duplicado")
    void novoContato_leadDaIaOcultoEmTodos_reaproveitaEAssumeParaSi() {
        String telefone = telefoneNacional();
        String canonico = "55" + telefone.replaceAll("\\D", "");
        UUID leadIa = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO lead (id, nome, telefone, atendente_responsavel_id, status_basico)"
                        + " VALUES (?, ?, ?, NULL, 'IA'::status_basico_lead)",
                leadIa,
                PREFIXO + "potencial",
                canonico);
        UUID atendimentoIa = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO atendimento (id, lead_id, status, iniciado_em) "
                        + "VALUES (?, ?, 'EM_IA'::status_atendimento, now())",
                atendimentoIa,
                leadIa);

        String token = ApoioAutenticacao.login(http, EMAIL_ANA, SENHA_ATENDENTE).accessToken();
        assertThat(listarAtendimentos(token, "TODOS")).doesNotContain(atendimentoIa.toString());
        assertThat(listarAtendimentos(token, "POTENCIAIS")).contains(atendimentoIa.toString());

        var resposta = iniciarComo(EMAIL_ANA, Map.of("nome", PREFIXO + "potencial", "telefone", telefone));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getBody()).contains("\"leadCriado\":false").contains(leadIa.toString());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM lead WHERE telefone = ?", Integer.class, canonico))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT atendente_responsavel_id FROM lead WHERE id = ?", UUID.class, leadIa))
                .isEqualTo(idAna);
    }

    @Test
    @DisplayName("telefone de colega responde 404 sem vazar a existencia")
    void novoContato_telefoneDeColega_retorna404() {
        String telefone = telefoneNacional();
        String canonico = "55" + telefone.replaceAll("\\D", "");
        jdbc.update(
                "INSERT INTO lead (id, nome, telefone, atendente_responsavel_id, status_basico)"
                        + " VALUES (?, ?, ?, ?, 'EM_ATENDIMENTO'::status_basico_lead)",
                UUID.randomUUID(),
                PREFIXO + "da ana",
                canonico,
                idAna);

        var resposta = iniciarComo(EMAIL_BRUNO, Map.of("nome", PREFIXO + "tentativa", "telefone", telefone));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(resposta.getBody()).contains("nao encontrado");
        assertThat(resposta.getBody()).doesNotContain(idAna.toString());
        assertThat(jdbc.queryForObject(
                        "SELECT atendente_responsavel_id FROM lead WHERE telefone = ?", UUID.class, canonico))
                .isEqualTo(idAna);
        assertThat(idBruno).isNotEqualTo(idAna);
    }

    @Test
    @DisplayName("texto livre fora da janela em contato novo responde 422 sem gravar lead")
    void novoContato_textoLivreForaDaJanela_retorna422SemLead() {
        canal.fecharJanela();
        String telefone = telefoneNacional();
        String canonico = "55" + telefone.replaceAll("\\D", "");

        var resposta = iniciarComo(
                EMAIL_ANA,
                Map.of("nome", PREFIXO + "fora", "telefone", telefone, "primeiraMensagem", "ola, tudo bem?"));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(resposta.getBody()).contains("Fora da janela de 24 horas");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM lead WHERE telefone = ?", Integer.class, canonico))
                .isZero();
        assertThat(canal.enviados()).isEmpty();
    }

    @Test
    @DisplayName("template fora da janela e aceito e cria o lead")
    void novoContato_templateForaDaJanela_criaEEnvia() {
        canal.fecharJanela();
        String telefone = telefoneNacional();

        var resposta = iniciarComo(
                EMAIL_ANA,
                Map.of(
                        "nome",
                        PREFIXO + "template",
                        "telefone",
                        telefone,
                        "template",
                        Map.of("nome", "hello_world", "idioma", "pt_BR", "parametros", List.of("Maria"))));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getBody()).contains("\"leadCriado\":true");
        assertThat(extrair(resposta.getBody(), "mensagemId")).isNotBlank();
    }

    private ResponseEntity<String> iniciarComo(String email, Map<String, ?> corpo) {
        String token = ApoioAutenticacao.login(http, email, SENHA_ATENDENTE).accessToken();
        HttpHeaders cabecalhos = new HttpHeaders();
        cabecalhos.setBearerAuth(token);
        cabecalhos.setContentType(MediaType.APPLICATION_JSON);
        return http.exchange(
                "/api/v1/atendimentos/novo-contato",
                HttpMethod.POST,
                new HttpEntity<>(corpo, cabecalhos),
                String.class);
    }

    private String listarAtendimentos(String token, String visao) {
        return ApoioAutenticacao.comToken(
                        http,
                        token,
                        HttpMethod.GET,
                        "/api/v1/atendimentos?visao=" + visao,
                        String.class)
                .getBody();
    }

    private static UUID extrairUuid(String json, String campo) {
        return UUID.fromString(extrair(json, campo));
    }

    private static String extrair(String json, String campo) {
        return json.replaceAll(".*\"" + campo + "\":\"([^\"]+)\".*", "$1");
    }

    private UUID idDoUsuario(String email) {
        return jdbc.queryForObject("SELECT id FROM usuario WHERE email = ?", UUID.class, email);
    }

    /** 11 digitos nacionais unicos — o CRM prefixa o DDI 55. */
    private static String telefoneNacional() {
        long n = Math.abs(UUID.randomUUID().getLeastSignificantBits()) % 100_000_000L;
        return String.format("839%08d", n);
    }
}
