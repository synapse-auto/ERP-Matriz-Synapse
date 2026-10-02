package com.synapse.crm.campanhas.interfaces;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.synapse.crm.campanhas.application.ConfiguracaoDeCampanhas;
import com.synapse.crm.campanhas.application.ConsultasDeCampanhaUseCases;
import com.synapse.crm.campanhas.application.ConsultasDeDestinatarios;
import com.synapse.crm.campanhas.application.OptOutRepositorio;
import com.synapse.crm.campanhas.application.Pagina;
import com.synapse.crm.campanhas.application.PedidoDeCampanha;
import com.synapse.crm.campanhas.application.ProjetarEnvioUseCase;
import com.synapse.crm.campanhas.application.PublicoRepositorio;
import com.synapse.crm.campanhas.domain.Campanha;
import com.synapse.crm.campanhas.domain.CampoDoLead;
import com.synapse.crm.campanhas.domain.DiasDaSemana;
import com.synapse.crm.campanhas.domain.FiltroDePublico;
import com.synapse.crm.campanhas.domain.JanelaDeEnvio;
import com.synapse.crm.campanhas.domain.MapeamentoDeVariaveis;
import com.synapse.crm.campanhas.domain.PlanoDeLimite;

/** Contratos HTTP de campanhas. O dominio nunca sai pela API: tudo passa por estes records. */
final class CampanhaDtos {

    private CampanhaDtos() {}

    // --- requisicoes ----------------------------------------------------------------------------------

    record VariavelDto(@Min(1) int posicao, @NotNull CampoDoLead campo, @NotBlank @Size(max = 60) String reserva) {}

    record FiltroDto(
            List<UUID> tagIds,
            UUID etapaId,
            UUID atendenteId,
            LocalDate cadastroDesde,
            LocalDate cadastroAte,
            Boolean nuncaConversou,
            @Size(max = 100) String busca) {

        FiltroDePublico paraDominio() {
            return new FiltroDePublico(
                    tagIds, etapaId, atendenteId, cadastroDesde, cadastroAte, Boolean.TRUE.equals(nuncaConversou), busca);
        }

        static FiltroDto de(FiltroDePublico filtro) {
            return new FiltroDto(
                    filtro.tagIds(),
                    filtro.etapaId(),
                    filtro.atendenteId(),
                    filtro.cadastroDesde(),
                    filtro.cadastroAte(),
                    filtro.nuncaConversou(),
                    filtro.busca());
        }
    }

    /** @param dias dias da semana ISO: 1 = segunda ... 7 = domingo */
    record JanelaDto(@NotNull LocalTime inicio, @NotNull LocalTime fim, @NotNull @Size(min = 1, max = 7) List<Integer> dias) {

        JanelaDeEnvio paraDominio() {
            Set<DayOfWeek> conjunto = EnumSet.noneOf(DayOfWeek.class);
            dias.forEach(dia -> conjunto.add(DayOfWeek.of(dia)));
            return new JanelaDeEnvio(inicio, fim, DiasDaSemana.de(conjunto));
        }

        static JanelaDto de(JanelaDeEnvio janela) {
            return new JanelaDto(
                    janela.inicio(),
                    janela.fim(),
                    janela.dias().conjunto().stream().map(DayOfWeek::getValue).sorted().toList());
        }
    }

    record RampaDto(@Min(1) int incrementoPorDia, @Min(1) int teto) {

        PlanoDeLimite.Rampa paraDominio() {
            return new PlanoDeLimite.Rampa(incrementoPorDia, teto);
        }
    }

