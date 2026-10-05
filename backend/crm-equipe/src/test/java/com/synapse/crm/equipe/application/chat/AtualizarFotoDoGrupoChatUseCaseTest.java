package com.synapse.crm.equipe.application.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronizationUtils;

import com.synapse.crm.equipe.application.chat.ChatInternoRepositorio.FotoDoGrupo;
import com.synapse.crm.equipe.application.usuario.FotoDeUsuarioInvalidaException;
import com.synapse.crm.equipe.application.usuario.LimiteDeAvatarRepositorio;
import com.synapse.crm.equipe.application.usuario.ProcessadorDeAvatar;
import com.synapse.crm.sharedkernel.identidade.PapelUsuario;
import com.synapse.crm.sharedkernel.identidade.UsuarioAutenticado;
import com.synapse.crm.sharedkernel.identidade.UsuarioContext;

/** Ordem das barreiras, autorizacao e compensacao do storage; a transacao e simulada a mao. */
class AtualizarFotoDoGrupoChatUseCaseTest {

    private static final Instant AGORA = Instant.parse("2026-10-05T12:00:00.123456Z");

    private final ChatInternoRepositorio repositorio = mock(ChatInternoRepositorio.class);
    private final UsuarioContext contexto = mock(UsuarioContext.class);
    private final ValidadorDeFotoDeGrupo validador = mock(ValidadorDeFotoDeGrupo.class);
    private final ProcessadorDeAvatar processador = mock(ProcessadorDeAvatar.class);
    private final ArmazenamentoDeFotoDeGrupo armazenamento = mock(ArmazenamentoDeFotoDeGrupo.class);
    private final LimiteDeAvatarRepositorio limite = mock(LimiteDeAvatarRepositorio.class);
    private final ApplicationEventPublisher eventos = mock(ApplicationEventPublisher.class);

    private final UUID conversa = UUID.randomUUID();
    private final UUID criador = UUID.randomUUID();
    private final UUID colega = UUID.randomUUID();
    private final AtomicInteger leituras = new AtomicInteger();

    private AtualizarFotoDoGrupoChatUseCase casoDeUso;

    @BeforeEach
    void preparar() {
        TransactionSynchronizationManager.initSynchronization();
        casoDeUso = new AtualizarFotoDoGrupoChatUseCase(repositorio, contexto, validador, processador,
                armazenamento, limite, eventos, Clock.fixed(AGORA, ZoneOffset.UTC));
        when(limite.limiteEmBytes()).thenReturn(Optional.of(5L * 1024 * 1024));
        when(repositorio.participante(any(), any())).thenReturn(false);
        when(repositorio.participante(conversa, criador)).thenReturn(true);
        when(repositorio.participante(conversa, colega)).thenReturn(true);
        when(repositorio.bloquearFotoDoGrupo(conversa)).thenReturn(Optional.of(new FotoDoGrupo(criador, "grupo/antiga.png")));
        when(repositorio.definirFotoDoGrupo(eq(conversa), eq(criador), any(), any())).thenReturn(true);
        when(repositorio.participantes(conversa)).thenReturn(List.of(criador, colega));
        when(repositorio.salvarMensagemSistema(eq(conversa), eq(criador), anyString()))
                .thenReturn(new ChatInternoRepositorio.MensagemResumo(
                        UUID.randomUUID(), conversa, criador, "Ana", "SISTEMA", "{}", null, null, AGORA));
        when(processador.processar(any())).thenReturn(new ProcessadorDeAvatar.Resultado(new byte[] {9}, "image/png"));
        when(armazenamento.salvar(any(), anyString())).thenReturn("grupo/nova.png");
        agir(criador);
    }

