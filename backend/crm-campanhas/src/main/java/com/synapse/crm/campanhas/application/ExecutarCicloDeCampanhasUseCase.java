package com.synapse.crm.campanhas.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.synapse.crm.atendimento.domain.canal.TemplateDoCanal;
import com.synapse.crm.campanhas.application.CampanhaRepositorio.Lease;
import com.synapse.crm.campanhas.application.DestinatarioRepositorio.Alvo;
import com.synapse.crm.campanhas.application.ProcessarDestinatarioDeCampanhaUseCase.Entrada;
import com.synapse.crm.campanhas.application.ProcessarDestinatarioDeCampanhaUseCase.LimiteDoDiaAtingidoException;
import com.synapse.crm.campanhas.application.ProcessarDestinatarioDeCampanhaUseCase.Resultado;
import com.synapse.crm.campanhas.domain.Campanha;
import com.synapse.crm.campanhas.domain.PlanoDeLimite;
import com.synapse.crm.campanhas.domain.PoliticaDePausa;
import com.synapse.crm.campanhas.domain.StatusDaCampanha;
import com.synapse.crm.campanhas.domain.StatusDoDestinatario;

/**
 * Um ciclo do motor de campanhas. O agendador o chama a cada poucos segundos; ele decide, para cada campanha
 * ativa, se envia agora e quantos.
 *
 * <p>Trava por campanha: antes de enviar pega um lease persistido, entao dois workers (duas instancias, ou
 * duas threads) nunca trabalham na mesma campanha. Cada destinatario e uma transacao curta
 * ({@link ProcessarDestinatarioDeCampanhaUseCase}); o lease so evita trabalho dobrado, a seguranca contra
 * duplicidade esta na atomicidade dessa transacao.
 *
 * <p>Nada aqui abre transacao em volta de rede: a conferencia do template no provedor acontece entre
 * transacoes.
 */
@Service
public class ExecutarCicloDeCampanhasUseCase {

    private static final Logger log = LoggerFactory.getLogger(ExecutarCicloDeCampanhasUseCase.class);

    /** Maior que um ciclo normal; expira sozinho se o processo morrer no meio. */
    static final Duration DURACAO_DO_LEASE = Duration.ofSeconds(120);

    /** Um ENFILEIRADO mais novo que isto ainda esta a caminho do provedor: nao reconcilia. */
    static final Duration ESPERA_DA_RECONCILIACAO = Duration.ofMinutes(2);

    static final int LOTE_DA_RECONCILIACAO = 100;
    static final int CAMPANHAS_POR_RECONCILIACAO = 50;

    private final CampanhaRepositorio campanhas;
    private final DestinatarioRepositorio destinatarios;
    private final ConfiguracaoDeCampanhas configuracao;
    private final TemplatesDoCanal templates;
    private final ProcessarDestinatarioDeCampanhaUseCase processador;
    private final AplicarEntregaDeCampanhaUseCase entregas;
    private final PausaAutomaticaDaCampanha pausa;
    private final DisponibilidadeDeCampanhas disponibilidade;
    private final TransacoesDeCampanha transacoes;
    private final Clock relogio;
    private final ZoneId fuso;

    public ExecutarCicloDeCampanhasUseCase(
            CampanhaRepositorio campanhas,
            DestinatarioRepositorio destinatarios,
            ConfiguracaoDeCampanhas configuracao,
            TemplatesDoCanal templates,
            ProcessarDestinatarioDeCampanhaUseCase processador,
            AplicarEntregaDeCampanhaUseCase entregas,
            PausaAutomaticaDaCampanha pausa,
            DisponibilidadeDeCampanhas disponibilidade,
            TransacoesDeCampanha transacoes,
            Clock relogio,
            ZoneId fuso) {
        this.campanhas = campanhas;
        this.destinatarios = destinatarios;
        this.configuracao = configuracao;
        this.templates = templates;
        this.processador = processador;
        this.entregas = entregas;
        this.pausa = pausa;
        this.disponibilidade = disponibilidade;
        this.transacoes = transacoes;
        this.relogio = relogio;
        this.fuso = fuso;
    }

    public void executar() {
        if (!disponibilidade.disponivel()) {
            return;
        }
        ConfiguracaoDeCampanhas.Parametros parametros = transacoes.noChatSomenteLeitura(configuracao::atuais);
        if (!parametros.envioHabilitado()) {
            return;
        }
        Instant agora = Instant.now(relogio);
        List<UUID> ids = transacoes.noChatSomenteLeitura(() -> campanhas.idsParaProcessar(agora));
        for (UUID id : ids) {
            tentar("envio", id, () -> enviarDaCampanha(id, parametros));
        }
        conciliar(parametros);
    }