    record CampanhaRequisicao(
            @NotBlank @Size(max = Campanha.TAMANHO_MAXIMO_DO_NOME) String nome,
            @NotBlank String templateNome,
            @NotBlank String templateIdioma,
            @Valid List<VariavelDto> variaveis,
            @Valid FiltroDto filtro,
            @Min(1) Integer limiteDiario,
            @Valid JanelaDto janela,
            @Min(1) @Max(600) Integer ritmoPorMinuto,
            @Valid RampaDto rampa,
            Instant agendadaPara) {

        PedidoDeCampanha paraDominio() {
            List<MapeamentoDeVariaveis.Variavel> mapeadas = variaveis == null
                    ? List.of()
                    : variaveis.stream()
                            .map(v -> new MapeamentoDeVariaveis.Variavel(v.posicao(), v.campo(), v.reserva()))
                            .toList();
            return new PedidoDeCampanha(
                    nome,
                    templateNome,
                    templateIdioma,
                    new MapeamentoDeVariaveis(mapeadas),
                    filtro == null ? FiltroDePublico.agendaInteira() : filtro.paraDominio(),
                    limiteDiario,
                    janela == null ? null : janela.paraDominio(),
                    ritmoPorMinuto,
                    rampa == null ? null : rampa.paraDominio(),
                    agendadaPara);
        }
    }

    record PreviaRequisicao(@Valid FiltroDto filtro) {

        FiltroDePublico filtroDominio() {
            return filtro == null ? FiltroDePublico.agendaInteira() : filtro.paraDominio();
        }
    }

    record ExcluidosRequisicao(@Valid FiltroDto filtro, @NotBlank String motivo) {}

    record ProjecaoRequisicao(
            @Valid FiltroDto filtro,
            @Min(1) int limiteDiario,
            @NotNull @Valid JanelaDto janela,
            @Min(1) @Max(600) int ritmoPorMinuto,
            @Valid RampaDto rampa,
            LocalDate primeiroDia) {

        ProjetarEnvioUseCase.Entrada paraDominio() {
            return new ProjetarEnvioUseCase.Entrada(
                    filtro == null ? FiltroDePublico.agendaInteira() : filtro.paraDominio(),
                    limiteDiario,
                    janela.paraDominio(),
                    ritmoPorMinuto,
                    rampa == null ? null : rampa.paraDominio(),
                    primeiroDia);
        }
    }

    record LimiteRequisicao(@Min(1) int limiteDiario, @Min(1) @Max(600) Integer ritmoPorMinuto) {}

    record InterruptorRequisicao(boolean desligada) {}

    record TesteRequisicao(@NotBlank String telefone, boolean destinatarioAutorizou) {}

    record OptOutRequisicao(@Size(max = 300) String motivo) {}

    record ConfiguracaoRequisicao(
            Boolean envioHabilitado,
            @Min(1) Integer tetoDiarioDaInstancia,
            @Min(1) Integer limiteDiarioPadrao,
            @Min(0) Integer limiteMetaInformado,
            @Min(1) @Max(100) Integer limiarDeFalhaPorCento,
            @Min(10) Integer janelaDeEnvios,
            @Min(5) Integer minimoDeAmostra) {

        ConfiguracaoDeCampanhas.Atualizacao paraDominio() {
            return new ConfiguracaoDeCampanhas.Atualizacao(
                    envioHabilitado,
                    tetoDiarioDaInstancia,
                    limiteDiarioPadrao,
                    limiteMetaInformado,
                    limiarDeFalhaPorCento,
                    janelaDeEnvios,
                    minimoDeAmostra);
        }
    }

    // --- respostas ------------------------------------------------------------------------------------

    record ContadoresResposta(
            int total,
            int pendentes,
            int enfileirados,
            int enviados,
            int entregues,
            int lidos,
            int respondidos,
            int falhas,
            int ignorados,
            int conferencia) {

        static ContadoresResposta de(Campanha.Contadores c) {
            return new ContadoresResposta(
                    c.total(), c.pendentes(), c.enfileirados(), c.enviados(), c.entregues(), c.lidos(),
                    c.respondidos(), c.falhas(), c.ignorados(), c.conferencia());
        }
    }

    record TemplateDaCampanhaResposta(
            String id, String nome, String idioma, String categoria, String corpo, int parametros) {}

