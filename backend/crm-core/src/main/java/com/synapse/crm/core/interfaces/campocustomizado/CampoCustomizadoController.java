package com.synapse.crm.core.interfaces.campocustomizado;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.synapse.crm.core.application.campocustomizado.ListarCamposCustomizadosUseCase;
import com.synapse.crm.core.domain.campocustomizado.CampoCustomizado;
import com.synapse.crm.core.domain.campocustomizado.TipoCampoCustomizado;

/** Metadados de campo customizado, para a UI renderizar o formulario do lead (E06b). */
@RestController
@RequestMapping("/api/v1/campos-customizados")
@Tag(name = "Campos customizados", description = "Metadados configuráveis da ficha de lead.")
@SecurityRequirement(name = "bearerAuth")
class CampoCustomizadoController {

    private final ListarCamposCustomizadosUseCase listar;

    CampoCustomizadoController(ListarCamposCustomizadosUseCase listar) {
        this.listar = listar;
    }

    @Operation(
            summary = "Listar campos customizados",
            description = "Retorna os campos ativos na ordem em que a ficha de lead deve exibi-los.",
            responses = @ApiResponse(responseCode = "200", description = "Metadados dos campos."))
    @GetMapping
    List<CampoCustomizadoResposta> listar() {
        return listar.executar().stream().map(CampoCustomizadoResposta::de).toList();
    }

    record CampoCustomizadoResposta(
            @Schema(description = "Chave usada em dadosCustomizados.", example = "segmento") String chave,
            @Schema(description = "Rótulo apresentado ao usuário.", example = "Segmento") String rotulo,
            @Schema(description = "Tipo que determina o componente e a validação.") TipoCampoCustomizado tipo,
            @Schema(description = "Valores aceitos quando o tipo possui opções fechadas.") List<String> opcoes,
            @Schema(description = "Indica se o preenchimento é obrigatório.") boolean obrigatorio,
            @Schema(description = "Indica se o campo pode ser usado no filtro modular.") boolean filtravel,
            @Schema(description = "Posição de exibição.", example = "1") short ordem) {

        static CampoCustomizadoResposta de(CampoCustomizado campo) {
            return new CampoCustomizadoResposta(
                    campo.chave(), campo.rotulo(), campo.tipo(), campo.opcoes(), campo.obrigatorio(),
                    campo.filtravel(), campo.ordem());
        }
    }
}
