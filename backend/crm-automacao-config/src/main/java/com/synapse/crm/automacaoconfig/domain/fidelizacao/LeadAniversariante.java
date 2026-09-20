package com.synapse.crm.automacaoconfig.domain.fidelizacao;

import java.util.UUID;

/**
 * O minimo que a Automacao precisa para parabenizar alguem: quem e e para onde mandar.
 *
 * <p>Nada de notas, resumo, dados customizados ou etapa — o contrato interno carrega so o que o
 * disparo usa, pelo mesmo principio de minimizacao que o EV-05 aplica ao omitir telefone de quem
 * nao envia mensagem nenhuma.
 */
public record LeadAniversariante(UUID id, String nome, String telefone) {}