    record CampanhaResposta(
            UUID id,
            String nome,
            String status,
            boolean desligada,
            TemplateDaCampanhaResposta template,
            List<VariavelDto> variaveis,
            FiltroDto filtro,
            int limiteDiario,
            JanelaDto janela,
            int ritmoPorMinuto,
            RampaResposta rampa,
            Instant agendadaPara,
            ContadoresResposta contadores,
            String motivoDePausa,
            Instant pausadaEm,
            Instant iniciadaEm,
            Instant concluidaEm,
            UUID criadaPor,
            Instant criadaEm) {

        static CampanhaResposta de(Campanha c) {
            return new CampanhaResposta(
                    c.id(),
                    c.nome(),
                    c.status().name(),
                    c.desligada(),
                    new TemplateDaCampanhaResposta(
                            c.template().id(),
                            c.template().nome(),
                            c.template().idioma(),
                            c.template().categoria(),
                            c.template().corpo(),
                            c.template().parametros()),
                    c.mapeamento().variaveis().stream()
                            .map(v -> new VariavelDto(v.posicao(), v.campo(), v.reserva()))
                            .toList(),
                    FiltroDto.de(c.filtro()),
                    c.limiteDiario(),
                    JanelaDto.de(c.janela()),
                    c.ritmoPorMinuto(),
                    c.rampa() == null ? null : new RampaResposta(c.rampa().incrementoPorDia(), c.rampa().teto()),
                    c.agendadaPara(),
                    ContadoresResposta.de(c.contadores()),
                    c.motivoDePausa(),
                    c.pausadaEm(),
                    c.iniciadaEm(),
                    c.concluidaEm(),
                    c.criadaPor(),
                    c.criadaEm());
        }
    }

    record RampaResposta(int incrementoPorDia, int teto) {}

    record DiaResposta(LocalDate dia, int enfileiradas) {}

    record DetalheResposta(
            CampanhaResposta campanha,
            List<DiaResposta> porDia,
            int limiteEfetivoHoje,
            int tetoDaInstancia,
            int limiteMetaInformado,
            int enfileiradasHojeNaInstancia) {

        static DetalheResposta de(ConsultasDeCampanhaUseCases.Detalhe d) {
            return new DetalheResposta(
                    CampanhaResposta.de(d.campanha()),
                    d.porDia().stream().map(dia -> new DiaResposta(dia.dia(), dia.enfileiradas())).toList(),
                    d.limiteEfetivoHoje(),
                    d.tetoDaInstancia(),
                    d.limiteMetaInformado(),
                    d.enfileiradasHojeNaInstancia());
        }
    }

    record IndicadoresResposta(long enviadas, long entregues, long lidas, long respondidas, long falhas) {}

    record ListaResposta(
            List<CampanhaResposta> itens,
            int pagina,
            int tamanho,
            long total,
            IndicadoresResposta indicadores,
            int enfileiradasHoje,
            int tetoDiario,
            int limiteMetaInformado) {

        static ListaResposta de(ConsultasDeCampanhaUseCases.Lista lista) {
            return new ListaResposta(
                    lista.pagina().itens().stream().map(CampanhaResposta::de).toList(),
                    lista.pagina().pagina(),
                    lista.pagina().tamanho(),
                    lista.pagina().total(),
                    new IndicadoresResposta(
                            lista.indicadores().enviadas(),
                            lista.indicadores().entregues(),
                            lista.indicadores().lidas(),
                            lista.indicadores().respondidas(),
                            lista.indicadores().falhas()),
                    lista.enfileiradasHoje(),
                    lista.tetoDiario(),
                    lista.limiteMetaInformado());
        }
    }

    record PreviaResposta(long total, long elegiveis, long excluidos, java.util.Map<String, Long> excluidosPorMotivo) {

        static PreviaResposta de(PublicoRepositorio.ContagemDoPublico contagem) {
            java.util.Map<String, Long> porMotivo = new java.util.LinkedHashMap<>();
            contagem.excluidosPorMotivo().entrySet().stream()
                    .sorted(java.util.Map.Entry.comparingByKey())
                    .forEach(e -> porMotivo.put(e.getKey().name(), e.getValue()));
            return new PreviaResposta(contagem.total(), contagem.elegiveis(), contagem.excluidos(), porMotivo);
        }
    }

    record ExcluidoResposta(UUID leadId, String nome, String telefone, String motivo) {

        static ExcluidoResposta de(PublicoRepositorio.Excluido excluido) {
            return new ExcluidoResposta(
                    excluido.leadId(), excluido.nome(), excluido.telefone(), excluido.motivo().name());
        }
    }

    record DiaProjetadoResposta(LocalDate dia, int mensagens, int limiteDoDia) {}