    @AfterEach
    void limpar() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("criador troca a foto: grava, avisa os outros participantes e devolve a URL versionada em milissegundos")
    void criador_substituiFoto() {
        String url = casoDeUso.substituir(conversa, foto("grupo.png", "image/png", 1024));

        assertThat(url).isEqualTo("/api/v1/chat-interno/conversas/" + conversa + "/foto?v="
                + AGORA.toEpochMilli());
        verify(repositorio).definirFotoDoGrupo(conversa, criador, "grupo/nova.png",
                Instant.parse("2026-10-05T12:00:00.123Z"));
        var evento = org.mockito.ArgumentCaptor.forClass(EventoDeChatInterno.MensagemEnviada.class);
        verify(eventos).publishEvent(evento.capture());
        assertThat(evento.getValue().destinatarios()).containsExactly(colega);
    }

    @Test
    @DisplayName("participante que nao criou o grupo recebe 403 e o arquivo nem chega a ser lido")
    void participanteComum_recusadoAntesDeLerOArquivo() {
        agir(colega);

        assertThatThrownBy(() -> casoDeUso.substituir(conversa, foto("grupo.png", "image/png", 1024)))
                .isInstanceOf(SemPermissaoParaAlterarFotoDoGrupoException.class);
        assertThatThrownBy(() -> casoDeUso.remover(conversa))
                .isInstanceOf(SemPermissaoParaAlterarFotoDoGrupoException.class);

        assertThat(leituras).hasValue(0);
        verify(armazenamento, never()).salvar(any(), anyString());
        verify(repositorio, never()).definirFotoDoGrupo(any(), any(), any(), any());
        verify(eventos, never()).publishEvent(any(Object.class));
    }

    @Test
    @DisplayName("quem nao participa do grupo recebe 403 de acesso, sem saber se o grupo existe")
    void naoParticipante_semAcesso() {
        agir(UUID.randomUUID());

        assertThatThrownBy(() -> casoDeUso.substituir(conversa, foto("grupo.png", "image/png", 1024)))
                .isInstanceOf(ChatSemAcessoException.class);
        verify(repositorio, never()).bloquearFotoDoGrupo(any());
        assertThat(leituras).hasValue(0);
    }

    @Test
    @DisplayName("conversa direta nao tem foto")
    void conversaDireta_recusada() {
        when(repositorio.bloquearFotoDoGrupo(conversa)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> casoDeUso.substituir(conversa, foto("grupo.png", "image/png", 1024)))
                .isInstanceOf(OperacaoDeGrupoInvalidaException.class);
    }

    @Test
    @DisplayName("criador apagado (criado_por_id nulo): ninguem altera, e o arquivo nao e lido")
    void grupoSemCriador_ninguemAltera() {
        when(repositorio.bloquearFotoDoGrupo(conversa)).thenReturn(Optional.of(new FotoDoGrupo(null, null)));

        assertThatThrownBy(() -> casoDeUso.substituir(conversa, foto("grupo.png", "image/png", 1024)))
                .isInstanceOf(SemPermissaoParaAlterarFotoDoGrupoException.class);
        assertThat(leituras).hasValue(0);
    }

    @Test
    @DisplayName("arquivo acima do limite configurado: 413 antes de carregar o corpo em memoria")
    void acimaDoLimite_naoLeOCorpo() {
        assertThatThrownBy(() -> casoDeUso.substituir(conversa, foto("grupo.png", "image/png", 5L * 1024 * 1024 + 1)))
                .isInstanceOf(FotoDeGrupoExcedeuLimiteException.class);

        assertThat(leituras).hasValue(0);
        verify(armazenamento, never()).salvar(any(), anyString());
    }

    @Test
    @DisplayName("nome com caminho, sem extensao de imagem ou tipo declarado nao imagem: 422 sem ler o corpo")
    void descricaoInvalida_naoLeOCorpo() {
        for (String[] invalido : new String[][] {
                {"../../etc/passwd.png", "image/png"},
                {"a/b.png", "image/png"},
                {"a\\b.png", "image/png"},
                {"foto.exe", "image/png"},
                {"foto", "image/png"},
                {"foto.png", "application/x-msdownload"},
                {"foto.png", null},
                {null, "image/png"},
                {"foto\u0000.png", "image/png"}}) {
            assertThatThrownBy(() -> casoDeUso.substituir(conversa, foto(invalido[0], invalido[1], 1024)))
                    .as(String.join(" | ", String.valueOf(invalido[0]), String.valueOf(invalido[1])))
                    .isInstanceOf(FotoDeGrupoInvalidaException.class);
        }
        assertThat(leituras).hasValue(0);
    }

