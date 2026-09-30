package com.synapse.crm.atendimento.application.template;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.UUID;

import com.synapse.crm.atendimento.domain.canal.CanalGateway;
import com.synapse.crm.atendimento.domain.canal.RegraDeTemplateRestrito;
import com.synapse.crm.sharedkernel.identidade.PapelUsuario;
import com.synapse.crm.sharedkernel.identidade.UsuarioAutenticado;
import com.synapse.crm.sharedkernel.identidade.UsuarioContext;

/** Monta a autorizacao real (regra "interno") para testes de caso de uso com portas mockadas. */
public final class AutorizacaoDeTemplatesDeTeste {

    public static final RegraDeTemplateRestrito REGRA = new RegraDeTemplateRestrito("interno");

    private AutorizacaoDeTemplatesDeTeste() {}

    public static AutorizacaoDeTemplates com(UsuarioContext contexto, CanalGateway canal) {
        return new AutorizacaoDeTemplates(REGRA, contexto, canal);
    }

    public static AutorizacaoDeTemplates paraPapel(PapelUsuario papel, CanalGateway canal) {
        UsuarioContext contexto = mock(UsuarioContext.class);
        when(contexto.atual()).thenReturn(new UsuarioAutenticado(UUID.randomUUID(), papel, false));
        return com(contexto, canal);
    }

    public static AutorizacaoDeTemplates administrador(CanalGateway canal) {
        return paraPapel(PapelUsuario.ADMINISTRADOR, canal);
    }
}
