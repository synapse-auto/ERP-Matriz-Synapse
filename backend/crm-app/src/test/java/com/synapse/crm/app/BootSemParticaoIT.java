package com.synapse.crm.app;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;

import com.synapse.crm.atendimento.infrastructure.particao.ParticaoMensagemAusenteException;

/**
 * Prova que a aplicacao se recusa a subir sem a janela de particoes.
 *
 * <p>Os demais testes exercitam a verificacao chamando o metodo direto. Este sobe a aplicacao de
 * verdade, porque a promessa que interessa nao e "o metodo lanca excecao" e sim "o processo nao
 * fica de pe". Falhar no start e barulhento e acontece com alguem olhando; falhar no primeiro envio
 * das 9h acontece na frente do cliente.
 *
 * <p>A janela pedida e maior do que a que a V5 cria, entao a falha e determinista sem depender da
 * data em que o teste roda nem de mexer no schema.
 */
class BootSemParticaoIT extends PostgresIT {

    private static final int JANELA_MAIOR_QUE_A_CRIADA = 60;

    @Test
    @DisplayName("a aplicacao nao sobe quando falta particao de mensagem, e diz como corrigir")
    void boot_semParticaoNaJanelaMinima_falhaComMensagemClara() {
        var aplicacao = new SpringApplicationBuilder(SynapseCrmApplication.class);

        // Argumentos de linha de comando, e nao .properties(...): aquele metodo
        // alimenta as defaultProperties, que tem a MENOR precedencia de todas e
        // perderiam para os defaults de desenvolvimento do application.yml.
        String[] argumentos = {
            "--server.port=0",
            "--synapse.datasource.general.url=" + POSTGRES.getJdbcUrl(),
            "--synapse.datasource.general.username=" + POSTGRES.getUsername(),
            "--synapse.datasource.general.password=" + POSTGRES.getPassword(),
            "--synapse.datasource.chat.url=" + POSTGRES.getJdbcUrl(),
            "--synapse.datasource.chat.username=" + POSTGRES.getUsername(),
            "--synapse.datasource.chat.password=" + POSTGRES.getPassword(),
            "--synapse.atendimento.particao.meses-minimos=" + JANELA_MAIOR_QUE_A_CRIADA,
        };

        assertThatThrownBy(() -> aplicacao.run(argumentos))
                .rootCause()
                .isInstanceOf(ParticaoMensagemAusenteException.class)
                .hasMessageContaining("sem particao para")
                .hasMessageContaining("SELECT garantir_particoes_mensagem(3);");
    }
}
