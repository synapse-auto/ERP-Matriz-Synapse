package com.synapse.crm.equipe.application.usuario;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.equipe.domain.usuario.MudancaDePresenca;
import com.synapse.crm.equipe.domain.usuario.OrigemDaPresenca;
import com.synapse.crm.equipe.domain.usuario.StatusPresenca;

/**
 * Presenca mudada pelo sistema (conexao e desconexao do WebSocket), nunca por um usuario: so o papel SERVICO chama.
 * Grava com origem SISTEMA, o motivo informado e o historico, e so se o estado atual for um dos permitidos.
 */
@Service
public class AlterarPresencaPeloSistemaUseCase {

    private final RegistradorDePresenca registrador;
    private final EquipeRepositorio equipe;

    public AlterarPresencaPeloSistemaUseCase(RegistradorDePresenca registrador, EquipeRepositorio equipe) {
        this.registrador = registrador;
        this.equipe = equipe;
    }

    @PreAuthorize("hasRole('SERVICO')")
    @Transactional
    public Optional<MudancaDePresenca> executar(
            UUID usuarioId, StatusPresenca novo, String motivo, Set<StatusPresenca> anterioresPermitidos) {
        return registrador.registrarSe(usuarioId, novo, OrigemDaPresenca.SISTEMA, motivo, anterioresPermitidos);
    }

    /** Quem a presenca automatica precisa conferir: ativos que estao ONLINE ou AUSENTE. */
    @PreAuthorize("hasRole('SERVICO')")
    @Transactional(readOnly = true)
    public List<UUID> idsComPresencaAtiva() {
        return equipe.idsComPresencaAtiva();
    }
}
