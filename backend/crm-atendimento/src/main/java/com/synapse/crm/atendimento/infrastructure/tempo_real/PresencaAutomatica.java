package com.synapse.crm.atendimento.infrastructure.tempo_real;

import java.security.Principal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import com.synapse.crm.equipe.application.usuario.AlterarPresencaPeloSistemaUseCase;
import com.synapse.crm.equipe.application.usuario.ConfiguracaoDePresencaAutomatica;
import com.synapse.crm.equipe.domain.usuario.MudancaDePresenca;
import com.synapse.crm.equipe.domain.usuario.StatusPresenca;
import com.synapse.crm.sharedkernel.identidade.ContextoDeServico;

/**
 * Presenca automatica (E223, docs/63): ONLINE ao conectar, OFFLINE depois da tolerancia sem nenhuma conexao. Atras
 * da chave {@code presenca.automatica}, desligada por padrao; desligada, nada aqui escreve presenca.
 *
 * <p>Regras:
 *
 * <ul>
 *   <li>"Conectado" e pelo menos uma sessao STOMP do usuario no {@link SimpUserRegistry}; varias abas ou aparelhos
 *       contam como uma pessoa.
 *   <li>Primeira sessao depois de um periodo sem nenhuma: o usuario passa a ONLINE, mesmo que tenha saido OFFLINE ou
 *       AUSENTE. Na carencia de partida so OFFLINE vira ONLINE (um AUSENTE escolhido antes do deploy e preservado).
 *   <li>Durante a sessao a escolha manual vale: sessao extra nao muda nada. Reconectar dentro da tolerancia e a
 *       mesma sessao (o frontend troca o socket a cada renovacao do token): nao muda nem grava historico.
 *   <li>Sem nenhuma sessao por mais que a tolerancia, e passada a carencia de partida: OFFLINE com origem SISTEMA.
 *       Quem decide e a varredura, que compara o BANCO com o registro: assim quem sumiu durante um deploy tambem sai.
 *   <li>AUSENTE continua so manual.
 * </ul>
 *
 * <p>So e correta com UMA instancia do backend (o registro de sessoes e por instancia, docs/63 §4). Nada aqui roda
 * depois de {@link ContextClosedEvent}: na atualizacao {@code start-first} a instancia velha ve as sessoes cairem e
 * nao pode gravar OFFLINE para quem ja esta na nova.
 *
 * <p>Estado em memoria (quem ja tem sessao, desde quando nao tem) e tudo recomputavel: a varredura reconcilia com o
 * registro, entao um evento perdido atrasa o OFFLINE, nunca o deixa para sempre.
 */
@Component
public class PresencaAutomatica {

    static final String MARCADOR = "[PRESENCA_AUTOMATICA]";
    static final String MOTIVO_CONEXAO = "CONEXAO";
    static final String MOTIVO_DESCONEXAO = "DESCONEXAO";

    private static final Set<StatusPresenca> PROMOVIVEIS = EnumSet.of(StatusPresenca.OFFLINE, StatusPresenca.AUSENTE);
    private static final Set<StatusPresenca> PROMOVIVEIS_NA_CARENCIA = EnumSet.of(StatusPresenca.OFFLINE);
    private static final Set<StatusPresenca> REBAIXAVEIS = EnumSet.of(StatusPresenca.ONLINE, StatusPresenca.AUSENTE);
    private static final Set<StatusPresenca> SO_OFFLINE = EnumSet.of(StatusPresenca.OFFLINE);

    private static final Logger log = LoggerFactory.getLogger(PresencaAutomatica.class);

    private final SimpUserRegistry usuarios;
    private final AlterarPresencaPeloSistemaUseCase presenca;
    private final ConfiguracaoDePresencaAutomatica configuracao;
    private final PublicadorDeAvisoDePresenca avisos;
    private final Clock relogio;
    private final Duration tolerancia;
    private final Duration carencia;
    private final Duration validadeDaChave;

    private final Set<UUID> comSessao = new HashSet<>();
    private final Map<UUID, Instant> semSessaoDesde = new HashMap<>();
    private Instant partidaEm;
    private boolean encerrando;
    private boolean chaveLida;
    private Instant chaveLidaEm = Instant.MIN;

