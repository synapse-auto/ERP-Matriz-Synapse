package com.synapse.crm.app.observabilidade;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import java.util.List;

import javax.sql.DataSource;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.synapse.crm.app.PostgresIT;
import com.synapse.crm.atendimento.application.painel.EstadoDoPool;
import com.synapse.crm.atendimento.application.painel.MedidorDoPoolDoChat;
import com.synapse.crm.sharedkernel.persistencia.Pools;

/**
 * E225 (PR 5): com o contexto real, o medidor le o pool Hikari do chat (porta do caso de uso) e o observador loga os dois
 * pools com os numeros do MXBean. O {@code PostgresIT} configura os dois pools com maximo 2, entao o numero e conferivel.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
class ObservabilidadeDosPoolsIT extends PostgresIT {

    @Autowired
    private MedidorDoPoolDoChat medidor;

    @Autowired
    private ObservabilidadeDosPoolsDeConexao observador;

    @Autowired
    @Qualifier(Pools.CHAT_DATA_SOURCE) private DataSource chat;

    @Autowired
    @Qualifier(Pools.GENERAL_DATA_SOURCE) private DataSource geral;

    @Test
    void medidorDoChatLeOPoolRealComNomeEMaximoConfigurados() throws SQLException {
        usar(chat);

        EstadoDoPool estado = medidor.estadoAtual().orElseThrow();

        assertThat(estado.nome()).isEqualTo("synapse-chat");
        assertThat(estado.maximo()).isEqualTo(2);
        assertThat(estado.ativas()).isBetween(0, 2);
        assertThat(estado.total()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void registrarMetricasLogaOsDoisPoolsComOsNumerosDoMxBean() throws SQLException {
        usar(chat);
        usar(geral);
        Logger logger = (Logger) LoggerFactory.getLogger(ObservabilidadeDosPoolsDeConexao.class);
        ListAppender<ILoggingEvent> coletor = new ListAppender<>();
        coletor.start();
        logger.addAppender(coletor);
        try {
            observador.registrarMetricas();
        } finally {
            logger.detachAppender(coletor);
        }

        List<String> linhas = coletor.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
        assertThat(linhas).hasSize(2);
        assertThat(linhas).anySatisfy(linha -> assertThat(linha)
                .startsWith("[METRICA_POOL_CONEXOES] pool=synapse-chat")
                .contains("ativas=", "ociosas=", "esperando=", "total=", "maximo=2"));
        assertThat(linhas).anySatisfy(linha -> assertThat(linha)
                .startsWith("[METRICA_POOL_CONEXOES] pool=synapse-geral")
                .contains("maximo=2"));
    }

    /** Garante que o pool ja subiu: o Hikari so cria o pool na primeira conexao. */
    private static void usar(DataSource dataSource) throws SQLException {
        dataSource.getConnection().close();
    }
}
