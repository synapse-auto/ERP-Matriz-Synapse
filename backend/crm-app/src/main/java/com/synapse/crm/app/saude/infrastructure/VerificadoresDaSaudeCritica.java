package com.synapse.crm.app.saude.infrastructure;

import java.sql.Connection;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.synapse.crm.app.saude.application.ComponenteDaSaude;
import com.synapse.crm.app.saude.application.DependenciaDoBancoChat;
import com.synapse.crm.app.saude.application.SeveridadeSaude;
import com.synapse.crm.app.saude.application.VerificadorDeComponente;
import com.synapse.crm.atendimento.domain.canal.CanalGateway;
import com.synapse.crm.atendimento.infrastructure.outbox.SaudeDoConsumidorDaOutbox;
import com.synapse.crm.atendimento.infrastructure.particao.ManutencaoParticaoMensagem;
import com.synapse.crm.atendimento.infrastructure.tempo_real.SaudeDoWebSocket;
import com.synapse.crm.sharedkernel.persistencia.Pools;

abstract class VerificadorBase implements VerificadorDeComponente {

    @Override
    public SeveridadeSaude severidadeDaFalha() {
        return SeveridadeSaude.CRITICO;
    }

    ComponenteDaSaude falha(RuntimeException e) {
        return ComponenteDaSaude.down(
                nome(), severidadeDaFalha(), "falha de " + e.getClass().getSimpleName());
    }
}

@Component
class VerificadorBancoChat extends VerificadorBase {

    private final DataSource chat;

    VerificadorBancoChat(@Qualifier(Pools.CHAT_DATA_SOURCE) DataSource chat) {
        this.chat = chat;
    }

    @Override
    public String nome() {
        return "banco-chat";
    }

    @Override
    public DependenciaDoBancoChat dependenciaDoBancoChat() {
        return DependenciaDoBancoChat.VERIFICA_O_BANCO;
    }

    @Override
    public ComponenteDaSaude verificar() {
        try (Connection conexao = chat.getConnection()) {
            if (!conexao.isValid(1)) {
                return ComponenteDaSaude.down(
                        nome(), severidadeDaFalha(), "chatDataSource recusou a validacao");
            }
            return ComponenteDaSaude.up(nome(), "chatDataSource acessivel");
        } catch (Exception e) {
            return ComponenteDaSaude.down(
                    nome(), severidadeDaFalha(), "chatDataSource indisponivel");
        }
    }
}

@Component
class VerificadorFilaOutbox extends VerificadorBase {

    private final SaudeDoConsumidorDaOutbox saude;
    private final JdbcTemplate chat;
    private final SaudeCriticaProperties propriedades;
    private final Clock relogio;

    VerificadorFilaOutbox(
            SaudeDoConsumidorDaOutbox saude,
            @Qualifier(Pools.CHAT_DATA_SOURCE) DataSource chatDataSource,
            SaudeCriticaProperties propriedades,
            Clock relogio) {
        this.saude = saude;
        this.chat = new JdbcTemplate(chatDataSource);
        this.propriedades = propriedades;
        this.relogio = relogio;
    }

    @Override
    public String nome() {
        return "fila-outbox";
    }

    @Override
    public DependenciaDoBancoChat dependenciaDoBancoChat() {
        return DependenciaDoBancoChat.DEPENDE_DO_BANCO;
    }

    @Override
    public ComponenteDaSaude verificar() {
        try {
            chat.queryForObject("SELECT count(*) FROM outbox_evento", Long.class);
            Instant ultimo = saude.ultimoConsumoBemSucedido();
            Instant limite = Instant.now(relogio).minus(propriedades.filaSemConsumoMaximo());
            if (ultimo.isBefore(limite)) {
                return ComponenteDaSaude.down(
                        nome(),
                        severidadeDaFalha(),
                        ultimo.equals(Instant.EPOCH)
                                ? "consumidor ainda nao registrou rodada bem-sucedida"
                                : "consumidor sem rodada bem-sucedida dentro do limite");
            }
            return ComponenteDaSaude.up(nome(), "fila acessivel e consumidor executando");
        } catch (RuntimeException e) {
            return falha(e);
        }
    }
}

@Component
class VerificadorCanal extends VerificadorBase {

    private static final String SQL_CREDENCIAL_ATIVA =
            """
            SELECT count(*)
              FROM canal c
              JOIN canal_credencial cc ON cc.canal_id = c.id
             WHERE c.ativo
               AND cc.ativo
               AND cc.vigente_desde <= now()
               AND (cc.vigente_ate IS NULL OR cc.vigente_ate > now())
               AND COALESCE(trim(cc.identificador_externo), '') <> ''
               AND COALESCE(trim(cc.token_ref), '') <> ''
            """;

    private final JdbcTemplate chat;
    private final CanalGateway canal;