    PresencaAutomatica(
            @Lazy SimpUserRegistry usuarios,
            AlterarPresencaPeloSistemaUseCase presenca,
            ConfiguracaoDePresencaAutomatica configuracao,
            PublicadorDeAvisoDePresenca avisos,
            Clock relogio,
            PresencaAutomaticaProperties propriedades) {
        this.usuarios = usuarios;
        this.presenca = presenca;
        this.configuracao = configuracao;
        this.avisos = avisos;
        this.relogio = relogio;
        this.tolerancia = propriedades.tolerancia();
        this.carencia = propriedades.carencia();
        // A chave e lida em tempo de execucao; nos eventos de conexao basta um valor recente (o da ultima varredura).
        this.validadeDaChave = propriedades.intervaloVarredura();
        this.partidaEm = relogio.instant();
    }

    // --- entradas do Spring ---------------------------------------------------------------------------------

    @EventListener
    void aoConectar(SessionConnectedEvent evento) {
        idDoUsuario(evento.getUser()).ifPresent(this::usuarioConectou);
    }

    @EventListener
    void aoDesconectar(SessionDisconnectEvent evento) {
        idDoUsuario(evento.getUser()).ifPresent(id -> usuarioDesconectou(id, evento.getSessionId()));
    }

    /** O contador da carencia comeca quando os clientes conseguem se conectar, nao quando o bean e criado. */
    @EventListener
    synchronized void aoFicarPronto(ApplicationReadyEvent evento) {
        partidaEm = relogio.instant();
        // Os dois instantes (partida e fim da carencia) permitem conferir, no log de um deploy, quanto durou o intervalo
        // entre esta instancia ficar pronta e a anterior encerrar.
        log.info("{} instancia pronta (ApplicationReady): instante={} carenciaAte={} carencia={}",
                MARCADOR, partidaEm, partidaEm.plus(carencia), carencia);
    }

    /**
     * O Spring publica este evento antes de parar o ciclo de vida: so depois o SubProtocolWebSocketHandler fecha todas as
     * sessoes com GOING_AWAY. Marcar aqui, antes dos SessionDisconnectEvent, e o que impede o deploy de gravar OFFLINE.
     */
    @EventListener
    synchronized void aoEncerrar(ContextClosedEvent evento) {
        encerrando = true;
        log.info("{} encerramento iniciado: instante={}; as desconexoes a partir daqui nao gravam presenca",
                MARCADOR, relogio.instant());
    }

    /** Ponto de entrada do agendador; os ITs chamam {@link #varrer()} direto (o agendamento fica desligado em teste). */
    @Scheduled(fixedDelayString = "${synapse.tempo-real.presenca.intervalo-varredura:PT15S}")
    void agendar() {
        varrer();
    }

    // --- regras ------------------------------------------------------------------------------------------------

    synchronized void usuarioConectou(UUID usuarioId) {
        if (encerrando) {
            return;
        }
        boolean jaTinhaSessao = !comSessao.add(usuarioId);
        if (jaTinhaSessao) {
            return;
        }
        Instant agora = relogio.instant();
        Instant desde = semSessaoDesde.remove(usuarioId);
        if (desde != null && Duration.between(desde, agora).compareTo(tolerancia) < 0) {
            return;
        }
        if (!habilitada(agora)) {
            return;
        }
        mudar(usuarioId, StatusPresenca.ONLINE, MOTIVO_CONEXAO, emCarencia(agora) ? PROMOVIVEIS_NA_CARENCIA : PROMOVIVEIS);
    }

    synchronized void usuarioDesconectou(UUID usuarioId, String sessaoQueSaiu) {
        if (encerrando || temSessaoAlemDe(usuarioId, sessaoQueSaiu)) {
            return;
        }
        comSessao.remove(usuarioId);
        semSessaoDesde.putIfAbsent(usuarioId, relogio.instant());
    }

    /**
     * Compara o banco com o registro de sessoes e rebaixa quem passou da tolerancia (e da carencia de partida).
     * USO EM TESTE: a visibilidade publica existe so para os testes de integracao, que chamam a varredura como o
     * runtime (o agendamento fica desligado neles). Em producao quem a chama e o agendador desta classe.
     */
    public synchronized void varrer() {
        if (encerrando) {
            return;
        }
        Instant agora = relogio.instant();
        reconciliarComORegistro(agora);
        if (!habilitada(agora)) {
            return;
        }
        List<UUID> presentes;
        try {
            presentes = ContextoDeServico.buscarComo("presenca-automatica", presenca::idsComPresencaAtiva);
        } catch (RuntimeException erro) {
            log.warn("{} varredura sem acesso ao banco: tipoErro={}", MARCADOR, erro.getClass().getSimpleName());
            return;
        }
        for (UUID id : presentes) {
            if (temSessaoAlemDe(id, null)) {
                semSessaoDesde.remove(id);
                continue;
            }
            Instant desde = semSessaoDesde.computeIfAbsent(id, chave -> agora);
            if (emCarencia(agora) || Duration.between(desde, agora).compareTo(tolerancia) < 0) {
                continue;
            }
            rebaixar(id);
        }
        Set<UUID> conjunto = new HashSet<>(presentes);
        semSessaoDesde.entrySet().removeIf(entrada -> !conjunto.contains(entrada.getKey())
                && Duration.between(entrada.getValue(), agora).compareTo(tolerancia) >= 0);
    }