    // --- envio ------------------------------------------------------------------------------------------

    private void enviarDaCampanha(UUID id, ConfiguracaoDeCampanhas.Parametros parametros) {
        Instant agora = Instant.now(relogio);
        Optional<Lease> lease = transacoes.noChat(() -> campanhas.adquirirLease(id, agora, agora.plus(DURACAO_DO_LEASE)));
        if (lease.isEmpty()) {
            return;
        }
        try {
            Campanha campanha = comecarSeAgendada(lease.get().campanha(), agora);
            if (campanha != null && campanha.status().envia()) {
                enviarDentroDaJanela(campanha, lease.get().ultimoCicloEm(), parametros, agora);
                avaliarTaxaDeFalha(campanha, parametros);
            }
        } finally {
            transacoes.noChatSemRetorno(() -> campanhas.liberarLease(id, Instant.now(relogio)));
        }
    }

    private Campanha comecarSeAgendada(Campanha campanha, Instant agora) {
        if (campanha.status() != StatusDaCampanha.AGENDADA) {
            return campanha;
        }
        return transacoes.noChat(() -> {
            Campanha travada = campanhas.bloquearPorId(campanha.id()).orElse(null);
            if (travada == null || travada.status() != StatusDaCampanha.AGENDADA) {
                return travada;
            }
            Campanha emAndamento = travada.comecarAgendada(agora);
            campanhas.atualizar(emAndamento);
            return emAndamento;
        });
    }

    private void enviarDentroDaJanela(
            Campanha campanha, Instant ultimoCiclo, ConfiguracaoDeCampanhas.Parametros parametros, Instant agora) {
        ZonedDateTime agoraNoFuso = agora.atZone(fuso);
        if (!campanha.janela().abertaEm(agoraNoFuso) || !templateEstaAprovado(campanha)) {
            return;
        }
        long diasDesdeOInicio = campanha.iniciadaEm() == null
                ? 0
                : PlanoDeLimite.diasDesdeOInicio(campanha.iniciadaEm().atZone(fuso), agoraNoFuso);
        int limiteDoDia = PlanoDeLimite.limiteDoDia(
                campanha.limiteDiario(), campanha.rampa(), diasDesdeOInicio, parametros.tetoDiarioDaInstancia());
        int orcamento = PlanoDeLimite.orcamentoDoCiclo(
                campanha.ritmoPorMinuto(), ultimoCiclo == null ? null : Duration.between(ultimoCiclo, agora));
        Entrada entrada = new Entrada(
                campanha.id(),
                campanha.template(),
                campanha.mapeamento(),
                agoraNoFuso.toLocalDate(),
                limiteDoDia,
                parametros.tetoDiarioDaInstancia());

        for (int enviados = 0; enviados < orcamento; enviados++) {
            Resultado resultado;
            try {
                resultado = processador.executar(entrada);
            } catch (LimiteDoDiaAtingidoException limite) {
                log.info("Campanha {} atingiu o limite do dia ({} da campanha, {} da instancia).",
                        campanha.id(), limiteDoDia, parametros.tetoDiarioDaInstancia());
                return;
            }
            switch (resultado) {
                case SEM_PENDENTES -> {
                    concluirSeNaoHaPendentes(campanha.id());
                    return;
                }
                case NAO_ENVIA -> {
                    return;
                }
                case BLOQUEADA_PELA_POLITICA -> {
                    log.warn("Campanha {} aguarda: a politica proativa esta desligada na instancia.", campanha.id());
                    return;
                }
                case ENFILEIRADO, IGNORADO -> {
                    // segue para o proximo destinatario do orcamento
                }
            }
        }
    }

    /**
     * Template pausado, rejeitado ou removido pausa a campanha com o motivo. Falha ao consultar o provedor NAO
     * pausa: um erro de rede nao e um template invalido, o ciclo so nao envia agora.
     */
    private boolean templateEstaAprovado(Campanha campanha) {
        try {
            Optional<TemplateDoCanal> template =
                    templates.buscar(campanha.template().nome(), campanha.template().idioma());
            if (template.isEmpty()) {
                pausa.pausar(campanha.id(), PoliticaDePausa.MOTIVO_TEMPLATE_INDISPONIVEL + ":REMOVIDO");
                return false;
            }
            if (template.get().status() != TemplateDoCanal.Status.APROVADO) {
                pausa.pausar(
                        campanha.id(),
                        PoliticaDePausa.MOTIVO_TEMPLATE_INDISPONIVEL + ":" + template.get().status());
                return false;
            }
            return true;
        } catch (TemplatesDoCanal.TemplatesIndisponiveisException indisponivel) {
            log.warn("Campanha {} nao enviou neste ciclo: provedor de templates indisponivel.", campanha.id(), indisponivel);
            return false;
        }
    }

