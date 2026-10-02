package com.synapse.crm.campanhas.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import com.synapse.crm.atendimento.application.campanha.EnfileirarTemplateDeCampanhaUseCase;
import com.synapse.crm.campanhas.domain.Campanha;
import com.synapse.crm.campanhas.domain.CampanhaInvalidaException;
import com.synapse.crm.campanhas.domain.CampoDoLead;
import com.synapse.crm.campanhas.domain.CorpoDoTemplate;
import com.synapse.crm.core.application.lead.LeadNoCaminhoDeMensagem;
import com.synapse.crm.core.domain.lead.TelefoneCanonico;
import com.synapse.crm.core.domain.lead.TelefoneInvalidoException;
import com.synapse.crm.sharedkernel.auditoria.Auditable;
import com.synapse.crm.sharedkernel.identidade.ContextoDeServico;

/**
 * Envia o template da campanha para UM numero informado, para a pessoa ver como chega antes de disparar.
 *
 * <p>Nao conta no limite diario e nao passa pela politica proativa. Protecao contra uso indevido: so vai para
 * um contato JA cadastrado na Agenda e exige a confirmacao explicita de que o dono do numero autorizou
 * receber o teste. Nunca para um numero avulso digitado.
 */
@Service
public class EnviarTesteDeCampanhaUseCase {

    private final CampanhaRepositorio campanhas;
    private final MontadorDeCampanha montador;
    private final PublicoRepositorio publico;
    private final EnfileirarTemplateDeCampanhaUseCase enfileirar;
    private final LeadNoCaminhoDeMensagem leads;
    private final TelefoneCanonico telefoneCanonico;
    private final DisponibilidadeDeCampanhas disponibilidade;
    private final TransacoesDeCampanha transacoes;

    public EnviarTesteDeCampanhaUseCase(
            CampanhaRepositorio campanhas,
            MontadorDeCampanha montador,
            PublicoRepositorio publico,
            EnfileirarTemplateDeCampanhaUseCase enfileirar,
            LeadNoCaminhoDeMensagem leads,
            TelefoneCanonico telefoneCanonico,
            DisponibilidadeDeCampanhas disponibilidade,
            TransacoesDeCampanha transacoes) {
        this.campanhas = campanhas;
        this.montador = montador;
        this.publico = publico;
        this.enfileirar = enfileirar;
        this.leads = leads;
        this.telefoneCanonico = telefoneCanonico;
        this.disponibilidade = disponibilidade;
        this.transacoes = transacoes;
    }

    public record Resultado(UUID leadId, UUID mensagemId, Instant enviadoEm, String corpoRenderizado) {}

    @PreAuthorize(PermissoesDeCampanha.ESCRITA)
    @Auditable(acao = "ENVIAR_TESTE_CAMPANHA", entidadeTipo = "CAMPANHA", capturarDados = false)
    public Resultado executar(UUID campanhaId, String telefone, boolean destinatarioAutorizou) {
        disponibilidade.exigir();
        if (!destinatarioAutorizou) {
            throw new CampanhaInvalidaException(
                    "TESTE_SEM_AUTORIZACAO", "confirme que o dono do numero autorizou receber o teste");
        }
        Campanha campanha = transacoes.noChatSomenteLeitura(() -> campanhas.porId(campanhaId))
                .orElseThrow(() -> new CampanhaNaoEncontradaException(campanhaId));
        montador.exigirTemplateUtilizavel(campanha.template().nome(), campanha.template().idioma());
        String canonico = canonico(telefone);
        return transacoes.noChat(() -> ContextoDeServico.buscarComo("teste-de-campanha", () -> {
            UUID leadId = leads.visivelPorTelefone(canonico)
                    .orElseThrow(() -> new CampanhaInvalidaException(
                            "CONTATO_DE_TESTE_NAO_CADASTRADO",
                            "o numero precisa estar na Agenda; cadastre o contato de teste primeiro"));
            CampoDoLead.Dados dados = publico.dadosDoLead(leadId)
                    .orElseThrow(() -> new CampanhaInvalidaException("CONTATO_DE_TESTE_NAO_CADASTRADO", "contato indisponivel"));
            List<String> parametros = campanha.mapeamento().resolver(dados);
            String corpo = CorpoDoTemplate.renderizar(campanha.template().corpo(), parametros);
            EnfileirarTemplateDeCampanhaUseCase.Resultado resultado = enfileirar.executar(
                    new EnfileirarTemplateDeCampanhaUseCase.Pedido(
                            campanhaId,
                            leadId,
                            campanha.template().nome(),
                            campanha.template().idioma(),
                            parametros,
                            corpo,
                            true));
            return switch (resultado) {
                case EnfileirarTemplateDeCampanhaUseCase.Resultado.Enfileirado enfileirado ->
                    new Resultado(leadId, enfileirado.mensagemId(), enfileirado.enviadoEm(), corpo);
                case EnfileirarTemplateDeCampanhaUseCase.Resultado.Recusado recusado ->
                    throw new CampanhaInvalidaException(
                            "TESTE_RECUSADO", "o teste nao pode ser enviado: " + recusado.motivo());
            };
        }));
    }

    private String canonico(String telefone) {
        try {
            String normalizado = telefoneCanonico.normalizar(telefone);
            if (normalizado == null) {
                throw new CampanhaInvalidaException("TELEFONE_INVALIDO", "informe o telefone do contato de teste");
            }
            return normalizado;
        } catch (TelefoneInvalidoException invalido) {
            throw new CampanhaInvalidaException("TELEFONE_INVALIDO", "telefone invalido para o contato de teste");
        }
    }
}
