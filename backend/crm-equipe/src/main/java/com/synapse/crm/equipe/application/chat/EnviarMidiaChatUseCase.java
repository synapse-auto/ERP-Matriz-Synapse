package com.synapse.crm.equipe.application.chat;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.equipe.application.chat.ChatInternoRepositorio.MensagemResumo;
import com.synapse.crm.equipe.domain.chat.TiposDeMidiaInterna;
import com.synapse.crm.sharedkernel.identidade.UsuarioContext;
import com.synapse.crm.sharedkernel.midia.ArmazenamentoDeMidia;
import com.synapse.crm.sharedkernel.midia.CategoriaDeMidia;
import com.synapse.crm.sharedkernel.midia.DetectorDeTipoReal;
import com.synapse.crm.sharedkernel.midia.IsoBmffAudioOnly;
import com.synapse.crm.sharedkernel.midia.LimiteDeAnexoRepositorio;

@Service
public class EnviarMidiaChatUseCase {
    private final ChatInternoRepositorio repositorio;
    private final UsuarioContext usuario;
    private final ApplicationEventPublisher eventos;
    private final ArmazenamentoDeMidia armazenamento;
    private final DetectorDeTipoReal detector;
    private final LimiteDeAnexoRepositorio limites;
    private final ObjectMapper mapper;
    private final IdempotenciaDeMidiaChatRepositorio idempotencia;

    public EnviarMidiaChatUseCase(ChatInternoRepositorio repositorio, UsuarioContext usuario,
            ApplicationEventPublisher eventos, ArmazenamentoDeMidia armazenamento,
            DetectorDeTipoReal detector, LimiteDeAnexoRepositorio limites, ObjectMapper mapper,
            IdempotenciaDeMidiaChatRepositorio idempotencia) {
        this.repositorio = repositorio;
        this.usuario = usuario;
        this.eventos = eventos;
        this.armazenamento = armazenamento;
        this.detector = detector;
        this.limites = limites;
        this.mapper = mapper;
        this.idempotencia = idempotencia;
    }

    @PreAuthorize("isAuthenticated()")
    @Transactional
    public MensagemResumo executar(UUID conversaId, String nomeOriginal, String legenda, byte[] conteudo) {
        return executar(conversaId, nomeOriginal, legenda, conteudo, null);
    }

    @PreAuthorize("isAuthenticated()")
    @Transactional
    public MensagemResumo executar(
            UUID conversaId, String nomeOriginal, String legenda, byte[] conteudo, String chaveIdempotencia) {
        UUID remetente = usuario.atual().id();
        if (!repositorio.participante(conversaId, remetente)) throw new ChatSemAcessoException();

        String detectado = detector.detectar(conteudo);
        var classificacao = TiposDeMidiaInterna.classificar(detectado, conteudo)
                .orElseThrow(() -> new TipoDeMidiaInternaNaoPermitidoException(detectado));
        String mimetypeReal = classificacao.mimetype();
        CategoriaDeMidia categoria = classificacao.categoria();

        long limite = limites.limiteEmBytes(categoria).orElse(100L * 1024 * 1024);
        if (conteudo.length > limite) {
            throw new MidiaInternaMuitoGrandeException(limite);
        }

        if (categoria == CategoriaDeMidia.AUDIO && mimetypeReal.equals("audio/mp4")
                && !IsoBmffAudioOnly.ehAudioSemVideo(conteudo)) {
            throw new TipoDeMidiaInternaNaoPermitidoException("video camuflado de mp4");
        }

        String chave = normalizarChave(chaveIdempotencia);
        if (chave != null) {
            var reserva = idempotencia.reservar(chave, remetente, conversaId,
                    impressao(nomeOriginal, legenda, conteudo));
            if (!reserva.nova()) {
                UUID mensagemId = reserva.mensagemId();
                if (mensagemId == null) {
                    throw new IllegalStateException("reserva de mídia interna sem mensagem concluída");
                }
                return repositorio.mensagem(conversaId, mensagemId)
                        .orElseThrow(() -> new IllegalStateException("mensagem idempotente não encontrada"));
            }
        }

        String extensao = nomeOriginal != null && nomeOriginal.contains(".")
                ? nomeOriginal.substring(nomeOriginal.lastIndexOf('.'))
                : "";
        String nomeSanitizado = UUID.randomUUID() + extensao;
        String chaveStorage = "chat_interno/" + conversaId + "/" + nomeSanitizado;

        String referencia = null;
        try {
            referencia = armazenamento.salvar(conteudo, chaveStorage, mimetypeReal);

            ObjectNode metadados = mapper.createObjectNode();
            metadados.put("nome_original", nomeOriginal);
            metadados.put("tamanho_bytes", conteudo.length);
            metadados.put("mimetype", mimetypeReal);
            if (legenda != null && !legenda.isBlank()) {
                metadados.put("legenda", legenda);
            }

            MensagemResumo salva = repositorio.salvarMensagemDeMidia(
                    conversaId, remetente, categoria.name(), legenda, referencia, metadados.toString());
            if (chave != null) {
                idempotencia.concluir(chave, salva.id());
            }

            var destinatarios = repositorio.participantes(conversaId);

            eventos.publishEvent(new EventoDeChatInterno.MensagemEnviada(
                    conversaId, salva.id(), remetente, destinatarios, salva.conteudo(), salva.enviadoEm(),
                    salva.remetenteNome(), salva.tipo(), salva.midiaMetadados()));
            return salva;
        } catch (Exception e) {
            if (referencia != null) armazenamento.remover(referencia);
            if (chave != null) idempotencia.cancelar(chave);
            throw e;
        }
    }

    private static String normalizarChave(String chave) {
        if (chave == null || chave.isBlank()) return null;
        String normalizada = chave.trim();
        if (normalizada.length() > 255) {
            throw new IllegalArgumentException("Idempotency-Key excede 255 caracteres");
        }
        return normalizada;
    }

    private static String impressao(String nomeOriginal, String legenda, byte[] conteudo) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            atualizar(digest, nomeOriginal);
            atualizar(digest, legenda);
            digest.update(conteudo);
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível", e);
        }
    }

    private static void atualizar(MessageDigest digest, String valor) {
        if (valor != null) digest.update(valor.getBytes(StandardCharsets.UTF_8));
        digest.update((byte) 0);
    }
}
