package com.synapse.crm.automacaoconfig.application.festivas;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.synapse.crm.automacaoconfig.domain.festivas.MensagemFestiva;

public interface MensagemFestivaRepositorio {
    List<MensagemFestiva> listarTodas();
    Optional<MensagemFestiva> porId(UUID id);
    MensagemFestiva salvar(MensagemFestiva mensagem);
    void excluir(UUID id);
}
