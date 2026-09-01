package com.synapse.crm.equipe.application.chat;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.sharedkernel.identidade.UsuarioContext;

/** Cria um grupo: qualquer usuario ativo, sem hierarquia. */
@Service
public class CriarGrupoChatUseCase {
    private final ChatInternoRepositorio repositorio;
    private final UsuarioContext usuario;
    private final ApplicationEventPublisher eventos;

    public CriarGrupoChatUseCase(
            ChatInternoRepositorio repositorio,
            UsuarioContext usuario,
            ApplicationEventPublisher eventos) {
        this.repositorio = repositorio;
        this.usuario = usuario;
        this.eventos = eventos;
    }

    @PreAuthorize("isAuthenticated()")
    @Transactional
    public UUID executar(String nome, List<UUID> participantesPedidos) {
        UUID criador = usuario.atual().id();
        String nomeLimpo = nome == null ? "" : nome.trim();
        if (nomeLimpo.isEmpty()) {
            throw new OperacaoDeGrupoInvalidaException("nome do grupo e obrigatorio");
        }
        if (participantesPedidos == null || participantesPedidos.isEmpty()) {
            throw new OperacaoDeGrupoInvalidaException("informe os participantes iniciais");
        }

        Set<UUID> membros = new LinkedHashSet<>();
        membros.add(criador);
        for (UUID id : participantesPedidos) {
            if (id == null) {
                throw new OperacaoDeGrupoInvalidaException("participante invalido");
            }
            membros.add(id);
        }
        if (membros.size() < 2) {
            throw new OperacaoDeGrupoInvalidaException("grupo exige ao menos dois participantes");
        }
        for (UUID id : membros) {
            if (!repositorio.usuarioExiste(id)) {
                throw new OperacaoDeGrupoInvalidaException("participante invalido ou inativo");
            }
        }

        List<UUID> ordenados = List.copyOf(membros);
        UUID conversaId = repositorio.criarConversaGrupo(nomeLimpo, ordenados);
        ChatInternoRepositorio.MensagemResumo sistema = repositorio.salvarMensagemSistema(
                conversaId, criador, ConteudoDeSistemaChat.grupoCriado(nomeLimpo));
        List<UUID> destinatarios = ordenados.stream().filter(id -> !id.equals(criador)).toList();
        eventos.publishEvent(new EventoDeChatInterno.MensagemEnviada(
                conversaId,
                sistema.id(),
                criador,
                destinatarios,
                sistema.conteudo(),
                sistema.enviadoEm()));
        return conversaId;
    }
}
