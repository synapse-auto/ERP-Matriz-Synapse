package com.synapse.crm.equipe.application.permissao;

import java.util.Map;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.equipe.domain.permissao.ConfiguracaoDePermissoes;
import com.synapse.crm.sharedkernel.auditoria.Auditable;

/**
 * Substitui o conjunto inteiro de excecoes de um usuario. Chave ausente = herdar do perfil, entao
 * restaurar uma acao e simplesmente nao envia-la. Atomico: ou tudo, ou nada.
 */
@Service
public class SalvarExcecoesDeUsuarioUseCase {

    private final GravadorDeExcecoes gravador;

    public SalvarExcecoesDeUsuarioUseCase(GravadorDeExcecoes gravador) {
        this.gravador = gravador;
    }

    @PreAuthorize(AutorizacaoDeGestao.EDITAR_EXCECOES)
    @Transactional
    @Auditable(acao = "SALVAR_EXCECOES_DE_PERMISSAO", entidadeTipo = "PERMISSAO")
    public Visoes.Gravacao executar(UUID usuarioId, long revisaoEsperada, Map<String, String> niveis,
            Map<String, Boolean> acoes, UUID copiadoDe) {
        ConfiguracaoDePermissoes novas = ConfiguracaoDePermissoes.interpretar(niveis, acoes);
        return gravador.gravar(usuarioId, revisaoEsperada, novas, copiadoDe,
                copiadoDe == null ? PermissaoRepositorio.Operacao.SALVAR : PermissaoRepositorio.Operacao.COPIAR);
    }
}
