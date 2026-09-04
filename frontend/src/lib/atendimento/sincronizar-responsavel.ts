import type { QueryClient } from "@tanstack/react-query";

import type { CartaoAtendimento, ItemInbox, StatusAtendimento } from "./types";

/** Última mudança de responsável aplicada por evento de tempo real, por lead. */
export type MudancaDeResponsavel = {
  ocorridoEm: string;
  atendimentoId: string;
  atendenteId: string | null;
  /** Nome só quando já conhecido; null limpa o cabeçalho (ex.: devolução à IA). */
  atendenteNome: string | null;
  status: StatusAtendimento;
};

export type RegistroDeMudancas = Map<string, MudancaDeResponsavel>;

/** Descarta evento mais antigo que o já aplicado — evita resposta atrasada sobrescrever o estado. */
export function ehMaisRecenteQue(
  registro: RegistroDeMudancas,
  leadId: string,
  ocorridoEm: string,
): boolean {
  const anterior = registro.get(leadId);
  return !anterior || ocorridoEm >= anterior.ocorridoEm;
}

export function registrarMudanca(
  registro: RegistroDeMudancas,
  leadId: string,
  mudanca: MudancaDeResponsavel,
): boolean {
  if (!ehMaisRecenteQue(registro, leadId, mudanca.ocorridoEm)) {
    return false;
  }
  registro.set(leadId, mudanca);
  return true;
}

/** Aplica a mudança confirmada pelo backend ao cartão (lista ou snapshot da conversa aberta). */
export function aplicarResponsavelAoCartao(
  cartao: CartaoAtendimento,
  mudanca: MudancaDeResponsavel,
): CartaoAtendimento {
  return {
    ...cartao,
    atendenteId: mudanca.atendenteId,
    atendenteNome: mudanca.atendenteNome,
    status: mudanca.status,
  };
}

export function aplicarResponsavelNaLista(
  itens: ItemInbox[],
  leadId: string,
  mudanca: MudancaDeResponsavel,
): ItemInbox[] {
  return itens.map((item) => {
    if (item.tipo === "EQUIPE_INTERNA" || item.leadId !== leadId) return item;
    return aplicarResponsavelAoCartao(item, mudanca);
  });
}

/**
 * Quando a lista refiltrada ainda não trouxe o cartão (ex.: saiu de Ativos para Potenciais),
 * preserva o snapshot — mas nunca com responsável anterior à última mudança confirmada.
 */
export function mesclarCartaoComLista(
  selecionado: CartaoAtendimento | null,
  cartoes: ItemInbox[],
  registro: RegistroDeMudancas,
): CartaoAtendimento | null {
  if (!selecionado) return null;
  const daLista = cartoes.find(
    (item) => item.tipo !== "EQUIPE_INTERNA" && item.leadId === selecionado.leadId,
  ) as CartaoAtendimento | undefined;
  const marca = registro.get(selecionado.leadId);

  if (daLista) {
    if (!marca) return daLista;
    // API já reconciliou com o evento: descarta a marca e usa o cartão fresco.
    if (daLista.atendenteId === marca.atendenteId) {
      return {
        ...daLista,
        status: marca.atendenteId === null ? marca.status : daLista.status,
      };
    }
    // Lista ainda atrasada em relação ao WebSocket: impõe o responsável do evento.
    return aplicarResponsavelAoCartao(daLista, marca);
  }

  if (!marca) return selecionado;
  return aplicarResponsavelAoCartao(selecionado, marca);
}

export function patchAtendimentosNoCache(
  cache: QueryClient,
  leadId: string,
  mudanca: MudancaDeResponsavel,
): void {
  cache.setQueriesData({ queryKey: ["atendimentos"] }, (atual: unknown) => {
    if (!atual || typeof atual !== "object") return atual;
    if (Array.isArray(atual)) {
      return aplicarResponsavelNaLista(atual as ItemInbox[], leadId, mudanca);
    }
    if ("pages" in atual && Array.isArray((atual as { pages: unknown }).pages)) {
      const inf = atual as { pages: { itens?: ItemInbox[] }[] };
      return {
        ...inf,
        pages: inf.pages.map((pagina) => ({
          ...pagina,
          itens: aplicarResponsavelNaLista(pagina.itens ?? [], leadId, mudanca),
        })),
      };
    }
    return atual;
  });
}

/** Devolução à IA (#sair / modo-ia): remove responsável e marca EM_IA. */
export function mudancaDevolucaoParaIa(dados: {
  atendimentoId: string;
  leadId: string;
  ocorridoEm: string;
}): { leadId: string; mudanca: MudancaDeResponsavel } {
  return {
    leadId: dados.leadId,
    mudanca: {
      ocorridoEm: dados.ocorridoEm,
      atendimentoId: dados.atendimentoId,
      atendenteId: null,
      atendenteNome: null,
      status: "EM_IA",
    },
  };
}

/** Transferência humana: id do novo dono; nome vem na reconciliação da API se ainda desconhecido. */
export function mudancaTransferencia(dados: {
  atendimentoId: string;
  leadId: string;
  paraAtendenteId: string | null;
  ocorridoEm: string;
  atendenteNome?: string | null;
}): { leadId: string; mudanca: MudancaDeResponsavel } {
  if (dados.paraAtendenteId == null) {
    return mudancaDevolucaoParaIa(dados);
  }
  return {
    leadId: dados.leadId,
    mudanca: {
      ocorridoEm: dados.ocorridoEm,
      atendimentoId: dados.atendimentoId,
      atendenteId: dados.paraAtendenteId,
      atendenteNome: dados.atendenteNome ?? null,
      status: "EM_ATENDIMENTO",
    },
  };
}
