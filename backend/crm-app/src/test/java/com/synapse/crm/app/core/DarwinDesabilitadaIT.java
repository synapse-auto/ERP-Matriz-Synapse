package com.synapse.crm.app.core;

import static com.synapse.crm.app.seguranca.ApoioAutenticacao.*;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;

import com.synapse.crm.app.PostgresIT;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
class DarwinDesabilitadaIT extends PostgresIT {
    @Autowired TestRestTemplate http;
    @Test void padraoDesligadoRecusaConsultaEAplicacaoMesmoComPermissao() {
        String token = login(http, EMAIL_ANA, SENHA_ATENDENTE).accessToken();
        var headers = new HttpHeaders(); headers.setBearerAuth(token); headers.setContentType(MediaType.APPLICATION_JSON);
        var disponibilidade = http.exchange("/api/v1/leads/integracoes/darwin",HttpMethod.GET,new HttpEntity<>(headers),String.class);
        assertThat(disponibilidade.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(disponibilidade.getBody()).isEqualTo("{\"habilitada\":false}");
        for (String acao : java.util.List.of("consultas","aplicacoes")) {
            var resposta = http.exchange("/api/v1/leads/"+UUID.randomUUID()+"/darwin/"+acao,HttpMethod.POST,
                    new HttpEntity<>(Map.of("confirmacao","invalida","nome",true),headers),String.class);
            assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(resposta.getBody()).contains("DARWIN_DESABILITADA");
        }
    }
}
