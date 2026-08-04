package com.synapse.crm.app.canal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.synapse.crm.atendimento.domain.canal.TradutorDeCanal;

/**
 * A metade de entrada do provedor falso.
 *
 * <p>Separada de {@link CanalFake} por uma razao concreta: uma classe so, implementando as duas
 * portas, faz o bean {@code @Primary} de {@code CanalGateway} ser tambem candidato a
 * {@code TradutorDeCanal} — e o contexto nao sobe, com "more than one primary bean". Vale como
 * lembrete de que as duas pontas do ACL sao independentes: um filho pode trocar so uma delas.
 *
 * <p>O formato lido aqui e proposital e visivelmente diferente do da Meta
 * ({@code {"id":..., "de":..., "texto":...}}). Se o codigo de producao dependesse do formato da Meta
 * em qualquer ponto fora do adaptador, estes testes quebrariam — que e a prova que interessa.
 */
@Component
public class TradutorFake implements TradutorDeCanal {

    public static final String TOKEN_DE_VERIFICACAO = "verify-token-fake";

    @Override
    public String provedor() {
        return CanalFake.PROVEDOR;
    }

    @Override
    public boolean tokenDeVerificacaoValido(String tokenRecebido) {
        return tokenRecebido != null
                && MessageDigest.isEqual(
                        TOKEN_DE_VERIFICACAO.getBytes(StandardCharsets.UTF_8),
                        tokenRecebido.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public boolean assinaturaValida(String payloadCru, String assinaturaRecebida) {
        return CanalFake.ASSINATURA_VALIDA.equals(assinaturaRecebida);
    }

    @Override
    public Optional<String> idExterno(String payloadCru) {
        return campo(payloadCru, "\"id\":");
    }

    @Override
    public Optional<MensagemRecebidaDoCanal> traduzir(String payloadCru) {
        Optional<String> id = idExterno(payloadCru);
        if (id.isEmpty()) {
            return Optional.empty();
        }
        String telefone = campo(payloadCru, "\"de\":").orElse("+5561999999999");
        String nome = campo(payloadCru, "\"nome\":").orElse(null);

        // "tipo" ausente ou "TEXTO": mensagem de texto, formato original desta classe.
        // Qualquer outro valor (E11b): midia, com os campos extras que o teste declarar.
        Optional<String> tipo = campo(payloadCru, "\"tipo\":");
        if (tipo.isEmpty() || "TEXTO".equals(tipo.get())) {
            return Optional.of(MensagemRecebidaDoCanal.texto(
                    id.get(), telefone, nome, campo(payloadCru, "\"texto\":").orElse(""), Instant.now()));
        }
        return Optional.of(new MensagemRecebidaDoCanal(
                id.get(),
                telefone,
                nome,
                null,
                tipo.get(),
                campo(payloadCru, "\"midiaId\":").orElse(null),
                campo(payloadCru, "\"mimetype\":").orElse(null),
                campo(payloadCru, "\"nomeArquivo\":").orElse(null),
                campo(payloadCru, "\"legenda\":").orElse(null),
                Instant.now()));
    }

    /**
     * Extrai {@code "chave": "valor"} tolerando espaco depois dos dois-pontos.
     *
     * <p>A tolerancia nao e capricho: o payload e guardado em coluna {@code JSONB}, e o PostgreSQL
     * <b>normaliza</b> o JSON ao gravar — reordena chaves e insere um espaco depois do
     * {@code :}. O texto que sai de {@code webhook_entrada} nao e byte a byte o que entrou pelo
     * webhook. Um parser ingenuo casa na requisicao e falha no reprocessamento, que foi exatamente o
     * que aconteceu aqui.
     */
    private static Optional<String> campo(String payload, String chave) {
        int inicio = payload.indexOf(chave);
        if (inicio < 0) {
            return Optional.empty();
        }
        int aspaDeAbertura = payload.indexOf('"', inicio + chave.length());
        if (aspaDeAbertura < 0) {
            return Optional.empty();
        }
        int fim = payload.indexOf('"', aspaDeAbertura + 1);
        return fim < 0 ? Optional.empty() : Optional.of(payload.substring(aspaDeAbertura + 1, fim));
    }
}