    VerificadorCanal(
            @Qualifier(Pools.CHAT_DATA_SOURCE) DataSource chatDataSource, CanalGateway canal) {
        this.chat = new JdbcTemplate(chatDataSource);
        this.canal = canal;
    }

    @Override
    public String nome() {
        return "canal";
    }

    @Override
    public DependenciaDoBancoChat dependenciaDoBancoChat() {
        return DependenciaDoBancoChat.DEPENDE_DO_BANCO;
    }

    @Override
    public ComponenteDaSaude verificar() {
        try {
            Long credenciais = chat.queryForObject(SQL_CREDENCIAL_ATIVA, Long.class);
            if (credenciais == null || credenciais < 1) {
                return ComponenteDaSaude.down(
                        nome(), severidadeDaFalha(), "nenhuma credencial ativa e valida");
            }
            CanalGateway.AutenticacaoDoCanal autenticacao = canal.verificarAutenticacao();
            return autenticacao.autenticada()
                    ? ComponenteDaSaude.up(nome(), autenticacao.detalhe())
                    : ComponenteDaSaude.down(
                            nome(), severidadeDaFalha(), autenticacao.detalhe());
        } catch (RuntimeException e) {
            return falha(e);
        }
    }
}

@Component
class VerificadorWebSocket extends VerificadorBase {

    private final SaudeDoWebSocket saude;

    VerificadorWebSocket(SaudeDoWebSocket saude) {
        this.saude = saude;
    }

    @Override
    public String nome() {
        return "websocket";
    }

    @Override
    public DependenciaDoBancoChat dependenciaDoBancoChat() {
        return DependenciaDoBancoChat.INDEPENDENTE;
    }

    @Override
    public ComponenteDaSaude verificar() {
        return saude.disponivel()
                ? ComponenteDaSaude.up(nome(), "broker STOMP aceitando conexoes")
                : ComponenteDaSaude.down(
                        nome(), severidadeDaFalha(), "broker STOMP indisponivel");
    }
}

@Component
class VerificadorParticoes extends VerificadorBase {

    private final ManutencaoParticaoMensagem particoes;

    VerificadorParticoes(ManutencaoParticaoMensagem particoes) {
        this.particoes = particoes;
    }

    @Override
    public String nome() {
        return "particoes-mensagem";
    }

    @Override
    public DependenciaDoBancoChat dependenciaDoBancoChat() {
        return DependenciaDoBancoChat.DEPENDE_DO_BANCO;
    }

    @Override
    public ComponenteDaSaude verificar() {
        try {
            List<String> faltantes = particoes.particoesFaltantes(1);
            return faltantes.isEmpty()
                    ? ComponenteDaSaude.up(nome(), "mes corrente e proximo cobertos")
                    : ComponenteDaSaude.down(
                            nome(),
                            severidadeDaFalha(),
                            "particoes ausentes: " + String.join(", ", faltantes));
        } catch (RuntimeException e) {
            return falha(e);
        }
    }
}

@Component
class VerificadorAcumuloDaOutbox extends VerificadorBase {

    private static final String SQL_PENDENTES_ANTIGAS =
            """
            SELECT count(*)
              FROM outbox_evento
             WHERE publicado_em IS NULL
               AND esgotado_em IS NULL
               AND criado_em <= ?
            """;

    private final JdbcTemplate chat;
    private final SaudeCriticaProperties propriedades;
    private final Clock relogio;

    VerificadorAcumuloDaOutbox(
            @Qualifier(Pools.CHAT_DATA_SOURCE) DataSource chatDataSource,
            SaudeCriticaProperties propriedades,
            Clock relogio) {
        this.chat = new JdbcTemplate(chatDataSource);
        this.propriedades = propriedades;
        this.relogio = relogio;
    }

    @Override
    public String nome() {
        return "acumulo-outbox";
    }

    @Override
    public DependenciaDoBancoChat dependenciaDoBancoChat() {
        return DependenciaDoBancoChat.DEPENDE_DO_BANCO;
    }

    @Override
    public SeveridadeSaude severidadeDaFalha() {
        return SeveridadeSaude.DEGRADADO;
    }

    @Override
    public ComponenteDaSaude verificar() {
        try {
            Instant corte = Instant.now(relogio).minus(propriedades.outboxIdadeMaxima());
            Long pendentes = chat.queryForObject(
                    SQL_PENDENTES_ANTIGAS, Long.class, Timestamp.from(corte));
            long total = pendentes == null ? 0 : pendentes;
            if (total > propriedades.outboxPendentesMaximo()) {
                return ComponenteDaSaude.down(
                        nome(),
                        severidadeDaFalha(),
                        total + " pendencia(s) antigas acima do limite operacional");
            }
            return ComponenteDaSaude.up(nome(), "sem acumulo anormal");
        } catch (RuntimeException e) {
            return falha(e);
        }
    }
}
