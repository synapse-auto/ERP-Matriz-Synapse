package com.synapse.crm.equipe.application.chat;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.sharedkernel.identidade.UsuarioContext;

/** Compartilha dados fornecidos pelo participante, nunca pesquisa leads por telefone. */
@Service
public class EnviarContatoChatUseCase {
    private final ChatInternoRepositorio repositorio;
    private final UsuarioContext usuario;
    private final ApplicationEventPublisher eventos;
    private final IdempotenciaDeMidiaChatRepositorio idempotencia;
    private final ObjectMapper mapper;

    public EnviarContatoChatUseCase(ChatInternoRepositorio repositorio, UsuarioContext usuario,
            ApplicationEventPublisher eventos, IdempotenciaDeMidiaChatRepositorio idempotencia, ObjectMapper mapper) {
        this.repositorio=repositorio; this.usuario=usuario; this.eventos=eventos; this.idempotencia=idempotencia; this.mapper=mapper;
    }

    @PreAuthorize("isAuthenticated()")
    @Transactional
    public ChatInternoRepositorio.MensagemResumo executar(UUID conversaId, UUID usuarioId, String nome, List<String> telefones, UUID chave) {
        UUID remetente=usuario.atual().id();
        if(!repositorio.participante(conversaId, remetente)) throw new ChatSemAcessoException();
        var contato=mapper.createObjectNode();
        var numeros=contato.putArray("telefones");
        if(usuarioId!=null) {
            var destino=repositorio.listarContatos(remetente).stream().filter(c->c.id().equals(usuarioId)).findFirst()
                    .orElseThrow(()->new IllegalArgumentException("Contato interno indisponivel."));
            if(telefones!=null && !telefones.isEmpty()) throw new IllegalArgumentException("Contato interno nao aceita telefone informado.");
            contato.put("usuarioId",usuarioId.toString()); contato.put("nome",destino.nome()); contato.put("origem","INTERNO");
        } else {
            if(nome==null || nome.isBlank()) throw new IllegalArgumentException("Nome do contato obrigatorio.");
            contato.put("nome",nome.trim()); contato.put("origem","EXTERNO");
            if(telefones!=null) for(String numero:telefones) {
                if(numero==null || !numero.trim().matches("\\+?[0-9\\s().-]+")) throw new IllegalArgumentException("Telefone de contato invalido.");
                String digitos=numero.replaceAll("[^0-9]","");
                if(digitos.length()<8 || digitos.length()>15) throw new IllegalArgumentException("Telefone de contato invalido.");
                numeros.addObject().put("numero",numero.trim());
            }
        }
        var metadados=mapper.createObjectNode(); metadados.putArray("contatos").add(contato);
        String json=metadados.toString();
        String reserva="contato:"+chave;
        String impressao;
        try { impressao=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.getBytes(StandardCharsets.UTF_8))); }
        catch(java.security.NoSuchAlgorithmException e){ throw new IllegalStateException(e); }
        var resultado=idempotencia.reservar(reserva,remetente,conversaId,impressao);
        if(!resultado.nova()) return repositorio.mensagem(conversaId,resultado.mensagemId()).orElseThrow(ChatSemAcessoException::new);
        var salva=repositorio.salvarMensagemDeMidia(conversaId,remetente,"CONTATO",null,null,json);
        idempotencia.concluir(reserva,salva.id());
        var destinatarios=repositorio.participantes(conversaId);
        eventos.publishEvent(new EventoDeChatInterno.MensagemEnviada(conversaId,salva.id(),remetente,destinatarios,null,salva.enviadoEm(),salva.remetenteNome(),salva.tipo(),salva.midiaMetadados()));
        return salva;
    }
}
