package com.synapse.crm.automacaoconfig.infrastructure.persistencia;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Repository;

import com.synapse.crm.automacaoconfig.application.festivas.MensagemFestivaRepositorio;
import com.synapse.crm.automacaoconfig.domain.festivas.MensagemFestiva;

@Repository
class MensagemFestivaRepositorioJpa implements MensagemFestivaRepositorio {
    private final MensagemFestivaJpaRepository jpa;

    MensagemFestivaRepositorioJpa(MensagemFestivaJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public List<MensagemFestiva> listarTodas() {
        return jpa.findAllByOrderByDataAscTituloAsc().stream().map(MensagemFestivaEntity::paraDominio).toList();
    }

    @Override
    public Optional<MensagemFestiva> porId(UUID id) {
        return jpa.findById(id).map(MensagemFestivaEntity::paraDominio);
    }

    @Override
    public MensagemFestiva salvar(MensagemFestiva mensagem) {
        MensagemFestivaEntity entidade = jpa.findById(mensagem.id()).orElse(null);
        if (entidade == null) {
            entidade = new MensagemFestivaEntity(mensagem);
        } else {
            entidade.atualizar(mensagem);
        }
        return jpa.save(entidade).paraDominio();
    }

    @Override
    public void excluir(UUID id) {
        jpa.deleteById(id);
    }
}
