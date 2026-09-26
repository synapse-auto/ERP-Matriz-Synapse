package com.synapse.crm.equipe.application.permissao;

import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.equipe.domain.permissao.ConfiguracaoDePermissoes;
import com.synapse.crm.sharedkernel.auditoria.Auditable;

/** "Voltar ao padrao do perfil": remove todas as excecoes do usuario, com revisao e historico. */
@Service
public class RestaurarPadraoDoUsuarioUseCase {

    private final GravadorDeExcecoes gravador;

    public RestaurarPadraoDoUsuarioUseCase(GravadorDeExcecoes gravador) {
        this.gravador = gravador;
    }

    @PreAuthorize(AutorizacaoDeGestao.EDITAR_EXCECOES)
    @Transactional
    @Auditable(acao = "RESTAURAR_PERMISSOES_DO_PERFIL", entidadeTipo = "PERMISSAO")
    public Visoes.Gravacao executar(UUID usuarioId, long revisaoEsperada) {
        return gravador.gravar(usuarioId, revisaoEsperada, ConfiguracaoDePermissoes.vazia(), null,
                PermissaoRepositorio.Operacao.RESTAURAR);
    }
}
