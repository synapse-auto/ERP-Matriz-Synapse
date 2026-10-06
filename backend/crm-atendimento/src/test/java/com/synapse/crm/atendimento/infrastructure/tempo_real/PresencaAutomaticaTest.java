package com.synapse.crm.atendimento.infrastructure.tempo_real;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.user.SimpSession;
import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.messaging.simp.user.SimpUserRegistry;

import com.synapse.crm.equipe.application.usuario.AlterarPresencaPeloSistemaUseCase;
import com.synapse.crm.equipe.application.usuario.ConfiguracaoDePresencaAutomatica;
import com.synapse.crm.equipe.domain.usuario.MudancaDePresenca;
import com.synapse.crm.equipe.domain.usuario.StatusPresenca;
import com.synapse.crm.sharedkernel.identidade.ContextoDeServico;

/**
 * A maquina de estados da presenca automatica com relogio controlado: sem esperar tempo real, cobre cada regra e as
 * armadilhas (renovacao de token, deploy, instancia velha, escolha manual).
 */
class PresencaAutomaticaTest {

    private static final Duration TOLERANCIA = Duration.ofSeconds(90);
    private static final Duration CARENCIA = Duration.ofSeconds(120);
    private static final Duration VARREDURA = Duration.ofSeconds(15);
    private static final Set<StatusPresenca> PROMOVIVEIS = EnumSet.of(StatusPresenca.OFFLINE, StatusPresenca.AUSENTE);
    private static final Set<StatusPresenca> PROMOVIVEIS_NA_CARENCIA = EnumSet.of(StatusPresenca.OFFLINE);
    private static final Set<StatusPresenca> REBAIXAVEIS = EnumSet.of(StatusPresenca.ONLINE, StatusPresenca.AUSENTE);

    private static final UUID JOANNA = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID DEBORA = UUID.fromString("00000000-0000-0000-0000-00000000000b");

    private final RelogioMutavel relogio = new RelogioMutavel(Instant.parse("2026-10-06T12:00:00Z"));
    private final SimpUserRegistry registro = mock(SimpUserRegistry.class);
    private final AlterarPresencaPeloSistemaUseCase presenca = mock(AlterarPresencaPeloSistemaUseCase.class);
    private final ConfiguracaoDePresencaAutomatica configuracao = mock(ConfiguracaoDePresencaAutomatica.class);
    private final PublicadorDeAvisoDePresenca avisos = mock(PublicadorDeAvisoDePresenca.class);

    private PresencaAutomatica automatica;

    @BeforeAll
    static void instalarPonteDeAutoridadeVazia() {
        ContextoDeServico.instalarPonteDeAutoridade(nome -> () -> {});
    }

    @AfterAll
    static void restaurarPonte() {
        ContextoDeServico.instalarPonteDeAutoridade(ContextoDeServico.PonteDeAutoridade.NAO_INSTALADA);
    }

    @BeforeEach
    void preparar() {
        when(configuracao.habilitada()).thenReturn(true);
        when(presenca.executar(any(), any(), any(), any())).thenAnswer(chamada -> {
            StatusPresenca novo = chamada.getArgument(1);
            StatusPresenca anterior = novo == StatusPresenca.ONLINE ? StatusPresenca.OFFLINE : StatusPresenca.ONLINE;
            return Optional.of(new MudancaDePresenca(anterior, novo));
        });
        automatica = nova(CARENCIA);
        // Fora da carencia por padrao: o relogio avanca alem dela antes de cada teste que nao a quer.
        relogio.avancar(CARENCIA.plusSeconds(1));
    }

    // --- chave --------------------------------------------------------------------------------------------------

    @Test
    void comAChaveDesligadaNadaEGravadoEmConexaoNemDesconexao() {
        when(configuracao.habilitada()).thenReturn(false);
        automatica = nova(Duration.ZERO);

        automatica.usuarioConectou(JOANNA);
        automatica.usuarioDesconectou(JOANNA, "s1");
        relogio.avancar(TOLERANCIA.plusSeconds(1));
        automatica.varrer();

        verifyNoInteractions(presenca, avisos);
    }

    @Test
    void ligarAChaveDepoisValeNaProximaLeituraSemReiniciar() {
        when(configuracao.habilitada()).thenReturn(false);
        automatica = nova(Duration.ZERO);
        automatica.usuarioConectou(JOANNA);
        verifyNoInteractions(presenca);

        when(configuracao.habilitada()).thenReturn(true);
        relogio.avancar(VARREDURA.plusSeconds(1));
        automatica.usuarioConectou(DEBORA);

        verify(presenca).executar(DEBORA, StatusPresenca.ONLINE, "CONEXAO", PROMOVIVEIS);
    }

