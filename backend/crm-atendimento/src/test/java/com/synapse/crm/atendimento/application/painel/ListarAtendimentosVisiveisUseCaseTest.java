package com.synapse.crm.atendimento.application.painel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;

import com.synapse.crm.sharedkernel.identidade.PapelUsuario;
import com.synapse.crm.sharedkernel.identidade.UsuarioAutenticado;
import com.synapse.crm.sharedkernel.identidade.UsuarioContext;

class ListarAtendimentosVisiveisUseCaseTest {

    @Test
    void abasCorrespondemAoPapelSemIncluirFinalizados() {
        UUID atendente = UUID.randomUUID();
        UUID gestor = UUID.randomUUID();

        assertThat(VisaoAtendimento.abasPara(
                        new UsuarioAutenticado(atendente, PapelUsuario.ATENDENTE, false)))
                .containsExactly(
                        VisaoAtendimento.ATIVOS,
                        VisaoAtendimento.PENDENTES,
                        VisaoAtendimento.POTENCIAIS)
                .doesNotContain(VisaoAtendimento.FINALIZADOS);
        assertThat(VisaoAtendimento.abasPara(new UsuarioAutenticado(gestor, PapelUsuario.GESTOR, false)))
                .containsExactly(
                        VisaoAtendimento.ATIVOS,
                        VisaoAtendimento.PENDENTES,
                        VisaoAtendimento.POTENCIAIS,
                        VisaoAtendimento.TODOS)
                .doesNotContain(VisaoAtendimento.FINALIZADOS);
    }

    @Test
    void finalizadosESolicitavelPorAtendenteEPorGestao() {
        UUID atendente = UUID.randomUUID();
        UUID gestor = UUID.randomUUID();

        assertThat(VisaoAtendimento.solicitaveisPor(
                        new UsuarioAutenticado(atendente, PapelUsuario.ATENDENTE, false)))
                .contains(
                        VisaoAtendimento.ATIVOS,
                        VisaoAtendimento.PENDENTES,
                        VisaoAtendimento.POTENCIAIS,
                        VisaoAtendimento.FINALIZADOS)
                .doesNotContain(VisaoAtendimento.TODOS);
        assertThat(VisaoAtendimento.solicitaveisPor(
                        new UsuarioAutenticado(gestor, PapelUsuario.GESTOR, false)))
                .contains(
                        VisaoAtendimento.TODOS,
                        VisaoAtendimento.FINALIZADOS);
    }

    @Test
    void subgestor_naoFicaRestritoAoProprioAtendente() {
        UUID michele = UUID.randomUUID();
        PainelDeAtendimentosRepositorio painel = mock(PainelDeAtendimentosRepositorio.class);
        UsuarioContext contexto = mock(UsuarioContext.class);
        when(contexto.atual()).thenReturn(new UsuarioAutenticado(michele, PapelUsuario.SUBGESTOR, false));
        when(painel.listar(VisaoAtendimento.TODOS, michele, false)).thenReturn(List.of());

        casoDe(painel, contexto).executar(VisaoAtendimento.TODOS);

        verify(painel).listar(VisaoAtendimento.TODOS, michele, false);
    }

    @Test
    void atendente_naoPodePedirVisaoTodos() {
        UUID ana = UUID.randomUUID();
        PainelDeAtendimentosRepositorio painel = mock(PainelDeAtendimentosRepositorio.class);
        UsuarioContext contexto = mock(UsuarioContext.class);
        when(contexto.atual()).thenReturn(new UsuarioAutenticado(ana, PapelUsuario.ATENDENTE, false));

        assertThatThrownBy(() -> casoDe(painel, contexto)
                        .executar(VisaoAtendimento.TODOS))
                .isInstanceOf(AccessDeniedException.class);

        verify(painel, never()).listar(VisaoAtendimento.TODOS, ana, true);
    }

