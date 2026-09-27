package com.synapse.crm.equipe.application.permissao;

import java.time.Duration;

/**
 * Atraso maximo entre uma alteracao de acesso salva em OUTRO no da aplicacao e este no passar a
 * aplica-la. No mesmo no, a invalidacao e imediata (apos o commit).
 */
public interface PoliticaDeRevalidacao {

    Duration intervaloDeRevalidacao();
}
