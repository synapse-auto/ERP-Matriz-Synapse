package com.synapse.crm.atendimento.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.synapse.crm.atendimento.application.canal.CanalCredencialAtivaRepositorio;
import com.synapse.crm.atendimento.application.participacao.ParticipacaoAtendimentoRepositorio;
import com.synapse.crm.atendimento.domain.atendimento.Atendimento;
import com.synapse.crm.atendimento.domain.canal.CanalGateway;
import com.synapse.crm.atendimento.domain.canal.ConteudoDeEnvio;
import com.synapse.crm.atendimento.domain.canal.ForaDaJanelaException;
import com.synapse.crm.atendimento.domain.mensagem.Mensagem;
import com.synapse.crm.atendimento.domain.mensagem.Remetente;
import com.synapse.crm.core.application.lead.LeadNoCaminhoDeMensagem;
import com.synapse.crm.core.domain.lead.TelefoneCanonico;
import com.synapse.crm.core.domain.lead.TelefoneInvalidoException;
import com.synapse.crm.sharedkernel.identidade.PapelUsuario;
import com.synapse.crm.sharedkernel.identidade.UsuarioAutenticado;
import com.synapse.crm.sharedkernel.identidade.UsuarioContext;

class IniciarNovoContatoUseCaseTest {

    private static final Instant AGORA = Instant.parse("2026-08-28T15:00:00Z");
    private static final String TELEFONE_MASCARA = "(83) 99999-1111";
    private static final String TELEFONE_CANONICO = "5583999991111";

    private LeadNoCaminhoDeMensagem leads;
    private AtendimentoRepositorio atendimentos;
    private EnviarMensagemUseCase enviar;
    private CanalGateway canal;
    private CanalCredencialAtivaRepositorio canaisAtivos;
    private UsuarioContext usuarioContext;
    private ParticipacaoAtendimentoRepositorio participacoes;
    private UUID quemPediu;
    private IniciarNovoContatoUseCase useCase;

    @BeforeEach
    void preparar() {
        leads = mock(LeadNoCaminhoDeMensagem.class);
        atendimentos = mock(AtendimentoRepositorio.class);
        enviar = mock(EnviarMensagemUseCase.class);
        canal = mock(CanalGateway.class);
        canaisAtivos = mock(CanalCredencialAtivaRepositorio.class);
        participacoes = mock(ParticipacaoAtendimentoRepositorio.class);
        usuarioContext = mock(UsuarioContext.class);
        quemPediu = UUID.randomUUID();
        when(usuarioContext.atual())
                .thenReturn(new UsuarioAutenticado(quemPediu, PapelUsuario.ATENDENTE, false));
        when(canaisAtivos.primeiraAtiva()).thenReturn(Optional.empty());
        useCase = new IniciarNovoContatoUseCase(
                leads,
                atendimentos,
                enviar,
                canal,
                canaisAtivos,
                new TelefoneCanonico("55"),
                usuarioContext,
                Clock.fixed(AGORA, ZoneOffset.UTC),
                participacoes);
    }

    @Test
    void contatoNovoSemMensagem_criaLeadEAbreAtendimentoHumano() {
        UUID leadId = UUID.randomUUID();
        when(leads.visivelPorTelefone(TELEFONE_CANONICO)).thenReturn(Optional.empty());
        when(leads.criarParaAtendente(eq("Maria"), eq(TELEFONE_CANONICO), eq(quemPediu), isNull()))
                .thenReturn(Optional.of(leadId));
        when(leads.assumirSeSemDono(leadId, quemPediu)).thenReturn(LeadNoCaminhoDeMensagem.Assuncao.assumido(quemPediu));
        when(atendimentos.abertoDoLead(leadId)).thenReturn(Optional.empty());
        when(atendimentos.salvar(any(Atendimento.class))).thenAnswer(invocacao -> invocacao.getArgument(0));

        IniciarNovoContatoUseCase.Resultado resultado =
                useCase.executar(new IniciarNovoContatoUseCase.Pedido("Maria", TELEFONE_MASCARA, null, null));

        assertThat(resultado.leadId()).isEqualTo(leadId);
        assertThat(resultado.leadCriado()).isTrue();
        assertThat(resultado.mensagem()).isNull();
        assertThat(resultado.atendimento().pertenceA(quemPediu)).isTrue();
        verify(enviar, never()).executar(any(UUID.class), any(String.class));
        verify(enviar, never()).executar(any(UUID.class), any(ConteudoDeEnvio.class));
        verify(leads, never()).registrarInteracao(any(), any(), anyInt(), anyInt());
    }

