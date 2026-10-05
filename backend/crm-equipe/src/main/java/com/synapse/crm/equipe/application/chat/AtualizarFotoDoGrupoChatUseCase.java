package com.synapse.crm.equipe.application.chat;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.synapse.crm.equipe.application.chat.ChatInternoRepositorio.FotoDoGrupo;
import com.synapse.crm.equipe.application.usuario.FotoDeUsuarioInvalidaException;
import com.synapse.crm.equipe.application.usuario.LimiteDeAvatarRepositorio;
import com.synapse.crm.equipe.application.usuario.ProcessadorDeAvatar;
import com.synapse.crm.sharedkernel.auditoria.Auditable;
import com.synapse.crm.sharedkernel.identidade.UsuarioContext;

/**
 * Troca ou remove a foto de um grupo. Somente quem criou o grupo pode: o modelo nao tem papel de
 * administrador, e a gestao pediu que a imagem do grupo nao fique aberta a qualquer participante.
 *
 * <p>Ordem deliberada: acesso, permissao e tamanho antes de ler o corpo do upload; conteudo antes de
 * decodificar; storage antes do banco, com compensacao nas duas pontas. O arquivo novo e apagado se
 * a transacao reverter, e o antigo so e apagado depois do commit, para que um commit que falhe
 * nunca deixe o grupo apontando para um objeto que ja nao existe.
 */
@Service
public class AtualizarFotoDoGrupoChatUseCase {
    private static final Logger log = LoggerFactory.getLogger(AtualizarFotoDoGrupoChatUseCase.class);

    private final ChatInternoRepositorio repositorio;
    private final UsuarioContext usuario;
    private final ValidadorDeFotoDeGrupo validador;
    private final ProcessadorDeAvatar processador;
    private final ArmazenamentoDeFotoDeGrupo armazenamento;
    private final LimiteDeAvatarRepositorio limite;
    private final ApplicationEventPublisher eventos;
    private final Clock relogio;

    public AtualizarFotoDoGrupoChatUseCase(
            ChatInternoRepositorio repositorio,
            UsuarioContext usuario,
            ValidadorDeFotoDeGrupo validador,
            ProcessadorDeAvatar processador,
            ArmazenamentoDeFotoDeGrupo armazenamento,
            LimiteDeAvatarRepositorio limite,
            ApplicationEventPublisher eventos,
            Clock relogio) {
        this.repositorio = repositorio;
        this.usuario = usuario;
        this.validador = validador;
        this.processador = processador;
        this.armazenamento = armazenamento;
        this.limite = limite;
        this.eventos = eventos;
        this.relogio = relogio;
    }

    /** @return a URL versionada da nova foto, para o cliente atualizar sem recarregar a lista */
    @PreAuthorize("isAuthenticated()")
    @Transactional
    @Auditable(acao = "ALTERAR_FOTO_GRUPO_CHAT_INTERNO", entidadeTipo = "CHAT_INTERNO_CONVERSA", capturarDados = false)
    public String substituir(UUID conversaId, ArquivoDeFoto arquivo) {
        UUID ator = usuario.atual().id();
        FotoDoGrupo atual = autorizar(conversaId, ator);
        arquivo.validarDescricao();
        validarTamanho(arquivo.tamanho());
        byte[] original = ler(arquivo);
        validador.validarConteudo(original, arquivo.tipoNormalizado());

        ProcessadorDeAvatar.Resultado pronto = reprocessar(original);
        String novaReferencia = salvar(pronto);
        apagarSeAReversaoOcorrer(novaReferencia);

        Instant versao = relogio.instant().truncatedTo(ChronoUnit.MILLIS);
        if (!repositorio.definirFotoDoGrupo(conversaId, ator, novaReferencia, versao)) {
            throw new SemPermissaoParaAlterarFotoDoGrupoException();
        }
        apagarDepoisDoCommit(atual.referencia());
        avisarOsParticipantes(conversaId, ator, ConteudoDeSistemaChat.fotoAlterada());
        return UrlDaFotoDoGrupo.de(conversaId, versao);
    }

