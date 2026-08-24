package com.synapse.crm.core.application.lead;
import java.util.List;
import java.util.UUID;
public interface LeadParaEntradaRepositorio { List<LeadParaEntrada> buscar(String termo, UUID usuarioId); }
