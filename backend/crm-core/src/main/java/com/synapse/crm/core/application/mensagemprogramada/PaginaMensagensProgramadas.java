package com.synapse.crm.core.application.mensagemprogramada;

import java.util.List;
import com.synapse.crm.core.domain.mensagemprogramada.MensagemProgramada;

public record PaginaMensagensProgramadas(List<MensagemProgramada> mensagens, int pagina, boolean temMais) {}