    private void concluirSeNaoHaPendentes(UUID id) {
        transacoes.noChatSemRetorno(() -> {
            Campanha travada = campanhas.bloquearPorId(id).orElse(null);
            if (travada != null
                    && travada.status() == StatusDaCampanha.EM_ANDAMENTO
                    && travada.contadores().pendentes() == 0) {
                campanhas.atualizar(travada.concluir(Instant.now(relogio)));
            }
        });
    }

    private void avaliarTaxaDeFalha(Campanha campanha, ConfiguracaoDeCampanhas.Parametros parametros) {
        PoliticaDePausa politica = parametros.politicaDePausa();
        PoliticaDePausa.Desfechos desfechos = transacoes.noChatSomenteLeitura(
                () -> destinatarios.desfechosRecentes(campanha.id(), politica.janelaDeEnvios()));
        politica.avaliarTaxa(desfechos).ifPresent(motivo -> pausa.pausar(campanha.id(), motivo));
    }

    // --- conferencia e reconciliacao -------------------------------------------------------------------

    /**
     * Rede de seguranca do funil: destinatario ENFILEIRADO ha mais de dois minutos tem a entrega
     * reconciliada com o status real da mensagem (um evento perdido nao pode deixar o contador parado), e o
     * que continua sem confirmacao depois do prazo vai para a conferencia manual. NUNCA para reenvio.
     */
    private void conciliar(ConfiguracaoDeCampanhas.Parametros parametros) {
        Instant agora = Instant.now(relogio);
        Instant corteDaReconciliacao = agora.minus(ESPERA_DA_RECONCILIACAO);
        Instant corteDaConferencia = agora.minus(Duration.ofMinutes(parametros.conferenciaAposMinutos()));
        List<UUID> ids = transacoes.noChatSomenteLeitura(
                () -> destinatarios.campanhasComEnfileiradoAntesDe(corteDaReconciliacao, CAMPANHAS_POR_RECONCILIACAO));
        for (UUID id : ids) {
            tentar("reconciliacao", id, () -> {
                reconciliar(id, corteDaReconciliacao, agora);
                sinalizarParaConferencia(id, corteDaConferencia);
            });
        }
    }

    private void reconciliar(UUID id, Instant corte, Instant agora) {
        List<Alvo> alvos = transacoes.noChatSomenteLeitura(
                () -> destinatarios.aReconciliar(id, corte, LOTE_DA_RECONCILIACAO));
        for (Alvo alvo : alvos) {
            Optional<StatusDoDestinatario> real = transacoes.noChatSomenteLeitura(
                    () -> destinatarios.statusDaMensagem(alvo.mensagemId(), alvo.mensagemEnviadaEm()));
            if (real.isPresent() && real.get() != StatusDoDestinatario.ENFILEIRADO) {
                entregas.executar(alvo.mensagemId(), real.get(), agora);
            }
        }
    }

    private void sinalizarParaConferencia(UUID id, Instant corte) {
        int sinalizados = transacoes.noChat(() -> {
            int quantidade = destinatarios.sinalizarParaConferencia(id, corte);
            if (quantidade > 0) {
                campanhas.variarContadores(id, CampanhaRepositorio.Variacao.conferencia(quantidade));
            }
            return quantidade;
        });
        if (sinalizados > 0) {
            log.warn(
                    "[ALERTA_CAMPANHA_CONFERENCIA] campanha {}: {} destinatario(s) enfileirado(s) sem confirmacao foram para a conferencia manual; nao serao reenviados.",
                    id,
                    sinalizados);
        }
    }

    /** Uma campanha com problema nunca derruba as outras nem o proximo ciclo. */
    private void tentar(String etapa, UUID campanhaId, Runnable acao) {
        try {
            acao.run();
        } catch (RuntimeException erro) {
            log.error("[ALERTA_CAMPANHA_CICLO] falha em {} da campanha {}; o ciclo segue.", etapa, campanhaId, erro);
        }
    }
}