    @Test
    @DisplayName("aceita image/jpg (nao oficial) e parametros no Content-Type")
    void tipoDeclaradoNormalizado() {
        casoDeUso.substituir(conversa, foto("grupo.JPG", "image/jpg", 1024));
        casoDeUso.substituir(conversa, foto("grupo.webp", "IMAGE/WEBP; q=1", 1024));

        verify(validador).validarConteudo(any(), eq("image/jpeg"));
        verify(validador).validarConteudo(any(), eq("image/webp"));
    }

    @Test
    @DisplayName("validador recusa o conteudo: nada e processado nem gravado")
    void conteudoInvalido_naoProcessa() {
        org.mockito.Mockito.doThrow(new FotoDeGrupoInvalidaException("falso")).when(validador).validarConteudo(any(), anyString());

        assertThatThrownBy(() -> casoDeUso.substituir(conversa, foto("grupo.png", "image/png", 1024)))
                .isInstanceOf(FotoDeGrupoInvalidaException.class);

        verify(processador, never()).processar(any());
        verify(armazenamento, never()).salvar(any(), anyString());
    }

    @Test
    @DisplayName("imagem corrompida no reprocessamento vira FotoDeGrupoInvalida (422), nao erro 500")
    void reprocessamentoFalha_viraInvalida() {
        when(processador.processar(any())).thenThrow(new FotoDeUsuarioInvalidaException("conteudo de imagem invalido"));

        assertThatThrownBy(() -> casoDeUso.substituir(conversa, foto("grupo.png", "image/png", 1024)))
                .isInstanceOf(FotoDeGrupoInvalidaException.class)
                .hasMessage("conteudo de imagem invalido");
        verify(armazenamento, never()).salvar(any(), anyString());
    }

    @Test
    @DisplayName("falha no storage: 503 e o banco nem e tocado")
    void storageFalha_naoTocaOBanco() {
        when(armazenamento.salvar(any(), anyString())).thenThrow(new IllegalStateException("minio fora"));

        assertThatThrownBy(() -> casoDeUso.substituir(conversa, foto("grupo.png", "image/png", 1024)))
                .isInstanceOf(ArmazenamentoDeFotoDeGrupoIndisponivelException.class);

        verify(repositorio, never()).definirFotoDoGrupo(any(), any(), any(), any());
        verify(eventos, never()).publishEvent(any(Object.class));
    }

    @Test
    @DisplayName("foto antiga so e apagada DEPOIS do commit; a nova nunca e apagada se o commit acontece")
    void fotoAntigaSoSaiDepoisDoCommit() {
        casoDeUso.substituir(conversa, foto("grupo.png", "image/png", 1024));

        verify(armazenamento, never()).remover(anyString());
        TransactionSynchronizationUtils.triggerAfterCommit();
        TransactionSynchronizationUtils.triggerAfterCompletion(TransactionSynchronization.STATUS_COMMITTED);

        verify(armazenamento).remover("grupo/antiga.png");
        verify(armazenamento, never()).remover("grupo/nova.png");
    }

    @Test
    @DisplayName("rollback depois de gravar no storage: a nova e apagada e a antiga permanece")
    void rollback_apagaANovaEPreservaAAntiga() {
        casoDeUso.substituir(conversa, foto("grupo.png", "image/png", 1024));

        TransactionSynchronizationUtils.triggerAfterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);

