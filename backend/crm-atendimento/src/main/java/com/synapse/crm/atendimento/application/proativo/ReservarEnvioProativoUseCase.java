package com.synapse.crm.atendimento.application.proativo;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.atendimento.application.RecursoDeAtendimentoIndisponivelException;
import com.synapse.crm.atendimento.application.internal.AtendimentoDaAutomacaoRepositorio;
import com.synapse.crm.atendimento.application.origem.TipoDeOrigem;
import com.synapse.crm.atendimento.application.proativo.PoliticaDeEnvioProativo.Politica;
import com.synapse.crm.atendimento.application.proativo.ReservaDeEnvioProativoRepositorio.Estado;
import com.synapse.crm.atendimento.application.proativo.ReservaDeEnvioProativoRepositorio.ReservaProativa;
import com.synapse.crm.sharedkernel.persistencia.Pools;

/**
 * Decide, antes de a Automacao chamar o provedor, se uma mensagem proativa pode sair (E219).
 *
 * <p>So reservas concedidas viram linha. A ordem das checagens importa: a reexecucao do mesmo pedido
 * (mesma chave) responde "chave ja usada" mesmo que a politica tenha mudado depois, para o n8n nunca
 * confundir "ja reservei" com "fui bloqueado". Resposta a mensagem do lead nao passa por aqui: o tipo
 * RESPOSTA_IA e recusado, e cooldown/teto contam apenas proativas.
 */
@Service
public class ReservarEnvioProativoUseCase {

    public static final int TAMANHO_MAXIMO_DA_CHAVE = 200;
    public static final int TAMANHO_MAXIMO_DA_OCORRENCIA = 200;
    public static final int TAMANHO_MAXIMO_DA_REGRA = 100;
    public static final int TAMANHO_MAXIMO_DA_EXECUCAO = 200;
    public static final int MAXIMO_DE_PENDENTES = 100;

    private final AtendimentoDaAutomacaoRepositorio leads;
    private final ReservaDeEnvioProativoRepositorio reservas;
    private final PoliticaDeEnvioProativo politica;
    private final Clock relogio;
    private final ZoneId fuso;

    public ReservarEnvioProativoUseCase(
            AtendimentoDaAutomacaoRepositorio leads,
            ReservaDeEnvioProativoRepositorio reservas,
            PoliticaDeEnvioProativo politica,
            Clock relogio,
            ZoneId fuso) {
        this.leads = leads;
        this.reservas = reservas;
        this.politica = politica;
        this.relogio = relogio;
        this.fuso = fuso;
    }

