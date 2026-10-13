package com.synapse.crm.app.saude.infrastructure;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.synapse.crm.app.config.SynapseProperties;
import com.synapse.crm.app.saude.application.HorarioComercialDoCliente;
import com.synapse.crm.automacaoconfig.application.ConfiguracaoAutomacaoRepositorio;

/**
 * Janela editável no CRUD já existente de {@code configuracao_automacao}.
 *
 * <p>A janela padrão ({@code alerta.horario_cliente.inicio/fim}) vale para todos os dias. Opcionalmente um dia
 * da semana pode ter a sua: {@code alerta.horario_cliente.<seg|ter|qua|qui|sex|sab|dom>} com o valor
 * {@code HH:mm-HH:mm} (início inclusivo, fim exclusivo) ou {@code fechado} (nunca avisa o cliente naquele dia).
 * Chave ausente = o dia usa a janela padrão, exatamente como antes. Valor ilegível também cai na janela padrão
 * (e avisa no log): errar para o lado de avisar é melhor do que silenciar um alerta crítico por um erro de digitação.
 *
 * <p>O dia da semana e a hora são os do fuso da instância ({@code synapse.tenant.timezone}), nunca os do UTC.
 */
@Component
class HorarioComercialConfiguravel implements HorarioComercialDoCliente {

    static final String CHAVE_INICIO = "alerta.horario_cliente.inicio";
    static final String CHAVE_FIM = "alerta.horario_cliente.fim";
    static final String PREFIXO_DIA = "alerta.horario_cliente.";

    private static final Logger log = LoggerFactory.getLogger(HorarioComercialConfiguravel.class);
    private static final Pattern FAIXA = Pattern.compile("^(\\d{2}:\\d{2})-(\\d{2}:\\d{2})$");
    private static final String FECHADO = "fechado";

    private static final Map<DayOfWeek, String> SIGLA_DO_DIA = new EnumMap<>(Map.of(
            DayOfWeek.MONDAY, "seg",
            DayOfWeek.TUESDAY, "ter",
            DayOfWeek.WEDNESDAY, "qua",
            DayOfWeek.THURSDAY, "qui",
            DayOfWeek.FRIDAY, "sex",
            DayOfWeek.SATURDAY, "sab",
            DayOfWeek.SUNDAY, "dom"));

    private final ConfiguracaoAutomacaoRepositorio configuracoes;
    private final ZoneId fusoHorario;
    private final AtomicReference<Janela> ultimaJanelaValida = new AtomicReference<>();
    /** Dia com valor: janela (aberto) ou vazio (fechado). Dia sem entrada: usa a janela padrão. */
    private final Map<DayOfWeek, Optional<Janela>> ultimaJanelaPorDia = new ConcurrentHashMap<>();

    HorarioComercialConfiguravel(
            ConfiguracaoAutomacaoRepositorio configuracoes, SynapseProperties propriedades) {
        this.configuracoes = configuracoes;
        this.fusoHorario = ZoneId.of(propriedades.tenant().timezone());
    }

    @Override
    public boolean permiteAvisarCliente(Instant instante) {
        ZonedDateTime local = instante.atZone(fusoHorario);
        atualizarCacheSePossivel(local.getDayOfWeek());
        Optional<Janela> doDia = ultimaJanelaPorDia.get(local.getDayOfWeek());
        if (doDia != null) {
            return doDia.map(janela -> janela.contem(local.toLocalTime())).orElse(false);
        }
        Janela janela = ultimaJanelaValida.get();
        return janela != null && janela.contem(local.toLocalTime());
    }

    /**
     * Lê a janela padrão e a de hoje e a de amanhã: se o banco cair na virada do dia, o cache ainda sabe como
     * o dia seguinte se comporta. São buscas por chave primária numa tabela pequena, uma por chave.
     */
    private void atualizarCacheSePossivel(DayOfWeek hoje) {
        try {
            Optional<String> inicio = configuracoes.porChave(CHAVE_INICIO).map(c -> c.valor());
            Optional<String> fim = configuracoes.porChave(CHAVE_FIM).map(c -> c.valor());
            if (inicio.isPresent() && fim.isPresent()) {
                ultimaJanelaValida.set(
                        new Janela(LocalTime.parse(inicio.get()), LocalTime.parse(fim.get())));
            }
            atualizarDia(hoje);
            atualizarDia(hoje.plus(1));
        } catch (RuntimeException e) {
            log.warn(
                    "Nao foi possivel atualizar a janela de alerta do cliente; usando o ultimo valor valido.");
        }
    }

    private void atualizarDia(DayOfWeek dia) {
        String chave = PREFIXO_DIA + SIGLA_DO_DIA.get(dia);
        Optional<String> valor = configuracoes.porChave(chave).map(c -> c.valor());
        if (valor.isEmpty()) {
            ultimaJanelaPorDia.remove(dia);
            return;
        }
        Optional<Optional<Janela>> lida = interpretar(valor.get());
        if (lida.isPresent()) {
            ultimaJanelaPorDia.put(dia, lida.get());
        } else {
            ultimaJanelaPorDia.remove(dia);
            log.warn("Valor ilegivel em {}; o dia usa a janela padrao. Esperado HH:mm-HH:mm ou fechado.", chave);
        }
    }

    /** Vazio = ilegível; {@code Optional.of(Optional.empty())} = fechado; senão, a janela do dia. */
    private static Optional<Optional<Janela>> interpretar(String bruto) {
        String valor = bruto.strip().toLowerCase(Locale.ROOT);
        if (FECHADO.equals(valor)) {
            return Optional.of(Optional.empty());
        }
        Matcher faixa = FAIXA.matcher(valor);
        if (!faixa.matches()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Optional.of(new Janela(LocalTime.parse(faixa.group(1)), LocalTime.parse(faixa.group(2)))));
        } catch (DateTimeParseException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private record Janela(LocalTime inicio, LocalTime fim) {

        private Janela {
            if (!fim.isAfter(inicio)) {
                throw new IllegalArgumentException("fim da janela deve ser posterior ao inicio");
            }
        }

        boolean contem(LocalTime horario) {
            return !horario.isBefore(inicio) && horario.isBefore(fim);
        }
    }
}
