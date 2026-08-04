package com.synapse.crm.core.application.lembrete;

import java.util.List;

import com.synapse.crm.core.domain.lembrete.Lembrete;

public record PaginaLembretes(List<Lembrete> lembretes, int pagina, boolean temMais) {}
