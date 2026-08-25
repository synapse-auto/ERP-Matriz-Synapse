package com.synapse.crm.equipe.application.usuario;

import java.util.Optional;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.equipe.domain.usuario.Usuario;
import com.synapse.crm.sharedkernel.identidade.UsuarioContext;

/** Troca ou remove somente a foto do usuario autenticado. */
@Service
public class AtualizarMinhaFotoUseCase {

    private final EquipeRepositorio equipe;
    private final UsuarioContext usuario;
    private final ProcessadorDeAvatar processador;
    private final ArmazenamentoDeAvatar armazenamento;
    private final LimiteDeAvatarRepositorio limite;

    public AtualizarMinhaFotoUseCase(
            EquipeRepositorio equipe,
            UsuarioContext usuario,
            ProcessadorDeAvatar processador,
            ArmazenamentoDeAvatar armazenamento,
            LimiteDeAvatarRepositorio limite) {
        this.equipe = equipe;
        this.usuario = usuario;
        this.processador = processador;
        this.armazenamento = armazenamento;
        this.limite = limite;
    }

    @PreAuthorize("isAuthenticated()")
    @Transactional
    public Optional<Usuario> executar(byte[] original) {
        Usuario antes = equipe.porId(usuario.atual().id()).orElseThrow();
        if (original == null) {
            equipe.atualizarFoto(antes.id(), null);
            removerSemFalhar(antes.fotoReferencia());
            return equipe.porId(antes.id());
        }
        limite.limiteEmBytes().filter(valor -> original.length > valor)
                .ifPresent(valor -> { throw new FotoDeUsuarioExcedeuLimiteException(valor); });
        ProcessadorDeAvatar.Resultado pronto = processador.processar(original);
        String novaReferencia = armazenamento.salvar(pronto.conteudo(), pronto.mimetype());
        try {
            if (!equipe.atualizarFoto(antes.id(), novaReferencia)) {
                throw new IllegalStateException("usuario autenticado nao encontrado");
            }
        } catch (RuntimeException erro) {
            removerSemFalhar(novaReferencia);
            throw erro;
        }
        removerSemFalhar(antes.fotoReferencia());
        return equipe.porId(antes.id());
    }

    private void removerSemFalhar(String referencia) {
        if (referencia == null || referencia.isBlank()) {
            return;
        }
        try {
            armazenamento.remover(referencia);
        } catch (RuntimeException ignorado) {
            // A coluna ja aponta para a nova referencia; lixo antigo nao pode quebrar o perfil.
        }
    }
}
