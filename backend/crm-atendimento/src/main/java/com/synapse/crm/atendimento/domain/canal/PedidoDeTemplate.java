package com.synapse.crm.atendimento.domain.canal;

/**
 * Pedido de criacao de um template de texto no provedor.
 *
 * <p>Recorte deliberado da primeira entrega: so corpo textual, sem cabecalho, rodape, botoes ou
 * midia. Esses componentes mudam o contrato da Meta e o ciclo de aprovacao; entram depois, com
 * tela e validacao proprias — nao como campo opcional escondido.
 */
public record PedidoDeTemplate(
        String nome, String idioma, TemplateDoCanal.Categoria categoria, String corpo) {}