    @Test
    void leadProprioSemMensagem_reusaAtendimentoAberto() {
        UUID leadId = UUID.randomUUID();
        Atendimento aberto = Atendimento.abrirComIa(UUID.randomUUID(), leadId, null, null, AGORA)
                .transferirPara(quemPediu);
        when(leads.visivelPorTelefone(TELEFONE_CANONICO)).thenReturn(Optional.of(leadId));
        when(leads.contatoParaEnvio(leadId)).thenReturn(Optional.empty());
        when(leads.assumirSeSemDono(leadId, quemPediu)).thenReturn(LeadNoCaminhoDeMensagem.Assuncao.preservado(quemPediu));
        when(atendimentos.abertoDoLead(leadId)).thenReturn(Optional.of(aberto));

        IniciarNovoContatoUseCase.Resultado resultado =
                useCase.executar(new IniciarNovoContatoUseCase.Pedido("Maria", TELEFONE_MASCARA, "  ", null));

        assertThat(resultado.leadCriado()).isFalse();
        assertThat(resultado.atendimento().id()).isEqualTo(aberto.id());
        verify(leads, never()).criarParaAtendente(any(), any(), any(), any());
        verify(enviar, never()).executar(any(UUID.class), any(String.class));
    }

    @Test
    void leadExistenteSemAberto_criaAtendimentoHumanoSemEnviarMesmoForaDaJanela() {
        UUID leadId = UUID.randomUUID();
        when(leads.assumirSeSemDono(leadId, quemPediu))
                .thenReturn(LeadNoCaminhoDeMensagem.Assuncao.preservado(quemPediu));
        when(atendimentos.abertoDoLead(leadId)).thenReturn(Optional.empty());
        when(atendimentos.salvar(any(Atendimento.class)))
                .thenAnswer(invocacao -> invocacao.getArgument(0));

        IniciarNovoContatoUseCase.Resultado resultado = useCase.abrirParaLeadExistente(leadId);

        assertThat(resultado.leadId()).isEqualTo(leadId);
        assertThat(resultado.leadCriado()).isFalse();
        assertThat(resultado.mensagem()).isNull();
        assertThat(resultado.atendimento().pertenceA(quemPediu)).isTrue();
        assertThat(resultado.atendimento().status().name()).isEqualTo("EM_ATENDIMENTO");
        verify(canal, never()).aceitaTextoLivre(any(), any());
        verify(enviar, never()).executar(any(UUID.class), any(String.class));
        verify(enviar, never()).executar(any(UUID.class), any(ConteudoDeEnvio.class));
    }

    @Test
    void leadExistenteInvisivel_vira404SemConsultarOuCriarAtendimento() {
        UUID leadId = UUID.randomUUID();
        when(leads.assumirSeSemDono(leadId, quemPediu))
                .thenReturn(LeadNoCaminhoDeMensagem.Assuncao.naoAlcancado());

        assertThatThrownBy(() -> useCase.abrirParaLeadExistente(leadId))
                .isInstanceOf(ContatoIndisponivelParaInicioException.class)
                .hasMessage("Numero indisponivel para iniciar atendimento. Procure a gestao.");

        verify(atendimentos, never()).abertoDoLead(any());
        verify(atendimentos, never()).salvar(any());
    }