    // --- conectar -----------------------------------------------------------------------------------------------

    @Test
    void primeiraSessaoViraOnlineMesmoQueTenhaSaidoOfflineOuAusente() {
        automatica.usuarioConectou(JOANNA);

        verify(presenca).executar(JOANNA, StatusPresenca.ONLINE, "CONEXAO", PROMOVIVEIS);
        verify(avisos).presencaAlterada(JOANNA, StatusPresenca.ONLINE);
    }

    @Test
    void segundaAbaDoMesmoUsuarioNaoGravaNada() {
        automatica.usuarioConectou(JOANNA);
        automatica.usuarioConectou(JOANNA);
        automatica.usuarioConectou(JOANNA);

        verify(presenca, times(1)).executar(any(), any(), any(), any());
    }

    @Test
    void nosPrimeirosSegundosDoBackendSoOfflineViraOnlineEOAusenteManualEPreservado() {
        automatica = nova(CARENCIA); // partida = agora

        automatica.usuarioConectou(JOANNA);

        verify(presenca).executar(JOANNA, StatusPresenca.ONLINE, "CONEXAO", PROMOVIVEIS_NA_CARENCIA);
    }

    // --- desconectar --------------------------------------------------------------------------------------------

    @Test
    void fecharUmaAbaMantendoOutraNuncaViraOffline() {
        automatica.usuarioConectou(JOANNA);
        registrarSessoes(JOANNA, "s2");

        automatica.usuarioDesconectou(JOANNA, "s1");
        relogio.avancar(TOLERANCIA.multipliedBy(3));
        when(presenca.idsComPresencaAtiva()).thenReturn(List.of(JOANNA));
        automatica.varrer();

        verify(presenca, never()).executar(eq(JOANNA), eq(StatusPresenca.OFFLINE), any(), any());
    }

    @Test
    void fecharTodasAsAbasSoViraOfflineDepoisDaTolerancia() {
        automatica.usuarioConectou(JOANNA);
        registrarSessoes(JOANNA); // nenhuma sessao
        when(presenca.idsComPresencaAtiva()).thenReturn(List.of(JOANNA));
        automatica.usuarioDesconectou(JOANNA, "s1");

        relogio.avancar(TOLERANCIA.minusSeconds(1));
        automatica.varrer();
        verify(presenca, never()).executar(eq(JOANNA), eq(StatusPresenca.OFFLINE), any(), any());

        relogio.avancar(Duration.ofSeconds(2));
        automatica.varrer();
        verify(presenca).executar(JOANNA, StatusPresenca.OFFLINE, "DESCONEXAO", REBAIXAVEIS);
        verify(avisos).presencaAlterada(JOANNA, StatusPresenca.OFFLINE);
    }

    @Test
    void reconectarDentroDaToleranciaEContinuacaoENaoMudaNada() {
        automatica.usuarioConectou(JOANNA);
        clearInvocations(presenca, avisos);
        automatica.usuarioDesconectou(JOANNA, "s1"); // o frontend troca o socket a cada renovacao do token

        relogio.avancar(Duration.ofSeconds(5));
        automatica.usuarioConectou(JOANNA);

        verifyNoInteractions(presenca, avisos);
    }

    @Test
    void reconectarDepoisDaToleranciaEUmaNovaSessao() {
        automatica.usuarioConectou(JOANNA);
        automatica.usuarioDesconectou(JOANNA, "s1");
        relogio.avancar(TOLERANCIA.plusSeconds(1));
        clearInvocations(presenca, avisos);

        automatica.usuarioConectou(JOANNA);

        verify(presenca).executar(JOANNA, StatusPresenca.ONLINE, "CONEXAO", PROMOVIVEIS);
    }

    // --- varredura, carencia e deploy ------------------------------------------------------------------------------

    @Test
    void quemSumiuDuranteODeployFicaOnlineAteAToleranciaESoDepoisDaCarenciaViraOffline() {
        automatica = nova(CARENCIA); // backend acabou de subir; Debora esta ONLINE no banco e nunca reconectou
        when(presenca.idsComPresencaAtiva()).thenReturn(List.of(DEBORA));

        automatica.varrer(); // primeira vez que a vemos sem sessao
        relogio.avancar(TOLERANCIA.plusSeconds(1));
        automatica.varrer(); // passou da tolerancia, mas ainda esta na carencia de partida
        verify(presenca, never()).executar(eq(DEBORA), eq(StatusPresenca.OFFLINE), any(), any());

        relogio.avancar(CARENCIA);
        automatica.varrer();
        verify(presenca).executar(DEBORA, StatusPresenca.OFFLINE, "DESCONEXAO", REBAIXAVEIS);
    }

