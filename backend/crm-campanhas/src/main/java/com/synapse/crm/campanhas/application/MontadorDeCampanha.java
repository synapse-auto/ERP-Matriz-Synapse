package com.synapse.crm.campanhas.application;

import java.time.Instant;
import java.time.LocalTime;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.synapse.crm.atendimento.domain.canal.TemplateDoCanal;
import com.synapse.crm.campanhas.domain.Campanha;
import com.synapse.crm.campanhas.domain.CampanhaInvalidaException;
import com.synapse.crm.campanhas.domain.DiasDaSemana;
import com.synapse.crm.campanhas.domain.FiltroDePublico;
import com.synapse.crm.campanhas.domain.JanelaDeEnvio;
import com.synapse.crm.campanhas.domain.MapeamentoDeVariaveis;

/**
 * Monta uma campanha em rascunho a partir do pedido do assistente: busca o template no provedor (rede, fora
 * de transacao), exige que esteja aprovado e seja suportado, e aplica os padroes da instancia.
 */
@Component
class MontadorDeCampanha {

    static final int RITMO_PADRAO_POR_MINUTO = 20;
    static final JanelaDeEnvio JANELA_PADRAO =
            new JanelaDeEnvio(LocalTime.of(9, 0), LocalTime.of(18, 0), DiasDaSemana.DIAS_UTEIS);

    private final TemplatesDoCanal templates;
    private final ConfiguracaoDeCampanhas configuracao;
    private final TransacoesDeCampanha transacoes;

    MontadorDeCampanha(
            TemplatesDoCanal templates, ConfiguracaoDeCampanhas configuracao, TransacoesDeCampanha transacoes) {
        this.templates = templates;
        this.configuracao = configuracao;
        this.transacoes = transacoes;
    }

    Campanha montar(UUID id, PedidoDeCampanha pedido, UUID criadaPor, Instant criadaEm) {
        ConfiguracaoDeCampanhas.Parametros parametros = transacoes.noChatSomenteLeitura(configuracao::atuais);
        TemplateDoCanal template = exigirTemplateUtilizavel(pedido.templateNome(), pedido.templateIdioma());
        int teto = parametros.tetoDiarioDaInstancia();
        int limite = Optional.ofNullable(pedido.limiteDiario()).orElse(Math.min(parametros.limiteDiarioPadrao(), teto));
        return Campanha.rascunho(
                id,
                pedido.nome(),
                new Campanha.TemplateSnapshot(
                        template.id(),
                        template.nome(),
                        template.idioma(),
                        template.categoria().name(),
                        template.corpo(),
                        template.quantidadeDeParametros()),
                pedido.mapeamento() == null ? MapeamentoDeVariaveis.vazio() : pedido.mapeamento(),
                pedido.filtro() == null ? FiltroDePublico.agendaInteira() : pedido.filtro(),
                limite,
                teto,
                pedido.janela() == null ? JANELA_PADRAO : pedido.janela(),
                Optional.ofNullable(pedido.ritmoPorMinuto()).orElse(RITMO_PADRAO_POR_MINUTO),
                pedido.rampa(),
                pedido.agendadaPara(),
                criadaPor,
                criadaEm);
    }

    /** Aprovado e so com variaveis de corpo; usado ao criar e de novo ao iniciar (o status muda no provedor). */
    TemplateDoCanal exigirTemplateUtilizavel(String nome, String idioma) {
        TemplateDoCanal template = templates.buscar(nome, idioma)
                .orElseThrow(() -> new CampanhaInvalidaException(
                        "TEMPLATE_NAO_ENCONTRADO", "o template " + nome + " (" + idioma + ") nao existe mais na conta"));
        if (template.status() != TemplateDoCanal.Status.APROVADO) {
            throw new CampanhaInvalidaException(
                    "TEMPLATE_NAO_APROVADO", "o template " + nome + " esta " + template.status() + ", nao aprovado");
        }
        if (!template.recursos().suportadoEmCampanha()) {
            throw new CampanhaInvalidaException(
                    "TEMPLATE_NAO_SUPORTADO",
                    "o template " + nome + " usa recursos nao suportados nesta versao: " + descreverRecursos(template));
        }
        return template;
    }

    private static String descreverRecursos(TemplateDoCanal template) {
        TemplateDoCanal.RecursosAlemDoCorpo recursos = template.recursos();
        StringBuilder motivos = new StringBuilder();
        anexar(motivos, recursos.cabecalhoDeMidia(), "cabecalho_de_midia");
        anexar(motivos, recursos.cabecalhoComVariavel(), "cabecalho_com_variavel");
        anexar(motivos, recursos.botaoComParametro(), "botao_com_parametro");
        anexar(motivos, recursos.outroComponente(), "outro_componente");
        return motivos.toString();
    }

    private static void anexar(StringBuilder motivos, boolean presente, String codigo) {
        if (presente) {
            motivos.append(motivos.isEmpty() ? "" : ",").append(codigo);
        }
    }
}
