package com.synapse.crm.campanhas.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.atendimento.domain.canal.TemplateDoCanal;
import com.synapse.crm.campanhas.application.CampanhaRepositorio.DiaEnfileirado;
import com.synapse.crm.campanhas.application.CampanhaRepositorio.Indicadores;
import com.synapse.crm.campanhas.application.ConsultasDeDestinatarios.Filtro;
import com.synapse.crm.campanhas.application.ConsultasDeDestinatarios.Linha;
import com.synapse.crm.campanhas.domain.Campanha;
import com.synapse.crm.campanhas.domain.MotivoDoDestinatario;
import com.synapse.crm.campanhas.domain.PlanoDeLimite;
import com.synapse.crm.campanhas.domain.StatusDoDestinatario;
import com.synapse.crm.sharedkernel.persistencia.Pools;

/** Leituras da tela de campanhas: lista, detalhe, destinatarios, conferencia e templates. */
@Service
public class ConsultasDeCampanhaUseCases {

    private final CampanhaRepositorio campanhas;
    private final ConsultasDeDestinatarios destinatarios;
    private final TemplatesDoCanal templates;
    private final ConfiguracaoDeCampanhas configuracao;
    private final DisponibilidadeDeCampanhas disponibilidade;
    private final Clock relogio;
    private final ZoneId fuso;

    public ConsultasDeCampanhaUseCases(
            CampanhaRepositorio campanhas,
            ConsultasDeDestinatarios destinatarios,
            TemplatesDoCanal templates,
            ConfiguracaoDeCampanhas configuracao,
            DisponibilidadeDeCampanhas disponibilidade,
            Clock relogio,
            ZoneId fuso) {
        this.campanhas = campanhas;
        this.destinatarios = destinatarios;
        this.templates = templates;
        this.configuracao = configuracao;
        this.disponibilidade = disponibilidade;
        this.relogio = relogio;
        this.fuso = fuso;
    }

    /**
     * @param limiteEfetivoHoje o que a campanha pode enfileirar hoje: limite com rampa, no maximo o teto
     * @param enfileiradasHojeNaInstancia quanto o dia ja consumiu somando todas as campanhas
     */
    public record Detalhe(
            Campanha campanha,
            List<DiaEnfileirado> porDia,
            int limiteEfetivoHoje,
            int tetoDaInstancia,
            int limiteMetaInformado,
            int enfileiradasHojeNaInstancia) {}

    public record Lista(
            Pagina<Campanha> pagina,
            Indicadores indicadores,
            int enfileiradasHoje,
            int tetoDiario,
            int limiteMetaInformado) {}

    /** Template como o assistente o mostra: com o que impede de usar, quando impede. */
    public record TemplateParaCampanha(
            String id,
            String nome,
            String idioma,
            String categoria,
            String status,
            String corpo,
            int parametros,
            boolean elegivel,
            List<String> restricoes) {}

    @PreAuthorize(PermissoesDeCampanha.LEITURA)
    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER, readOnly = true)
    public Detalhe obter(UUID id) {
        disponibilidade.exigir();
        Campanha campanha = campanhas.porId(id).orElseThrow(() -> new CampanhaNaoEncontradaException(id));
        ConfiguracaoDeCampanhas.Parametros parametros = configuracao.atuais();
        ZonedDateTime agora = ZonedDateTime.ofInstant(Instant.now(relogio), fuso);
        long dias = campanha.iniciadaEm() == null
                ? 0
                : PlanoDeLimite.diasDesdeOInicio(campanha.iniciadaEm().atZone(fuso), agora);
        int limiteHoje = PlanoDeLimite.limiteDoDia(
                campanha.limiteDiario(), campanha.rampa(), dias, parametros.tetoDiarioDaInstancia());
        return new Detalhe(
                campanha,
                campanhas.enfileiradasPorDia(id),
                limiteHoje,
                parametros.tetoDiarioDaInstancia(),
                parametros.limiteMetaInformado(),
                campanhas.enfileiradasNoDia(agora.toLocalDate()));
    }

    @PreAuthorize(PermissoesDeCampanha.LEITURA)
    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER, readOnly = true)
    public Lista listar(int pagina, int tamanho) {
        disponibilidade.exigir();
        int paginaSegura = Pagina.paginaSegura(pagina);
        int tamanhoSeguro = Pagina.tamanhoSeguro(tamanho);
        ConfiguracaoDeCampanhas.Parametros parametros = configuracao.atuais();
        LocalDate hoje = LocalDate.ofInstant(Instant.now(relogio), fuso);
        return new Lista(
                new Pagina<>(
                        campanhas.listar(paginaSegura, tamanhoSeguro), paginaSegura, tamanhoSeguro, campanhas.contar()),
                campanhas.indicadores(),
                campanhas.enfileiradasNoDia(hoje),
                parametros.tetoDiarioDaInstancia(),
                parametros.limiteMetaInformado());
    }

    @PreAuthorize(PermissoesDeCampanha.LEITURA)
    @Transactional(readOnly = true)
    public Pagina<Linha> destinatarios(
            UUID campanhaId, StatusDoDestinatario status, MotivoDoDestinatario motivo, int pagina, int tamanho) {
        disponibilidade.exigir();
        return destinatarios.listar(
                new Filtro(campanhaId, status, motivo, false), Pagina.paginaSegura(pagina), Pagina.tamanhoSeguro(tamanho));
    }

    /** Aba "Conferencia manual": enfileirados sem confirmacao e falhas cujo resultado nao se sabe. */
    @PreAuthorize(PermissoesDeCampanha.LEITURA)
    @Transactional(readOnly = true)
    public Pagina<Linha> conferencia(UUID campanhaId, int pagina, int tamanho) {
        disponibilidade.exigir();
        return destinatarios.listar(
                new Filtro(campanhaId, null, null, true), Pagina.paginaSegura(pagina), Pagina.tamanhoSeguro(tamanho));
    }

    /** Chama o provedor (cache curto); sem transacao aberta durante a rede. */
    @PreAuthorize(PermissoesDeCampanha.LEITURA)
    public List<TemplateParaCampanha> templates() {
        disponibilidade.exigir();
        return templates.listar().stream().map(ConsultasDeCampanhaUseCases::paraTela).toList();
    }

    private static TemplateParaCampanha paraTela(TemplateDoCanal template) {
        TemplateDoCanal.RecursosAlemDoCorpo recursos = template.recursos();
        java.util.ArrayList<String> restricoes = new java.util.ArrayList<>();
        if (template.categoria() == TemplateDoCanal.Categoria.AUTENTICACAO) {
            restricoes.add("CATEGORIA_AUTENTICACAO");
        }
        if (recursos.cabecalhoDeMidia()) {
            restricoes.add("CABECALHO_DE_MIDIA");
        }
        if (recursos.cabecalhoComVariavel()) {
            restricoes.add("CABECALHO_COM_VARIAVEL");
        }
        if (recursos.botaoComParametro()) {
            restricoes.add("BOTAO_COM_PARAMETRO");
        }
        if (recursos.outroComponente()) {
            restricoes.add("OUTRO_COMPONENTE");
        }
        boolean elegivel = template.status() == TemplateDoCanal.Status.APROVADO && restricoes.isEmpty();
        return new TemplateParaCampanha(
                template.id(),
                template.nome(),
                template.idioma(),
                template.categoria().name(),
                template.status().name(),
                template.corpo(),
                template.quantidadeDeParametros(),
                elegivel,
                List.copyOf(restricoes));
    }
}
