package com.synapse.crm.equipe.application.setor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.synapse.crm.equipe.domain.setor.AtribuicaoDeSetor;
import com.synapse.crm.equipe.domain.setor.Setor;

public interface SetorRepositorio {

    List<Setor> listar();

    Optional<Setor> porId(UUID id);

    /** Lança {@code NomeDeSetorEmUsoException} se o nome (sem caixa nem espaços nas pontas) já existe. */
    Setor criar(String nome, boolean recebeDaTriagem);

    /** Vazio = setor inexistente. Lança {@code NomeDeSetorEmUsoException} em nome duplicado. */
    Optional<Setor> atualizar(UUID id, String nome, boolean recebeDaTriagem, boolean ativo);

    List<AtribuicaoDeSetor> listarAtribuicoes();

    /** Troca o setor do usuário (um por usuário) na mesma transação. */
    void atribuir(UUID usuarioId, UUID setorId, UUID atribuidoPorId);

    /** Verdadeiro se o usuário tinha setor. */
    boolean remover(UUID usuarioId);
}