    @Test
    void atendente_naoPodePedirVisaoTodosPaginada() {
        UUID ana = UUID.randomUUID();
        PainelDeAtendimentosRepositorio painel = mock(PainelDeAtendimentosRepositorio.class);
        UsuarioContext contexto = mock(UsuarioContext.class);
        when(contexto.atual()).thenReturn(new UsuarioAutenticado(ana, PapelUsuario.ATENDENTE, false));

        assertThatThrownBy(() -> casoDe(painel, contexto)
                        .executarPaginado(VisaoAtendimento.TODOS, 50, false, null, null))
                .isInstanceOf(AccessDeniedException.class);

        verify(painel, never()).listarPaginado(
                VisaoAtendimento.TODOS, ana, true, false, null, null, 50);
    }

    @Test
    void atendente_podePedirVisaoFinalizados() {
        UUID ana = UUID.randomUUID();
        PainelDeAtendimentosRepositorio painel = mock(PainelDeAtendimentosRepositorio.class);
        UsuarioContext contexto = mock(UsuarioContext.class);
        when(contexto.atual()).thenReturn(new UsuarioAutenticado(ana, PapelUsuario.ATENDENTE, false));
        when(painel.listar(VisaoAtendimento.FINALIZADOS, ana, true)).thenReturn(List.of());

        casoDe(painel, contexto).executar(VisaoAtendimento.FINALIZADOS);

        verify(painel).listar(VisaoAtendimento.FINALIZADOS, ana, true);
    }

    // --- E225 (PR 5): rastro de listagem lenta --------------------------------------------------------------------

    @Test
    void listagemPaginadaAcimaDoLimiteGravaWarnComPapelAbaFormaDuracaoEPool() {
        UUID ana = UUID.randomUUID();
        PainelDeAtendimentosRepositorio painel = mock(PainelDeAtendimentosRepositorio.class);
        UsuarioContext contexto = mock(UsuarioContext.class);
        when(contexto.atual()).thenReturn(new UsuarioAutenticado(ana, PapelUsuario.ATENDENTE, false));
        RelogioQueAvanca relogio = new RelogioQueAvanca();
        when(painel.listarPaginado(VisaoAtendimento.ATIVOS, ana, true, false, null, null, 50)).thenAnswer(chamada -> {
            relogio.avancar(Duration.ofMillis(1_300));
            return List.of();
        });
        MedidorDoPoolDoChat pool = () -> Optional.of(new EstadoDoPool("synapse-chat", 8, 0, 12, 8, 8));

        List<String> avisos = capturandoWarns(
                () -> casoDe(painel, contexto, pool, relogio).executarPaginado(VisaoAtendimento.ATIVOS, 50, false, null, null));

        assertThat(avisos).singleElement().satisfies(aviso -> assertThat(aviso)
                .startsWith("[LISTAGEM_PAINEL_LENTA]")
                .contains("papel=ATENDENTE", "aba=ATIVOS", "limite=50", "cursor=nao", "duracaoMs=1300", "limiteMs=1000")
                .contains("poolAtivas=8", "poolEsperando=12", "poolMaximo=8")
                // Sem dado pessoal: nem o id do usuario aparece.
                .doesNotContain(ana.toString()));
    }

    @Test
    void listagemRapidaNaoGravaNada() {
        UUID ana = UUID.randomUUID();
        PainelDeAtendimentosRepositorio painel = mock(PainelDeAtendimentosRepositorio.class);
        UsuarioContext contexto = mock(UsuarioContext.class);
        when(contexto.atual()).thenReturn(new UsuarioAutenticado(ana, PapelUsuario.ATENDENTE, false));
        RelogioQueAvanca relogio = new RelogioQueAvanca();
        when(painel.listar(VisaoAtendimento.POTENCIAIS, ana, true)).thenAnswer(chamada -> {
            relogio.avancar(Duration.ofMillis(999));
            return List.of();
        });

        List<String> avisos = capturandoWarns(() -> casoDe(painel, contexto, estadoIndisponivel(), relogio)
                .executar(VisaoAtendimento.POTENCIAIS));

        assertThat(avisos).isEmpty();
    }