        verify(armazenamento).remover("grupo/nova.png");
        verify(armazenamento, never()).remover("grupo/antiga.png");
    }

    @Test
    @DisplayName("UPDATE condicionado ao criador nao casou nenhuma linha: 403 e a nova e apagada no rollback")
    void updateNaoCasou_viraSemPermissaoEApagaANova() {
        when(repositorio.definirFotoDoGrupo(eq(conversa), eq(criador), any(), any())).thenReturn(false);

        assertThatThrownBy(() -> casoDeUso.substituir(conversa, foto("grupo.png", "image/png", 1024)))
                .isInstanceOf(SemPermissaoParaAlterarFotoDoGrupoException.class);
        TransactionSynchronizationUtils.triggerAfterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);

        verify(armazenamento).remover("grupo/nova.png");
        verify(eventos, never()).publishEvent(any(Object.class));
    }

    @Test
    @DisplayName("falha ao apagar objeto antigo no storage nao desfaz nem mascara a troca")
    void falhaAoApagarAntiga_naoQuebra() {
        org.mockito.Mockito.doThrow(new IllegalStateException("minio fora")).when(armazenamento).remover("grupo/antiga.png");

        casoDeUso.substituir(conversa, foto("grupo.png", "image/png", 1024));
        TransactionSynchronizationUtils.triggerAfterCommit();

        verify(armazenamento).remover("grupo/antiga.png");
    }

    @Test
    @DisplayName("remover: limpa a referencia, avisa e apaga o objeto so depois do commit")
    void remover_limpaEApagaDepoisDoCommit() {
        when(repositorio.definirFotoDoGrupo(conversa, criador, null, null)).thenReturn(true);

        casoDeUso.remover(conversa);

        verify(repositorio).definirFotoDoGrupo(conversa, criador, null, null);
        verify(eventos).publishEvent(any(EventoDeChatInterno.MensagemEnviada.class));
        verify(armazenamento, never()).remover(anyString());
        TransactionSynchronizationUtils.triggerAfterCommit();
        verify(armazenamento).remover("grupo/antiga.png");
    }

    @Test
    @DisplayName("remover grupo sem foto e idempotente: nao escreve, nao avisa")
    void remover_semFoto_naoFazNada() {
        when(repositorio.bloquearFotoDoGrupo(conversa)).thenReturn(Optional.of(new FotoDoGrupo(criador, null)));

        casoDeUso.remover(conversa);

        verify(repositorio, never()).definirFotoDoGrupo(any(), any(), any(), any());
        verify(eventos, never()).publishEvent(any(Object.class));
    }

    @Test
    @DisplayName("corpo ilegivel (IOException) vira 422")
    void corpoIlegivel() {
        ArquivoDeFoto quebrado = new ArquivoDeFoto("grupo.png", "image/png", 1024, () -> {
            throw new IOException("disco");
        });

        assertThatThrownBy(() -> casoDeUso.substituir(conversa, quebrado))
                .isInstanceOf(FotoDeGrupoInvalidaException.class);
    }

    @Test
    @DisplayName("arquivo vazio e 422")
    void arquivoVazio() {
        assertThatThrownBy(() -> casoDeUso.substituir(conversa, foto("grupo.png", "image/png", 0)))
                .isInstanceOf(FotoDeGrupoInvalidaException.class);
    }

    @Test
    @DisplayName("sem transacao ativa a troca falha alto em vez de gravar sem compensacao")
    void semTransacao_falhaAlto() {
        TransactionSynchronizationManager.clearSynchronization();

        assertThatThrownBy(() -> casoDeUso.substituir(conversa, foto("grupo.png", "image/png", 1024)))
                .isInstanceOf(IllegalStateException.class);
    }

    private void agir(UUID usuario) {
        when(contexto.atual()).thenReturn(new UsuarioAutenticado(usuario, PapelUsuario.ATENDENTE, false));
    }

    private ArquivoDeFoto foto(String nome, String tipo, long tamanho) {
        return new ArquivoDeFoto(nome, tipo, tamanho, () -> {
            leituras.incrementAndGet();
            return new byte[] {1, 2, 3};
        });
    }
}
