package com.synapse.crm.equipe.application.disponibilidade;

import java.util.UUID;

/**
 * Recorte do rodízio da IA por setor (D3a da Disbrel). {@link #GLOBAL} é o rodízio de sempre: nenhum filtro de
 * setor, nenhuma consulta nova — é o único escopo usado quando a capacidade "setores" está desligada.
 *
 * @param setorId setor pedido pelo chamador (já validado: existe, está ativo e recebe da triagem); {@code null} =
 *     qualquer setor
 * @param excluirSetoresQueNaoRecebem a capacidade "setores" está ligada: quem está num setor que não recebe da
 *     triagem (ou inativo) nunca é destino do rodízio, com ou sem {@code setorId}
 */
public record EscopoDoRodizio(UUID setorId, boolean excluirSetoresQueNaoRecebem) {

    public static final EscopoDoRodizio GLOBAL = new EscopoDoRodizio(null, false);

    public boolean eGlobal() {
        return setorId == null && !excluirSetoresQueNaoRecebem;
    }
}
