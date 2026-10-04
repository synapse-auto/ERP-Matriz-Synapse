package com.synapse.crm.atendimento.application;

import java.util.UUID;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import com.synapse.crm.sharedkernel.identidade.PapelUsuario;
import com.synapse.crm.sharedkernel.identidade.UsuarioContext;
import com.synapse.crm.sharedkernel.permissao.ConsultaDeRecebimentoHumano;

/** Regra comum ao seletor e ao comando direto; a Automacao continua usando o contrato anterior. */
@Service
public class DestinoHumanoAutorizado {
    private final AtendenteParaTransferenciaRepositorio destinos;
    private final UsuarioContext usuarios;
    private final ConsultaDeRecebimentoHumano politica;

    public DestinoHumanoAutorizado(AtendenteParaTransferenciaRepositorio destinos,
            UsuarioContext usuarios, ConsultaDeRecebimentoHumano politica) {
        this.destinos = destinos;
        this.usuarios = usuarios;
        this.politica = politica;
    }

    public boolean permitido(AtendenteParaTransferenciaRepositorio.Destino destino) {
        return destino.papel() != PapelUsuario.OPERADOR
                || politica.permitido(destino.id(), usuarios.atual().papel());
    }

    public void exigir(UUID id) {
        exigir(id, false);
    }

    public void exigir(UUID id, boolean assumirPotencial) {
        var destino = destinos.ativoDestinoHumano(id)
                .orElseThrow(() -> new AtendenteDestinoInvalidoException(id, destinos.motivoDaRecusa(id)));
        boolean proprio = id.equals(usuarios.atual().id());
        if (destino.papel() == PapelUsuario.OPERADOR
                && !(proprio && assumirPotencial)
                && (proprio || !permitido(destino))) {
            throw new AccessDeniedException("destino humano nao autorizado pela Gestao");
        }
    }
}
