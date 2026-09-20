package com.synapse.crm.atendimento.application;

import java.util.Optional;

/**
 * Le a mensagem configurada que devolve o atendimento para a Automacao e ainda zera a ficha do lead.
 *
 * <p>Porta separada da do {@code #reset} de proposito: sao dois comandos independentes, com chaves
 * independentes, e um filho pode trocar o literal de um sem tocar no outro. O que os dois adaptadores
 * compartilham e a consulta, nao o contrato.
 */
public interface ConfiguracaoDoComandoResetGeralRepositorio {

    Optional<String> valor();
}