    record ProjecaoResposta(
            long destinatarios,
            List<DiaProjetadoResposta> dias,
            LocalDate terminoEstimado,
            boolean completa,
            int tetoDaInstancia,
            int limiteMetaInformado,
            int limiteEfetivoNoPrimeiroDia) {

        static ProjecaoResposta de(ProjetarEnvioUseCase.Saida saida) {
            return new ProjecaoResposta(
                    saida.destinatarios(),
                    saida.projecao().dias().stream()
                            .map(dia -> new DiaProjetadoResposta(dia.dia(), dia.mensagens(), dia.limiteDoDia()))
                            .toList(),
                    saida.projecao().terminoEstimado(),
                    saida.projecao().completa(),
                    saida.tetoDaInstancia(),
                    saida.limiteMetaInformado(),
                    saida.limiteEfetivoNoPrimeiroDia());
        }
    }

    record TemplateResposta(
            String id,
            String nome,
            String idioma,
            String categoria,
            String status,
            String corpo,
            int parametros,
            boolean elegivel,
            List<String> restricoes) {

        static TemplateResposta de(ConsultasDeCampanhaUseCases.TemplateParaCampanha template) {
            return new TemplateResposta(
                    template.id(),
                    template.nome(),
                    template.idioma(),
                    template.categoria(),
                    template.status(),
                    template.corpo(),
                    template.parametros(),
                    template.elegivel(),
                    template.restricoes());
        }
    }

    record DestinatarioResposta(
            UUID id,
            UUID leadId,
            String nome,
            String telefone,
            String status,
            String motivo,
            Integer codigoDeErro,
            Instant enviadoEm,
            Instant entregueEm,
            Instant lidoEm,
            Instant respondeuEm,
            Instant conferenciaEm) {

        static DestinatarioResposta de(ConsultasDeDestinatarios.Linha linha) {
            return new DestinatarioResposta(
                    linha.id(),
                    linha.leadId(),
                    linha.nome(),
                    linha.telefone(),
                    linha.status().name(),
                    linha.motivo() == null ? null : linha.motivo().name(),
                    linha.codigoDeErro(),
                    linha.enviadoEm(),
                    linha.entregueEm(),
                    linha.lidoEm(),
                    linha.respondeuEm(),
                    linha.conferenciaEm());
        }
    }

    record PaginaResposta<T>(List<T> itens, int pagina, int tamanho, long total) {

        static <O, T> PaginaResposta<T> de(Pagina<O> pagina, java.util.function.Function<O, T> conversor) {
            return new PaginaResposta<>(
                    pagina.itens().stream().map(conversor).toList(), pagina.pagina(), pagina.tamanho(), pagina.total());
        }
    }

    record ConfiguracaoResposta(
            boolean envioHabilitado,
            int tetoDiarioDaInstancia,
            int limiteDiarioPadrao,
            int limiteMetaInformado,
            int limiarDeFalhaPorCento,
            int janelaDeEnvios,
            int minimoDeAmostra,
            int conferenciaAposMinutos,
            int respondeuJanelaDias) {

        static ConfiguracaoResposta de(ConfiguracaoDeCampanhas.Parametros p) {
            return new ConfiguracaoResposta(
                    p.envioHabilitado(),
                    p.tetoDiarioDaInstancia(),
                    p.limiteDiarioPadrao(),
                    p.limiteMetaInformado(),
                    p.politicaDePausa().limiarDeFalhaPorCento(),
                    p.politicaDePausa().janelaDeEnvios(),
                    p.politicaDePausa().minimoDeAmostra(),
                    p.conferenciaAposMinutos(),
                    p.respondeuJanelaDias());
        }
    }

    record OptOutResposta(UUID leadId, String nome, String telefone, Instant desde, String origem, String motivo) {

        static OptOutResposta de(OptOutRepositorio.Registro registro) {
            return new OptOutResposta(
                    registro.leadId(),
                    registro.nome(),
                    registro.telefone(),
                    registro.desde(),
                    registro.origem().name(),
                    registro.motivo());
        }
    }

    record TesteResposta(UUID leadId, UUID mensagemId, Instant enviadoEm, String corpoRenderizado) {}
}
