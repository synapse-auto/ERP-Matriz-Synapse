package com.synapse.crm.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A regra do nono digito existe em SQL uma vez so.
 *
 * <p>A V50 aplica a regra no deploy; a simulacao em {@code docker/provisionamento} mostra o que a
 * V50 faria antes de ele acontecer. Se as duas descreverem regras diferentes, a simulacao aprova uma
 * fusao e o deploy executa outra — e o erro so aparece depois de leads terem sido apagados.
 *
 * <p>Por isso o bloco entre os marcadores e copiado literalmente, e este teste compara os dois
 * textos caractere a caractere. Editar um e esquecer o outro reprova o build.
 *
 * <p>A terceira implementacao da mesma regra e {@code TelefoneCanonico}, em Java, que nao pode ser
 * comparada por texto: quem prova que ela concorda com o SQL e {@code TelefoneCanonicoParidadeIT}.
 */
class RegraDoNonoDigitoCompartilhadaTest {

    private static final String ABERTURA = "-- >>> regra-do-nono-digito >>>";
    private static final String FECHAMENTO = "-- <<< regra-do-nono-digito <<<";

    private static final String MIGRATION =
            "backend/crm-app/src/main/resources/db/migration/V50__telefone_nono_digito.sql";
    private static final String SIMULACAO = "docker/provisionamento/simular-fusao-nono-digito.sql";

    @Test
    @DisplayName("a migration e a simulacao carregam o mesmo texto da regra")
    void migracaoESimulacao_carregamOMesmoTexto() throws IOException {
        Path raiz = raizDoRepositorio();

        String naMigration = blocoDaRegra(raiz.resolve(MIGRATION));
        String naSimulacao = blocoDaRegra(raiz.resolve(SIMULACAO));

        assertThat(naSimulacao)
                .as(
                        "o bloco entre os marcadores em %s e %s precisa ser identico;"
                                + " copie o da migration",
                        MIGRATION, SIMULACAO)
                .isEqualTo(naMigration);
    }

    /** O bloco tem de conter as funcoes de fato — marcador vazio passaria no teste acima. */
    @Test
    @DisplayName("o bloco compartilhado contem as tres funcoes da regra")
    void bloco_contemAsFuncoes() throws IOException {
        String bloco = blocoDaRegra(raizDoRepositorio().resolve(MIGRATION));

        assertThat(bloco)
                .contains("CREATE OR REPLACE FUNCTION app_telefone_com_ddi(")
                .contains("CREATE OR REPLACE FUNCTION app_telefone_canonico(")
                .contains("CREATE OR REPLACE FUNCTION app_telefone_fora_da_regra(");
    }

    private static String blocoDaRegra(Path arquivo) throws IOException {
        String conteudo = Files.readString(arquivo, StandardCharsets.UTF_8);
        int inicio = conteudo.indexOf(ABERTURA);
        int fim = conteudo.indexOf(FECHAMENTO);
        assertThat(inicio).as("marcador de abertura ausente em %s", arquivo).isNotNegative();
        assertThat(fim).as("marcador de fechamento ausente em %s", arquivo).isGreaterThan(inicio);
        return conteudo.substring(inicio, fim + FECHAMENTO.length()).replace("\r\n", "\n");
    }

    /**
     * O teste roda com o diretorio do modulo como base, mas a simulacao vive fora de {@code backend}.
     * Subir ate achar o arquivo evita fixar quantos niveis sao — que muda quando o modulo muda de
     * lugar e transforma este teste em falha misteriosa.
     */
    private static Path raizDoRepositorio() {
        Path candidato = Path.of("").toAbsolutePath();
        while (candidato != null) {
            if (Files.exists(candidato.resolve(SIMULACAO))) {
                return candidato;
            }
            candidato = candidato.getParent();
        }
        throw new IllegalStateException(
                "nao encontrei a raiz do repositorio a partir de " + Path.of("").toAbsolutePath());
    }
}