    @Test
    void telefoneDeColega_vira404SemDistinguirExistencia() {
        when(leads.visivelPorTelefone(TELEFONE_CANONICO)).thenReturn(Optional.empty());
        when(leads.criarParaAtendente(eq("Maria"), eq(TELEFONE_CANONICO), eq(quemPediu), isNull()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                        useCase.executar(new IniciarNovoContatoUseCase.Pedido("Maria", TELEFONE_MASCARA, null, null)))
                .isInstanceOf(ContatoIndisponivelParaInicioException.class)
                .hasMessage("Numero indisponivel para iniciar atendimento. Procure a gestao.");
        verify(enviar, never()).executar(any(UUID.class), any(String.class));
    }

    @Test
    void textoLivreForaDaJanela_emContatoNovo_falhaAntesDeCriarLead() {
        when(leads.visivelPorTelefone(TELEFONE_CANONICO)).thenReturn(Optional.empty());
        when(canal.aceitaTextoLivre(Optional.empty(), AGORA)).thenReturn(false);

        assertThatThrownBy(() -> useCase.executar(
                        new IniciarNovoContatoUseCase.Pedido("Maria", TELEFONE_MASCARA, "ola", null)))
                .isInstanceOf(ForaDaJanelaException.class);
        verify(leads, never()).criarParaAtendente(any(), any(), any(), any());
        verify(enviar, never()).executar(any(UUID.class), any(String.class));
    }

    @Test
    void templateForaDaJanela_eEnviadoSemCriarTextoLivre() {
        UUID leadId = UUID.randomUUID();
        Atendimento aberto = Atendimento.abrirComIa(UUID.randomUUID(), leadId, null, null, AGORA)
                .transferirPara(quemPediu);
        Mensagem mensagem = Mensagem.texto(
                UUID.randomUUID(), aberto.id(), Remetente.atendente(quemPediu), "template", AGORA);
        when(leads.visivelPorTelefone(TELEFONE_CANONICO)).thenReturn(Optional.empty());
        when(leads.criarParaAtendente(eq("Maria"), eq(TELEFONE_CANONICO), eq(quemPediu), isNull()))
                .thenReturn(Optional.of(leadId));
        when(leads.assumirSeSemDono(leadId, quemPediu)).thenReturn(LeadNoCaminhoDeMensagem.Assuncao.assumido(quemPediu));
        when(enviar.executar(eq(leadId), any(ConteudoDeEnvio.MensagemTemplate.class)))
                .thenReturn(new EnviarMensagemUseCase.Resultado(aberto, mensagem, true));
        when(canal.aceitaTextoLivre(any(), any())).thenReturn(false);

        IniciarNovoContatoUseCase.Resultado resultado = useCase.executar(new IniciarNovoContatoUseCase.Pedido(
                "Maria",
                TELEFONE_MASCARA,
                null,
                new IniciarNovoContatoUseCase.Pedido.Template("hello_world", "pt_BR", List.of("Maria"))));

        assertThat(resultado.leadCriado()).isTrue();
        assertThat(resultado.mensagem()).isEqualTo(mensagem);
        verify(enviar).executar(eq(leadId), any(ConteudoDeEnvio.MensagemTemplate.class));
        verify(canal, never()).aceitaTextoLivre(any(), any());
    }

    @Test
    void textoLivreETemplateJuntos_saoRecusados() {
        assertThatThrownBy(() -> useCase.executar(new IniciarNovoContatoUseCase.Pedido(
                        "Maria",
                        TELEFONE_MASCARA,
                        "ola",
                        new IniciarNovoContatoUseCase.Pedido.Template("hello_world", "pt_BR", List.of()))))
                .isInstanceOf(PedidoDeNovoContatoInvalidoException.class)
                .hasMessageContaining("nao os dois");
        verify(leads, never()).criarParaAtendente(any(), any(), any(), any());
    }

    @Test
    void telefoneIlegivel_propagaExcecaoDeDominio() {
        assertThatThrownBy(() ->
                        useCase.executar(new IniciarNovoContatoUseCase.Pedido("Maria", "123", null, null)))
                .isInstanceOf(TelefoneInvalidoException.class);
    }

    @Test
    void nomeEmBranco_ePedidoInvalido() {
        assertThatThrownBy(() ->
                        useCase.executar(new IniciarNovoContatoUseCase.Pedido("  ", TELEFONE_MASCARA, null, null)))
                .isInstanceOf(PedidoDeNovoContatoInvalidoException.class)
                .hasMessageContaining("nome");
    }
}
