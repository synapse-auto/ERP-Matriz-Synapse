package com.synapse.crm.core.application.campocustomizado;

import java.util.List;
import java.util.Optional;

import com.synapse.crm.core.domain.campocustomizado.CampoCustomizado;

/**
 * Porta de leitura dos metadados de campo customizado.
 *
 * <p>So leitura: a escrita (cadastrar/editar/reordenar campo) e fase 2, pela UI de gestao. Ate la,
 * {@code campo_customizado} e populado por migration ou SQL direto (docs/09 §3.1) — nao ha caso de
 * uso de escrita porque nao ha, ainda, quem deva chama-lo.
 */
public interface CampoCustomizadoRepositorio {

    /** Todos os campos cadastrados, para a tela renderizar o formulario. */
    List<CampoCustomizado> listarTodos();

    /** Um campo por chave — usado pela validacao de escrita e pelo filtro modular dinamico. */
    Optional<CampoCustomizado> porChave(String chave);
}
