package com.synapse.crm.core.domain.lead;

/** Estado macro do lead, espelha o ENUM {@code status_basico_lead} do banco. */
public enum StatusBasicoLead {
    /** Ainda com a IA, sem dono. Compoe o grupo "Potenciais", visivel a todos. */
    IA,
    EM_ATENDIMENTO,
    FINALIZADO
}