    /**
     * USO EM TESTE: nada em producao chama este metodo. Esquece o que se sabe de um usuario (se tem sessao, desde
     * quando nao tem) para os testes de integracao, que reaproveitam o mesmo bean entre cenarios: sem isto,
     * reconectar dentro da tolerancia do cenario anterior seria (corretamente) tratado como continuacao da sessao.
     */
    public synchronized void limparEstadoDe(UUID usuarioId) {
        comSessao.remove(usuarioId);
        semSessaoDesde.remove(usuarioId);
    }

    private void rebaixar(UUID usuarioId) {
        Optional<MudancaDePresenca> mudanca =
                mudar(usuarioId, StatusPresenca.OFFLINE, MOTIVO_DESCONEXAO, REBAIXAVEIS);
        semSessaoDesde.remove(usuarioId);
        // O usuario pode ter reconectado entre a leitura do registro e a gravacao: nesse caso desfaz na hora.
        if (mudanca.filter(MudancaDePresenca::mudou).isPresent() && temSessaoAlemDe(usuarioId, null)) {
            comSessao.add(usuarioId);
            mudar(usuarioId, StatusPresenca.ONLINE, MOTIVO_CONEXAO, SO_OFFLINE);
        }
    }

    /** Tira de {@code comSessao} quem o registro ja nao tem (evento perdido) e comeca a contar a tolerancia dele. */
    private void reconciliarComORegistro(Instant agora) {
        for (UUID id : new ArrayList<>(comSessao)) {
            if (!temSessaoAlemDe(id, null)) {
                comSessao.remove(id);
                semSessaoDesde.putIfAbsent(id, agora);
            }
        }
    }

    // --- apoio -------------------------------------------------------------------------------------------------

    private Optional<MudancaDePresenca> mudar(
            UUID usuarioId, StatusPresenca novo, String motivo, Set<StatusPresenca> anterioresPermitidos) {
        try {
            Optional<MudancaDePresenca> mudanca = ContextoDeServico.buscarComo(
                    "presenca-automatica", () -> presenca.executar(usuarioId, novo, motivo, anterioresPermitidos));
            mudanca.filter(MudancaDePresenca::mudou).ifPresent(m -> avisos.presencaAlterada(usuarioId, m.novo()));
            return mudanca;
        } catch (RuntimeException erro) {
            // Nunca derruba a thread do WebSocket nem a varredura: o proximo ciclo reconcilia.
            log.warn(
                    "{} nao foi possivel mudar a presenca: usuarioId={} para={} motivo={} tipoErro={}",
                    MARCADOR,
                    usuarioId,
                    novo,
                    motivo,
                    erro.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    private boolean emCarencia(Instant agora) {
        return agora.isBefore(partidaEm.plus(carencia));
    }

    /** Valor recente da chave; falha de leitura mantem o ultimo valor (desligada se nunca leu). */
    private boolean habilitada(Instant agora) {
        if (Duration.between(chaveLidaEm, agora).compareTo(validadeDaChave) >= 0) {
            try {
                chaveLida = configuracao.habilitada();
                chaveLidaEm = agora;
            } catch (RuntimeException erro) {
                log.warn("{} chave presenca.automatica ilegivel: tipoErro={}", MARCADOR, erro.getClass().getSimpleName());
            }
        }
        return chaveLida;
    }

    /** O usuario tem sessao no registro, sem contar {@code sessaoQueSaiu} (que pode ainda nao ter sido removida). */
    private boolean temSessaoAlemDe(UUID usuarioId, String sessaoQueSaiu) {
        SimpUser usuario = usuarios.getUser(usuarioId.toString());
        return usuario != null
                && usuario.getSessions().stream().anyMatch(sessao -> !sessao.getId().equals(sessaoQueSaiu));
    }

    private static Optional<UUID> idDoUsuario(Principal principal) {
        if (principal == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(principal.getName()));
        } catch (IllegalArgumentException naoEUuid) {
            return Optional.empty();
        }
    }
}