    @Test
    void listaSimplesLentaSemPoolDisponivelAindaGravaOWarn() {
        UUID michele = UUID.randomUUID();
        PainelDeAtendimentosRepositorio painel = mock(PainelDeAtendimentosRepositorio.class);
        UsuarioContext contexto = mock(UsuarioContext.class);
        when(contexto.atual()).thenReturn(new UsuarioAutenticado(michele, PapelUsuario.GESTOR, false));
        RelogioQueAvanca relogio = new RelogioQueAvanca();
        when(painel.listar(VisaoAtendimento.TODOS, michele, false)).thenAnswer(chamada -> {
            relogio.avancar(Duration.ofSeconds(1));
            return List.of();
        });

        List<String> avisos = capturandoWarns(
                () -> casoDe(painel, contexto, estadoIndisponivel(), relogio).executar(VisaoAtendimento.TODOS));

        assertThat(avisos).singleElement().satisfies(aviso -> assertThat(aviso)
                .contains("papel=GESTOR", "aba=TODOS", "pagina=lista-simples", "duracaoMs=1000", "pool=indisponivel"));
    }

    @Test
    void falhaDaConsultaPassaDiretoSemWarnDeLentidao() {
        UUID ana = UUID.randomUUID();
        PainelDeAtendimentosRepositorio painel = mock(PainelDeAtendimentosRepositorio.class);
        UsuarioContext contexto = mock(UsuarioContext.class);
        when(contexto.atual()).thenReturn(new UsuarioAutenticado(ana, PapelUsuario.ATENDENTE, false));
        RelogioQueAvanca relogio = new RelogioQueAvanca();
        when(painel.listar(VisaoAtendimento.ATIVOS, ana, true)).thenAnswer(chamada -> {
            relogio.avancar(Duration.ofSeconds(5));
            throw new IllegalStateException("banco fora");
        });

        List<String> avisos = capturandoWarns(() -> assertThatThrownBy(() ->
                        casoDe(painel, contexto, estadoIndisponivel(), relogio).executar(VisaoAtendimento.ATIVOS))
                .isInstanceOf(IllegalStateException.class));

        assertThat(avisos).isEmpty();
    }

    // --- apoio ----------------------------------------------------------------------------------------------------

    private static ListarAtendimentosVisiveisUseCase casoDe(
            PainelDeAtendimentosRepositorio painel, UsuarioContext contexto) {
        return casoDe(painel, contexto, estadoIndisponivel(), new RelogioQueAvanca());
    }

    private static ListarAtendimentosVisiveisUseCase casoDe(
            PainelDeAtendimentosRepositorio painel,
            UsuarioContext contexto,
            MedidorDoPoolDoChat pool,
            RelogioQueAvanca relogio) {
        return new ListarAtendimentosVisiveisUseCase(painel, contexto, pool, relogio, Duration.ofSeconds(1));
    }

    private static MedidorDoPoolDoChat estadoIndisponivel() {
        return Optional::empty;
    }

    private static List<String> capturandoWarns(Runnable acao) {
        Logger logger = (Logger) LoggerFactory.getLogger(ListarAtendimentosVisiveisUseCase.class);
        ListAppender<ILoggingEvent> coletor = new ListAppender<>();
        coletor.start();
        logger.addAppender(coletor);
        try {
            acao.run();
        } finally {
            logger.detachAppender(coletor);
        }
        return coletor.list.stream()
                .filter(evento -> evento.getLevel() == Level.WARN)
                .map(ILoggingEvent::getFormattedMessage)
                .toList();
    }

    /** Relogio controlado: so avanca quando a consulta de teste pede. */
    private static final class RelogioQueAvanca extends Clock {
        private Instant agora = Instant.parse("2026-10-06T12:00:00Z");

        private void avancar(Duration quanto) {
            agora = agora.plus(quanto);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zona) {
            return this;
        }

        @Override
        public Instant instant() {
            return agora;
        }
    }
}
