package com.synapse.crm.core.interfaces.campocustomizado;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.synapse.crm.core.application.campocustomizado.ListarCamposCustomizadosUseCase;
import com.synapse.crm.core.domain.campocustomizado.CampoCustomizado;
import com.synapse.crm.core.domain.campocustomizado.TipoCampoCustomizado;

/** Metadados de campo customizado, para a UI renderizar o formulario do lead (E06b). */
@RestController
@RequestMapping("/api/v1/campos-customizados")
class CampoCustomizadoController {

    private final ListarCamposCustomizadosUseCase listar;

    CampoCustomizadoController(ListarCamposCustomizadosUseCase listar) {
        this.listar = listar;
    }

    @GetMapping
    List<CampoCustomizadoResposta> listar() {
        return listar.executar().stream().map(CampoCustomizadoResposta::de).toList();
    }

    record CampoCustomizadoResposta(
            String chave,
            String rotulo,
            TipoCampoCustomizado tipo,
            List<String> opcoes,
            boolean obrigatorio,
            boolean filtravel,
            short ordem) {

        static CampoCustomizadoResposta de(CampoCustomizado campo) {
            return new CampoCustomizadoResposta(
                    campo.chave(), campo.rotulo(), campo.tipo(), campo.opcoes(), campo.obrigatorio(),
                    campo.filtravel(), campo.ordem());
        }
    }
}
