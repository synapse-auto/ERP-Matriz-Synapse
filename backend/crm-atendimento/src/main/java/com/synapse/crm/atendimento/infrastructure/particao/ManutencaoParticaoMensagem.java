package com.synapse.crm.atendimento.infrastructure.particao;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Mantem a janela de particoes mensais de {@code mensagem}.
 *
 * <p>Sao tres responsabilidades sobre o mesmo risco. Um INSERT numa faixa sem particao falha, e
 * falhar ali significa parar de enviar e receber mensagem — o que a regra de precedencia do produto
 * proibe. Entao: o job mensal cria a janela com meses de antecedencia; a verificacao de
 * inicializacao recusa subir se a janela minima nao existir; e as duas se apoiam nas mesmas funcoes
 * SQL, idempotentes, criadas na migration V5.
 *
 * <p>Criar varios meses de uma vez, em vez de so o proximo, e o que torna o mecanismo tolerante a
 * falha: o job pode deixar de rodar algumas vezes seguidas sem que a operacao pare.
 *
 * <p>O {@link JdbcTemplate} injetado e o autoconfigurado, construido sobre o DataSource
 * {@code @Primary} — o pool geral. Manutencao de schema nao consome a reserva do chat.
 */
@Component
@DependsOnDatabaseInitialization
public class ManutencaoParticaoMensagem implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(ManutencaoParticaoMensagem.class);

    private static final String SQL_GARANTIR = "SELECT garantir_particoes_mensagem(?)";
    private static final String SQL_FALTANTES =
            "SELECT nome_esperado FROM particoes_mensagem_faltantes(?)";

    private final JdbcTemplate jdbc;
    private final ParticaoMensagemProperties propriedades;

    public ManutencaoParticaoMensagem(JdbcTemplate jdbc, ParticaoMensagemProperties propriedades) {
        this.jdbc = jdbc;
        this.propriedades = propriedades;
    }

    /** Confere a janela minima antes de a aplicacao aceitar trafego. */
    @Override
    public void afterPropertiesSet() {
        if (!propriedades.verificarNaInicializacao()) {
            log.warn(
                    "Verificacao de particao de mensagem desligada. A aplicacao vai subir mesmo sem "
                            + "particao para o mes corrente.");
            return;
        }
        verificarJanelaMinima(propriedades.mesesMinimos());
        log.info(
                "Particoes de mensagem conferidas: janela minima de {} mes(es) alem do corrente esta completa.",
                propriedades.mesesMinimos());
    }

    /** Job mensal: recompoe a janela inteira, nao apenas o proximo mes. */
    @Scheduled(cron = "${synapse.atendimento.particao.cron}")
    public void garantirJanelaAgendada() {
        int meses = propriedades.mesesAFrente();
        List<String> antes = particoesFaltantes(meses);
        garantirJanela(meses);
        List<String> depois = particoesFaltantes(meses);

        if (depois.isEmpty()) {
            log.info(
                    "Janela de particoes de mensagem garantida ate {} mes(es) a frente. Criadas agora: {}.",
                    meses,
                    antes.isEmpty() ? "nenhuma" : String.join(", ", antes));
        } else {
            // Nao lancamos: derrubar o agendador nao consertaria nada, e o alerta
            // e mais util que a excecao. A proxima execucao tenta de novo.
            log.error(
                    "Janela de particoes de mensagem INCOMPLETA apos a manutencao. Ainda faltam: {}. "
                            + "Escritas em mensagem vao falhar quando alcancarem esses meses.",
                    String.join(", ", depois));
        }
    }

    /**
     * Cria as particoes que faltam do mes corrente ate {@code mesesAFrente}.
     *
     * @return quantos meses da janela foram conferidos
     */
    public int garantirJanela(int mesesAFrente) {
        Integer total = jdbc.queryForObject(SQL_GARANTIR, Integer.class, mesesAFrente);
        return total == null ? 0 : total;
    }

    /** Meses da janela que ainda nao tem particao. Lista vazia significa seguro para escrever. */
    public List<String> particoesFaltantes(int mesesAFrente) {
        return jdbc.queryForList(SQL_FALTANTES, String.class, mesesAFrente);
    }

    /**
     * @throws ParticaoMensagemAusenteException se faltar particao na janela pedida
     */
    public void verificarJanelaMinima(int mesesAFrente) {
        List<String> faltantes = particoesFaltantes(mesesAFrente);
        if (!faltantes.isEmpty()) {
            throw new ParticaoMensagemAusenteException(faltantes);
        }
    }
}
