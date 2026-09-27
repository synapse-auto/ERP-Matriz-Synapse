package com.synapse.crm.equipe.infrastructure.seguranca;

import java.util.Optional;

import org.springframework.stereotype.Component;

import com.synapse.crm.equipe.application.permissao.ResolvedorDePermissoesEfetivas;
import com.synapse.crm.equipe.domain.permissao.Capacidade;
import com.synapse.crm.sharedkernel.identidade.ContextoDeServico;
import com.synapse.crm.sharedkernel.identidade.UsuarioAutenticado;
import com.synapse.crm.sharedkernel.identidade.UsuarioContext;
import com.synapse.crm.sharedkernel.permissao.VerificadorDeCapacidades;

/**
 * O bean {@code @capacidades} usado nos {@code @PreAuthorize} de todos os modulos.
 *
 * <p>Decide com o papel e a situacao do BANCO: se o JWT diz um papel e o banco outro (mudanca de
 * papel ainda nao refletida no token) ou o usuario foi desativado, nega. Sem usuario e em contexto
 * de servico, permite — processamento tecnico de comando ja aceito nao e revalidado.
 */
@Component(VerificadorDeCapacidades.NOME_DO_BEAN)
class VerificadorDeCapacidadesSpring implements VerificadorDeCapacidades {

    private final UsuarioContext usuarios;
    private final ResolvedorDePermissoesEfetivas resolvedor;

    VerificadorDeCapacidadesSpring(UsuarioContext usuarios, ResolvedorDePermissoesEfetivas resolvedor) {
        this.usuarios = usuarios;
        this.resolvedor = resolvedor;
    }

    @Override
    public boolean permite(String id) {
        Capacidade capacidade = Capacidade.porId(id)
                .orElseThrow(() -> new IllegalArgumentException("capacidade desconhecida: " + id));
        Optional<UsuarioAutenticado> usuario = usuarios.atualSeHouver();
        if (usuario.isEmpty()) {
            return ContextoDeServico.ativo();
        }
        return resolvedor.de(usuario.get().id())
                .filter(ResolvedorDePermissoesEfetivas.Resolvido::ativo)
                .filter(r -> r.papel() == usuario.get().papel())
                .map(r -> r.efetivas().permite(capacidade))
                .orElse(false);
    }
}
