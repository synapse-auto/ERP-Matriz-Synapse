package com.synapse.crm.equipe.application.setor;

import java.util.List;

import com.synapse.crm.equipe.domain.setor.AtribuicaoDeSetor;
import com.synapse.crm.equipe.domain.setor.Setor;

/** Setores da instância e quem está em qual; uma só leitura para a tela de gestão da equipe. */
public record VisaoDeSetores(List<Setor> setores, List<AtribuicaoDeSetor> atribuicoes) {}