    /** Idempotente: remover de grupo sem foto nao muda nada nem escreve mensagem. */
    @PreAuthorize("isAuthenticated()")
    @Transactional
    @Auditable(acao = "REMOVER_FOTO_GRUPO_CHAT_INTERNO", entidadeTipo = "CHAT_INTERNO_CONVERSA", capturarDados = false)
    public void remover(UUID conversaId) {
        UUID ator = usuario.atual().id();
        FotoDoGrupo atual = autorizar(conversaId, ator);
        if (atual.referencia() == null) {
            return;
        }
        if (!repositorio.definirFotoDoGrupo(conversaId, ator, null, null)) {
            throw new SemPermissaoParaAlterarFotoDoGrupoException();
        }
        apagarDepoisDoCommit(atual.referencia());
        avisarOsParticipantes(conversaId, ator, ConteudoDeSistemaChat.fotoRemovida());
    }

    /** Participa, e grupo, e o criador — nesta ordem, para nao revelar o grupo a quem nao o ve. */
    private FotoDoGrupo autorizar(UUID conversaId, UUID ator) {
        if (!repositorio.participante(conversaId, ator)) {
            throw new ChatSemAcessoException();
        }
        FotoDoGrupo grupo = repositorio.bloquearFotoDoGrupo(conversaId)
                .orElseThrow(() -> new OperacaoDeGrupoInvalidaException("so grupos tem foto"));
        if (!ator.equals(grupo.criadorId())) {
            throw new SemPermissaoParaAlterarFotoDoGrupoException();
        }
        return grupo;
    }

    private void validarTamanho(long tamanho) {
        if (tamanho <= 0) {
            throw new FotoDeGrupoInvalidaException("a foto esta vazia");
        }
        limite.limiteEmBytes()
                .filter(maximo -> tamanho > maximo)
                .ifPresent(maximo -> { throw new FotoDeGrupoExcedeuLimiteException(maximo); });
    }

    private static byte[] ler(ArquivoDeFoto arquivo) {
        try {
            return arquivo.leitor().ler();
        } catch (IOException e) {
            throw new FotoDeGrupoInvalidaException("nao foi possivel ler o arquivo enviado");
        }
    }

    private ProcessadorDeAvatar.Resultado reprocessar(byte[] original) {
        try {
            return processador.processar(original);
        } catch (FotoDeUsuarioInvalidaException e) {
            throw new FotoDeGrupoInvalidaException(e.getMessage());
        }
    }

    private String salvar(ProcessadorDeAvatar.Resultado pronto) {
        try {
            return armazenamento.salvar(pronto.conteudo(), pronto.mimetype());
        } catch (RuntimeException e) {
            throw new ArmazenamentoDeFotoDeGrupoIndisponivelException(e);
        }
    }

    private void avisarOsParticipantes(UUID conversaId, UUID ator, String conteudoDeSistema) {
        ChatInternoRepositorio.MensagemResumo sistema =
                repositorio.salvarMensagemSistema(conversaId, ator, conteudoDeSistema);
        List<UUID> destinatarios = repositorio.participantes(conversaId).stream()
                .filter(id -> !id.equals(ator))
                .toList();
        eventos.publishEvent(new EventoDeChatInterno.MensagemEnviada(
                conversaId, sistema.id(), ator, destinatarios, sistema.conteudo(), sistema.enviadoEm()));
    }

    private void apagarSeAReversaoOcorrer(String referencia) {
        aoConcluirATransacao(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    removerSemFalhar(referencia);
                }
            }
        });
    }

    private void apagarDepoisDoCommit(String referenciaAnterior) {
        if (referenciaAnterior == null || referenciaAnterior.isBlank()) {
            return;
        }
        aoConcluirATransacao(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                removerSemFalhar(referenciaAnterior);
            }
        });
    }

    private static void aoConcluirATransacao(TransactionSynchronization acao) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("a troca de foto exige transacao ativa");
        }
        TransactionSynchronizationManager.registerSynchronization(acao);
    }

    /** Lixo no storage nao pode desfazer nem mascarar uma troca ja decidida. */
    private void removerSemFalhar(String referencia) {
        try {
            armazenamento.remover(referencia);
        } catch (RuntimeException e) {
            log.warn("Nao foi possivel apagar a foto antiga do grupo no storage: {}", referencia, e);
        }
    }
}