    @PreAuthorize("hasRole('SERVICO')")
    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER)
    public Decisao reservar(UUID leadId, Pedido pedido) {
        TipoDeOrigem tipo = validar(pedido);
        if (!leads.leadExiste(leadId)) {
            throw new RecursoDeAtendimentoIndisponivelException("lead", leadId);
        }
        String hash = hash(leadId, tipo, pedido.regraId(), pedido.ocorrencia());
        reservas.serializarDecisoesDoLead(leadId);

        Optional<ReservaProativa> mesmaChave = reservas.porChave(pedido.chave());
        if (mesmaChave.isPresent()) {
            return repeticao(mesmaChave.get(), hash);
        }
        Optional<ReservaProativa> mesmaOcorrencia =
                reservas.porOcorrencia(leadId, tipo, pedido.regraId(), pedido.ocorrencia());
        if (mesmaOcorrencia.isPresent()) {
            return Decisao.naoEnvie(Motivo.OCORRENCIA_JA_REGISTRADA, mesmaOcorrencia.get(), null);
        }
        Instant agora = Instant.now(relogio);
        Optional<Decisao> bloqueio = bloqueioPelaPolitica(leadId, tipo, agora);
        if (bloqueio.isPresent()) {
            return bloqueio.get();
        }
        ReservaProativa nova = new ReservaProativa(
                pedido.chave(),
                leadId,
                tipo,
                pedido.regraId(),
                pedido.ocorrencia(),
                pedido.execucaoId(),
                hash,
                Estado.RESERVADO,
                null,
                null,
                agora,
                null);
        if (!reservas.inserir(nova)) {
            // A trava e por lead; a chave pode ter sido usada por outro lead na mesma janela.
            return reservas.porChave(pedido.chave())
                    .map(existente -> repeticao(existente, hash))
                    .orElseThrow(() -> new IllegalStateException("reserva proativa recusada sem linha conflitante"));
        }
        return Decisao.podeEnviar(nova);
    }

    @PreAuthorize("hasRole('SERVICO')")
    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER, readOnly = true)
    public List<ReservaProativa> pendentesAntesDe(Instant limite) {
        return reservas.pendentesAntesDe(limite, MAXIMO_DE_PENDENTES);
    }

    private Optional<Decisao> bloqueioPelaPolitica(UUID leadId, TipoDeOrigem tipo, Instant agora) {
        Politica vigente = politica.vigente();
        if (!vigente.habilitada()) {
            return Optional.of(Decisao.naoEnvie(Motivo.AUTOMACAO_PROATIVA_DESLIGADA, null, null));
        }
        if (vigente.tiposDesligados().contains(tipo)) {
            return Optional.of(Decisao.naoEnvie(Motivo.TIPO_DESLIGADO, null, null));
        }
        if (vigente.cooldownHoras() > 0) {
            Optional<Instant> liberadoApos = reservas.ultimoEnvioDoTipo(leadId, tipo)
                    .map(ultimo -> ultimo.plus(Duration.ofHours(vigente.cooldownHoras())))
                    .filter(liberacao -> liberacao.isAfter(agora));
            if (liberadoApos.isPresent()) {
                return Optional.of(Decisao.naoEnvie(Motivo.COOLDOWN, null, liberadoApos.get()));
            }
        }
        if (vigente.tetoDiarioPorLead() > 0) {
            LocalDate hoje = LocalDate.ofInstant(agora, fuso);
            Instant inicioDoDia = hoje.atStartOfDay(fuso).toInstant();
            if (reservas.enviosProativosDesde(leadId, inicioDoDia) >= vigente.tetoDiarioPorLead()) {
                Instant amanha = hoje.plusDays(1).atStartOfDay(fuso).toInstant();
                return Optional.of(Decisao.naoEnvie(Motivo.TETO_DIARIO, null, amanha));
            }
        }
        return Optional.empty();
    }

    private static Decisao repeticao(ReservaProativa existente, String hash) {
        if (!existente.requisicaoHash().equals(hash)) {
            throw new ChaveProativaReutilizadaException(existente.chave());
        }
        return Decisao.naoEnvie(Motivo.CHAVE_JA_USADA, existente, null);
    }

    private static TipoDeOrigem validar(Pedido pedido) {
        if (pedido == null) {
            throw new PedidoDeEnvioProativoInvalidoException("corpo obrigatorio");
        }
        exigirTexto("chave", pedido.chave(), TAMANHO_MAXIMO_DA_CHAVE, true);
        exigirTexto("ocorrencia", pedido.ocorrencia(), TAMANHO_MAXIMO_DA_OCORRENCIA, true);
        exigirTexto("regraId", pedido.regraId(), TAMANHO_MAXIMO_DA_REGRA, false);
        exigirTexto("execucaoId", pedido.execucaoId(), TAMANHO_MAXIMO_DA_EXECUCAO, false);
        TipoDeOrigem tipo = TipoDeOrigem.declaradoPelaAutomacao(pedido.tipo())
                .orElseThrow(() -> new PedidoDeEnvioProativoInvalidoException("tipo desconhecido: " + pedido.tipo()));
        if (!tipo.proativa()) {
            throw new PedidoDeEnvioProativoInvalidoException(
                    "tipo " + tipo + " nao e proativo: resposta ao lead nao passa pela reserva proativa");
        }
        return tipo;
    }

    private static void exigirTexto(String campo, String valor, int maximo, boolean obrigatorio) {
        if (valor == null) {
            if (obrigatorio) {
                throw new PedidoDeEnvioProativoInvalidoException(campo + " e obrigatorio");
            }
            return;
        }
        if (valor.isBlank() || valor.length() > maximo) {
            throw new PedidoDeEnvioProativoInvalidoException(campo + " vazio ou maior que " + maximo + " caracteres");
        }
    }

    /**
     * O hash cobre o que define o envio (lead, tipo, regra, ocorrencia). execucaoId fica de fora: a
     * reexecucao do mesmo envio no n8n tem outro id de execucao e precisa ser reconhecida.
     */
    static String hash(UUID leadId, TipoDeOrigem tipo, String regraId, String ocorrencia) {
        String canonico = leadId + "\n" + tipo.name() + "\n" + (regraId == null ? "" : regraId) + "\n" + ocorrencia;
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(canonico.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException erro) {
            throw new IllegalStateException("SHA-256 indisponivel", erro);
        }
    }

    /** Tipo e texto livre do contrato: {@link TipoDeOrigem} proativo, validado aqui. */
    public record Pedido(String tipo, String regraId, String ocorrencia, String chave, String execucaoId) {}

    public enum Motivo {
        /** A chave ja tem reserva (reexecucao/retry): veja estado e wamidSaida, nao envie de novo. */
        CHAVE_JA_USADA,
        /** A mesma ocorrencia (lead, tipo, regra, ocorrencia) ja foi reservada com outra chave. */
        OCORRENCIA_JA_REGISTRADA,
        AUTOMACAO_PROATIVA_DESLIGADA,
        TIPO_DESLIGADO,
        COOLDOWN,
        TETO_DIARIO
    }

    /**
     * @param reserva a reserva concedida, ou a existente nos motivos CHAVE_JA_USADA e
     *     OCORRENCIA_JA_REGISTRADA; nula nos bloqueios da politica
     * @param liberadoApos quando o bloqueio de COOLDOWN/TETO_DIARIO deixa de valer
     */
    public record Decisao(boolean podeEnviar, Motivo motivo, ReservaProativa reserva, Instant liberadoApos) {

        static Decisao podeEnviar(ReservaProativa reserva) {
            return new Decisao(true, null, reserva, null);
        }

        static Decisao naoEnvie(Motivo motivo, ReservaProativa reserva, Instant liberadoApos) {
            return new Decisao(false, motivo, reserva, liberadoApos);
        }
    }

    public static final class PedidoDeEnvioProativoInvalidoException extends RuntimeException {
        PedidoDeEnvioProativoInvalidoException(String detalhe) {
            super(detalhe);
        }
    }

    public static final class ChaveProativaReutilizadaException extends RuntimeException {
        public ChaveProativaReutilizadaException(String chave) {
            super("a chave " + chave + " ja foi usada com outro conteudo (lead, tipo, regra ou ocorrencia diferentes)");
        }
    }
}
