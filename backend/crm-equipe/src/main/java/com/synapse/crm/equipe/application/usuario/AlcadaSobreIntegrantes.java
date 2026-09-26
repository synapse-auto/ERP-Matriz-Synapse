package com.synapse.crm.equipe.application.usuario;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.synapse.crm.equipe.domain.permissao.ConcessaoNegadaException;
import com.synapse.crm.equipe.domain.permissao.ConcessaoNegadaException.Codigo;
import com.synapse.crm.equipe.domain.usuario.PapelGerenciavel;
import com.synapse.crm.sharedkernel.identidade.PapelUsuario;
import com.synapse.crm.sharedkernel.identidade.UsuarioAutenticado;
import com.synapse.crm.sharedkernel.identidade.UsuarioContext;

/**
 * Recorte de alvo das operacoes sobre integrantes, separado da capacidade de executa-las.
 *
 * <p>Ter {@code equipe.criar} (ou editar, desativar, senha provisoria) diz que a pessoa pode executar
 * a operacao; esta classe diz sobre QUEM. GESTOR/ADMINISTRADOR alcancam ATENDENTE e SUBGESTOR (o
 * repositorio ja recusa GESTOR/ADMINISTRADOR como alvo). SUBGESTOR delegado alcanca somente
 * ATENDENTE, nunca a si mesmo, e nunca cria ou promove alguem a SUBGESTOR.
 */
@Component
public class AlcadaSobreIntegrantes {

    private final UsuarioContext usuarios;

    public AlcadaSobreIntegrantes(UsuarioContext usuarios) {
        this.usuarios = usuarios;
    }

    public UsuarioAutenticado atual() {
        return usuarios.atual();
    }

    public void exigirPapelNovo(PapelGerenciavel papel) {
        if (usuarios.atual().papel() == PapelUsuario.SUBGESTOR && papel != PapelGerenciavel.ATENDENTE) {
            throw new ConcessaoNegadaException(Codigo.ALVO_FORA_DA_ALCADA, "papel");
        }
    }

    public void exigirAlvo(UUID alvoId, PapelUsuario papelDoAlvo) {
        UsuarioAutenticado ator = usuarios.atual();
        if (ator.papel() != PapelUsuario.SUBGESTOR) {
            return;
        }
        if (ator.id().equals(alvoId)) {
            throw new ConcessaoNegadaException(Codigo.ALVO_PROPRIO);
        }
        if (papelDoAlvo != PapelUsuario.ATENDENTE) {
            throw new ConcessaoNegadaException(Codigo.ALVO_FORA_DA_ALCADA);
        }
    }
}