    @Test
    void usuarioQueReconectaDepoisDoDeployNaoEDerrubado() {
        automatica = nova(CARENCIA);
        when(presenca.idsComPresencaAtiva()).thenReturn(List.of(DEBORA));
        automatica.varrer();

        registrarSessoes(DEBORA, "nova");
        automatica.usuarioConectou(DEBORA);
        relogio.avancar(CARENCIA.plus(TOLERANCIA));
        automatica.varrer();

        verify(presenca, never()).executar(eq(DEBORA), eq(StatusPresenca.OFFLINE), any(), any());
    }

    @Test
    void usuarioQueReconectouEntreALeituraEAGravacaoVoltaParaOnlineNaHora() {
        when(presenca.idsComPresencaAtiva()).thenReturn(List.of(JOANNA));
        automatica.varrer();
        relogio.avancar(TOLERANCIA.plusSeconds(1));
        // Depois de gravar OFFLINE o registro ja mostra uma sessao: ela reconectou no meio da varredura.
        when(presenca.executar(eq(JOANNA), eq(StatusPresenca.OFFLINE), any(), any())).thenAnswer(chamada -> {
            registrarSessoes(JOANNA, "reconectou");
            return Optional.of(new MudancaDePresenca(StatusPresenca.ONLINE, StatusPresenca.OFFLINE));
        });

        automatica.varrer();

        verify(presenca).executar(JOANNA, StatusPresenca.ONLINE, "CONEXAO", EnumSet.of(StatusPresenca.OFFLINE));
    }

    // --- instancia velha e falhas -----------------------------------------------------------------------------------

    @Test
    void depoisDeEncerrarAInstanciaNaoGravaMais() {
        automatica.usuarioConectou(JOANNA);
        clearInvocations(presenca, avisos);
        automatica.aoEncerrar(null);

        automatica.usuarioDesconectou(JOANNA, "s1");
        relogio.avancar(TOLERANCIA.multipliedBy(2));
        when(presenca.idsComPresencaAtiva()).thenReturn(List.of(JOANNA));
        automatica.varrer();
        automatica.usuarioConectou(DEBORA);

        verifyNoInteractions(presenca, avisos);
    }

    @Test
    void falhaNoBancoNaoPropagaENaoAvisaNinguem() {
        when(presenca.executar(any(), any(), any(), any())).thenThrow(new IllegalStateException("banco fora"));

        automatica.usuarioConectou(JOANNA);

        verifyNoInteractions(avisos);
    }

    @Test
    void usuarioInexistenteOuInativoNaoGeraAviso() {
        when(presenca.executar(any(), any(), any(), any())).thenReturn(Optional.empty());

        automatica.usuarioConectou(JOANNA);

        verifyNoInteractions(avisos);
    }

    @Test
    void mudancaQueNaoMudouNadaNaoGeraAviso() {
        when(presenca.executar(any(), any(), any(), any()))
                .thenReturn(Optional.of(new MudancaDePresenca(StatusPresenca.ONLINE, StatusPresenca.ONLINE)));

        automatica.usuarioConectou(JOANNA);

        verifyNoInteractions(avisos);
    }

    @Test
    void falhaNaVarreduraNaoPropaga() {
        when(presenca.idsComPresencaAtiva()).thenThrow(new IllegalStateException("banco fora"));

        automatica.varrer();

        verify(presenca, never()).executar(any(), any(), any(), any());
    }

    // --- apoio -------------------------------------------------------------------------------------------------------

    private PresencaAutomatica nova(Duration carencia) {
        return new PresencaAutomatica(
                registro,
                presenca,
                configuracao,
                avisos,
                relogio,
                new PresencaAutomaticaProperties(TOLERANCIA, carencia, VARREDURA));
    }

    private void registrarSessoes(UUID usuario, String... idsDasSessoes) {
        if (idsDasSessoes.length == 0) {
            when(registro.getUser(usuario.toString())).thenReturn(null);
            return;
        }
        SimpUser simp = mock(SimpUser.class);
        Set<SimpSession> sessoes = new HashSet<>();
        for (String id : idsDasSessoes) {
            SimpSession sessao = mock(SimpSession.class);
            when(sessao.getId()).thenReturn(id);
            sessoes.add(sessao);
        }
        when(simp.getSessions()).thenReturn(sessoes);
        when(registro.getUser(usuario.toString())).thenReturn(simp);
    }

    /** Relogio que so anda quando o teste manda. */
    private static final class RelogioMutavel extends Clock {
        private Instant agora;

        RelogioMutavel(Instant inicio) {
            this.agora = inicio;
        }

        void avancar(Duration quanto) {
            agora = agora.plus(quanto);
        }

        @Override
        public Instant instant() {
            return agora;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zona) {
            return this;
        }
    }
}
