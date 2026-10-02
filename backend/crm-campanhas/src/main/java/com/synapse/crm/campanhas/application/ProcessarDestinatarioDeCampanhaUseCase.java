package com.synapse.crm.campanhas.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.atendimento.application.campanha.EnfileirarTemplateDeCampanhaUseCase;
import com.synapse.crm.campanhas.application.DestinatarioRepositorio.Pendente;
import com.synapse.crm.campanhas.domain.Campanha;
import com.synapse.crm.campanhas.domain.CampoDoLead;
import com.synapse.crm.campanhas.domain.CorpoDoTemplate;
import com.synapse.crm.campanhas.domain.MapeamentoDeVariaveis;
import com.synapse.crm.campanhas.domain.MotivoDoDestinatario;
import com.synapse.crm.core.domain.lead.TelefoneCanonico;
import com.synapse.crm.core.domain.lead.TelefoneInvalidoException;
import com.synapse.crm.sharedkernel.persistencia.Pools;

/**
 * Envia UM destinatario da campanha, numa transacao curta. E a unidade de atomicidade do envio em massa: a
 * reserva da politica proativa (E219), o atendimento finalizado, a mensagem, o evento da outbox, a vaga do dia
 * e a mudanca do destinatario para ENFILEIRADO acontecem juntos ou nao acontecem. Por isso reiniciar o processo
 * no meio da campanha nunca duplica nem perde ninguem: o que nao commitou continua PENDENTE.
 *
 * <p>Quem chama roda em contexto de servico (agendador). O interruptor da campanha e o global sao relidos aqui,
 * a cada destinatario, com a campanha travada em modo compartilhado: "pausar agora" vale no maximo depois da
 * transacao em curso.
 */
@Service
public class ProcessarDestinatarioDeCampanhaUseCase {

    public enum Resultado {
        ENFILEIRADO,
        IGNORADO,
        /** Nao ha mais ninguem para enviar. */
        SEM_PENDENTES,
        /** A campanha deixou de poder enviar (pausada, desligada, envio global desligado). */
        NAO_ENVIA,
        /** A politica proativa esta desligada na instancia: nada e consumido, o ciclo tenta de novo depois. */
        BLOQUEADA_PELA_POLITICA
    }

    /** O limite do dia (da campanha ou da instancia) esgotou: a transacao desfaz tudo e o ciclo para. */
    public static final class LimiteDoDiaAtingidoException extends RuntimeException {

        public LimiteDoDiaAtingidoException() {
            super("limite diario atingido");
        }
    }

    public record Entrada(
            UUID campanhaId,
            Campanha.TemplateSnapshot template,
            MapeamentoDeVariaveis mapeamento,
            LocalDate dia,
            int limiteDoDia,
            int tetoDaInstancia) {}

    private final CampanhaRepositorio campanhas;
    private final DestinatarioRepositorio destinatarios;
    private final OptOutRepositorio optOuts;
    private final EnfileirarTemplateDeCampanhaUseCase enfileirar;
    private final ConfiguracaoDeCampanhas configuracao;
    private final TelefoneCanonico telefoneCanonico;
    private final Clock relogio;

