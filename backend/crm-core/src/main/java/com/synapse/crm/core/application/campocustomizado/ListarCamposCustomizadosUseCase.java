package com.synapse.crm.core.application.campocustomizado;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.core.domain.campocustomizado.CampoCustomizado;

/** Lista os campos customizados cadastrados, para a tela renderizar o formulario do lead. */
@Service
public class ListarCamposCustomizadosUseCase {

    private final CampoCustomizadoRepositorio campos;

    public ListarCamposCustomizadosUseCase(CampoCustomizadoRepositorio campos) {
        this.campos = campos;
    }

    @PreAuthorize("isAuthenticated()")
    @Transactional(readOnly = true)
    public List<CampoCustomizado> executar() {
        return campos.listarTodos();
    }
}
