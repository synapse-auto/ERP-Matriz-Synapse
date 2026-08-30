package com.synapse.crm.atendimento.application.template;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import com.synapse.crm.atendimento.domain.canal.CanalGateway;
import com.synapse.crm.atendimento.domain.canal.PedidoDeTemplate;
import com.synapse.crm.atendimento.domain.canal.ResultadoDeTemplate;
import com.synapse.crm.atendimento.domain.canal.TemplateDoCanal;

class CriarTemplateWhatsAppUseCaseTest {

    @Test
    void rejeitaNomeComEspacoAlemDoPermitidoPelaMeta() {
        CriarTemplateWhatsAppUseCase useCase = new CriarTemplateWhatsAppUseCase(mock(CanalGateway.class));

        assertThatThrownBy(() -> useCase.executar(
                        "Oi Cliente!", "pt_BR", TemplateDoCanal.Categoria.UTILIDADE, "Ola"))
                .isInstanceOf(PedidoDeTemplateInvalidoException.class);
    }

    @Test
    void rejeitaVariaveisForaDeOrdem() {
        CriarTemplateWhatsAppUseCase useCase = new CriarTemplateWhatsAppUseCase(mock(CanalGateway.class));

        assertThatThrownBy(() -> useCase.executar(
                        "retorno", "pt_BR", TemplateDoCanal.Categoria.UTILIDADE, "Ola {{1}} e {{3}}"))
                .isInstanceOf(PedidoDeTemplateInvalidoException.class)
                .hasMessageContaining("{{2}}")
                .hasMessageContaining("ausente");
    }

    @Test
    void rejeitaIndiceZero() {
        CriarTemplateWhatsAppUseCase useCase = new CriarTemplateWhatsAppUseCase(mock(CanalGateway.class));

        assertThatThrownBy(() -> useCase.executar(
                        "retorno", "pt_BR", TemplateDoCanal.Categoria.UTILIDADE, "Ola {{0}}"))
                .isInstanceOf(PedidoDeTemplateInvalidoException.class)
                .hasMessageContaining("{{0}}")
                .hasMessageContaining("invalido");
    }

    @Test
    void aceitaQuatroVariaveisSequenciaisMesmoComRepeticaoDoMesmoIndice() {
        CanalGateway canal = mock(CanalGateway.class);
        TemplateDoCanal criado = new TemplateDoCanal(
                "retorno",
                "pt_BR",
                TemplateDoCanal.Categoria.UTILIDADE,
                TemplateDoCanal.Status.PENDENTE,
                "Ola {{1}}, {{2}}, {{3}} e {{4}}. {{1}} de novo.",
                4);
        when(canal.criarTemplate(any(PedidoDeTemplate.class)))
                .thenReturn(new ResultadoDeTemplate.Aceito(criado));

        TemplateDoCanal resultado = new CriarTemplateWhatsAppUseCase(canal)
                .executar(
                        "retorno",
                        "pt_BR",
                        TemplateDoCanal.Categoria.UTILIDADE,
                        "Ola {{1}}, {{2}}, {{3}} e {{4}}. {{1}} de novo.");

        assertThat(resultado.quantidadeDeParametros()).isEqualTo(4);
    }

    @Test
    void devolveOTemplateQuandoOProvedorAceita() {
        CanalGateway canal = mock(CanalGateway.class);
        TemplateDoCanal criado = new TemplateDoCanal(
                "retorno",
                "pt_BR",
                TemplateDoCanal.Categoria.UTILIDADE,
                TemplateDoCanal.Status.PENDENTE,
                "Ola {{1}}",
                1);
        when(canal.criarTemplate(any(PedidoDeTemplate.class)))
                .thenReturn(new ResultadoDeTemplate.Aceito(criado));

        TemplateDoCanal resultado = new CriarTemplateWhatsAppUseCase(canal)
                .executar("Retorno", "pt_BR", TemplateDoCanal.Categoria.UTILIDADE, "Ola {{1}}");

        assertThat(resultado.status()).isEqualTo(TemplateDoCanal.Status.PENDENTE);
        assertThat(resultado.nome()).isEqualTo("retorno");
    }
}
