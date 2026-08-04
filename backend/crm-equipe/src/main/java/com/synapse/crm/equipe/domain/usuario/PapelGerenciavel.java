package com.synapse.crm.equipe.domain.usuario;
import com.synapse.crm.sharedkernel.identidade.PapelUsuario;
public enum PapelGerenciavel { ATENDENTE, SUBGESTOR; public PapelUsuario comoPapel(){return PapelUsuario.valueOf(name());} }
