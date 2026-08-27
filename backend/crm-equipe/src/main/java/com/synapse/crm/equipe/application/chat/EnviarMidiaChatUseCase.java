package com.synapse.crm.equipe.application.chat;

import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.equipe.application.chat.ChatInternoRepositorio.MensagemResumo;
import com.synapse.crm.sharedkernel.identidade.UsuarioContext;
import com.synapse.crm.sharedkernel.midia.ArmazenamentoDeMidia;
import com.synapse.crm.sharedkernel.midia.CategoriaDeMidia;
import com.synapse.crm.sharedkernel.midia.DetectorDeTipoReal;
import com.synapse.crm.sharedkernel.midia.IsoBmffAudioOnly;
import com.synapse.crm.sharedkernel.midia.LimiteDeAnexoRepositorio;
import com.synapse.crm.sharedkernel.midia.RegrasDeAnexoBase;

@Service
public class EnviarMidiaChatUseCase {
    private final ChatInternoRepositorio repositorio;
    private final UsuarioContext usuario;
    private final ApplicationEventPublisher eventos;
    private final ArmazenamentoDeMidia armazenamento;
    private final DetectorDeTipoReal detector;
    private final LimiteDeAnexoRepositorio limites;
    private final ObjectMapper mapper;

    public EnviarMidiaChatUseCase(ChatInternoRepositorio repositorio, UsuarioContext usuario,
            ApplicationEventPublisher eventos, ArmazenamentoDeMidia armazenamento,
            DetectorDeTipoReal detector, LimiteDeAnexoRepositorio limites, ObjectMapper mapper) {
        this.repositorio = repositorio;
        this.usuario = usuario;
        this.eventos = eventos;
        this.armazenamento = armazenamento;
        this.detector = detector;
        this.limites = limites;
        this.mapper = mapper;
    }

    @PreAuthorize("isAuthenticated()")
    @Transactional
    public MensagemResumo executar(UUID conversaId, String nomeOriginal, String legenda, byte[] conteudo) {
        UUID remetente = usuario.atual().id();
        if (!repositorio.participante(conversaId, remetente)) throw new ChatSemAcessoException();

        String mimetypeReal = detector.detectar(conteudo);
        CategoriaDeMidia categoria = RegrasDeAnexoBase.categoriaDe(mimetypeReal)
                .orElseThrow(() -> new TipoDeMidiaInternaNaoPermitidoException(mimetypeReal));

        long limite = limites.limiteEmBytes(categoria).orElse(100L * 1024 * 1024);
        if (conteudo.length > limite) {
            throw new MidiaInternaMuitoGrandeException(limite);
        }

        if (categoria == CategoriaDeMidia.AUDIO && mimetypeReal.equals("audio/mp4")
                && !IsoBmffAudioOnly.ehAudioSemVideo(conteudo)) {
            throw new TipoDeMidiaInternaNaoPermitidoException("video camuflado de mp4");
        }

        String extensao = nomeOriginal != null && nomeOriginal.contains(".")
                ? nomeOriginal.substring(nomeOriginal.lastIndexOf('.'))
                : "";
        String nomeSanitizado = UUID.randomUUID() + extensao;
        String chaveStorage = "chat_interno/" + conversaId + "/" + nomeSanitizado;

        String referencia = armazenamento.salvar(conteudo, chaveStorage, mimetypeReal);

        ObjectNode metadados = mapper.createObjectNode();
        metadados.put("nome_original", nomeOriginal);
        metadados.put("tamanho_bytes", conteudo.length);
        metadados.put("mimetype", mimetypeReal);

        MensagemResumo salva;
        try {
            salva = repositorio.salvarMensagemDeMidia(conversaId, remetente, categoria.name(), legenda, referencia, metadados.toString());
        } catch (Exception e) {
            armazenamento.remover(referencia);
            throw e;
        }

        var destinatarios = repositorio.participantes(conversaId).stream()
                .filter(id -> !id.equals(remetente)).toList();

        eventos.publishEvent(new EventoDeChatInterno.MensagemEnviada(
                conversaId, salva.id(), remetente, destinatarios, salva.conteudo(), salva.enviadoEm()));

        return salva;
    }
}
