package com.synapse.crm.core.application.mensagemrapida;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.synapse.crm.core.domain.mensagemrapida.MensagemRapida;

public interface MensagemRapidaRepositorio {
    List<MensagemRapida> listar(EscopoMensagensRapidas escopo);
    MensagemRapida criar(UUID atendenteId,String palavraChave,String conteudo);
    Optional<MensagemRapida> atualizar(UUID id,EscopoMensagensRapidas escopo,String palavraChave,String conteudo);
    boolean remover(UUID id,EscopoMensagensRapidas escopo);
}