    public ProcessarDestinatarioDeCampanhaUseCase(
            CampanhaRepositorio campanhas,
            DestinatarioRepositorio destinatarios,
            OptOutRepositorio optOuts,
            EnfileirarTemplateDeCampanhaUseCase enfileirar,
            ConfiguracaoDeCampanhas configuracao,
            TelefoneCanonico telefoneCanonico,
            Clock relogio) {
        this.campanhas = campanhas;
        this.destinatarios = destinatarios;
        this.optOuts = optOuts;
        this.enfileirar = enfileirar;
        this.configuracao = configuracao;
        this.telefoneCanonico = telefoneCanonico;
        this.relogio = relogio;
    }

    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER)
    public Resultado executar(Entrada entrada) {
        Optional<Campanha> viva = campanhas.bloquearParaEnvio(entrada.campanhaId());
        if (viva.isEmpty() || !viva.get().podeEnviarAgora(configuracao.atuais().envioHabilitado())) {
            return Resultado.NAO_ENVIA;
        }
        Optional<Pendente> proximo = destinatarios.proximoPendente(entrada.campanhaId());
        if (proximo.isEmpty()) {
            return Resultado.SEM_PENDENTES;
        }
        Pendente destinatario = proximo.get();
        Instant agora = Instant.now(relogio);
        if (optOuts.existe(destinatario.leadId())) {
            return ignorar(destinatario, MotivoDoDestinatario.OPT_OUT_NO_ENVIO, agora);
        }
        if (!telefoneValido(destinatario.telefone())) {
            return ignorar(destinatario, MotivoDoDestinatario.TELEFONE_INVALIDO, agora);
        }

        List<String> parametros = entrada.mapeamento()
                .resolver(new CampoDoLead.Dados(
                        destinatario.nome(), destinatario.empresa(), destinatario.localizacao()));
        String corpo = CorpoDoTemplate.renderizar(entrada.template().corpo(), parametros);
        EnfileirarTemplateDeCampanhaUseCase.Resultado resultado = enfileirar.executar(
                new EnfileirarTemplateDeCampanhaUseCase.Pedido(
                        entrada.campanhaId(),
                        destinatario.leadId(),
                        entrada.template().nome(),
                        entrada.template().idioma(),
                        parametros,
                        corpo,
                        false));
        return switch (resultado) {
            case EnfileirarTemplateDeCampanhaUseCase.Resultado.Recusado recusado -> tratarRecusa(destinatario, recusado, agora);
            case EnfileirarTemplateDeCampanhaUseCase.Resultado.Enfileirado enfileirado ->
                registrar(entrada, destinatario, enfileirado, agora);
        };
    }

    private Resultado registrar(
            Entrada entrada,
            Pendente destinatario,
            EnfileirarTemplateDeCampanhaUseCase.Resultado.Enfileirado enfileirado,
            Instant agora) {
        // Depois de enfileirar: se a vaga nao existir, a excecao desfaz a reserva E219, a mensagem e a outbox.
        if (!campanhas.reservarVagaDoDia(
                entrada.campanhaId(), entrada.dia(), entrada.limiteDoDia(), entrada.tetoDaInstancia())) {
            throw new LimiteDoDiaAtingidoException();
        }
        boolean marcado = destinatarios.marcarEnfileirado(
                destinatario.id(), enfileirado.mensagemId(), enfileirado.enviadoEm(), enfileirado.atendimentoId(), agora);
        if (!marcado) {
            throw new IllegalStateException("destinatario " + destinatario.id() + " deixou de ser PENDENTE durante o envio");
        }
        campanhas.variarContadores(entrada.campanhaId(), CampanhaRepositorio.Variacao.enfileirou());
        return Resultado.ENFILEIRADO;
    }

    private Resultado tratarRecusa(
            Pendente destinatario, EnfileirarTemplateDeCampanhaUseCase.Resultado.Recusado recusado, Instant agora) {
        return switch (recusado.motivo()) {
            // Politica desligada pela instancia: nao consome o destinatario, ou um desligamento passageiro
            // marcaria a base inteira como ignorada de forma irreversivel.
            case AUTOMACAO_PROATIVA_DESLIGADA, TIPO_PROATIVO_DESLIGADO -> Resultado.BLOQUEADA_PELA_POLITICA;
            case LEAD_INDISPONIVEL -> ignorar(destinatario, MotivoDoDestinatario.LEAD_INDISPONIVEL, agora);
            case ATENDIMENTO_ABERTO -> ignorar(destinatario, MotivoDoDestinatario.ATENDIMENTO_ABERTO_NO_ENVIO, agora);
            case JA_RESERVADO -> ignorar(destinatario, MotivoDoDestinatario.OCORRENCIA_JA_REGISTRADA, agora);
            case COOLDOWN -> ignorar(destinatario, MotivoDoDestinatario.COOLDOWN, agora);
            case TETO_DIARIO_POR_LEAD -> ignorar(destinatario, MotivoDoDestinatario.TETO_DIARIO_POR_LEAD, agora);
        };
    }

    private Resultado ignorar(Pendente destinatario, MotivoDoDestinatario motivo, Instant agora) {
        if (destinatarios.marcarIgnorado(destinatario.id(), motivo, agora)) {
            // A campanha ja esta travada em modo compartilhado; os contadores sao incrementais e atomicos.
            campanhas.variarContadores(destinatario.campanhaId(), CampanhaRepositorio.Variacao.ignorou());
        }
        return Resultado.IGNORADO;
    }

    private boolean telefoneValido(String telefone) {
        try {
            return telefone != null && telefoneCanonico.normalizar(telefone) != null;
        } catch (TelefoneInvalidoException invalido) {
            return false;
        }
    }
}
