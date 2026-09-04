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
    // Lista alinhada ao evento (mesmo dono): ainda assim aplica a marca se sobrou
    // nome/status antigo com atendenteId já nulo — evita cabeçalho com avatar fantasma.
    if (
      daLista.atendenteId === marca.atendenteId
      && (marca.atendenteId !== null || daLista.atendenteNome == null)
      && daLista.status === marca.status
    ) {
      return daLista;
    }
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

/** Transferência humana: id do novo dono; nome vem na reconciliação da API se ainda desconhecido. */
export function mudancaTransferencia(dados: {
  atendimentoId: string;
  leadId: string;
  paraAtendenteId: string | null | undefined;
  ocorridoEm: string;
  status?: StatusAtendimento | null;
  atendenteNome?: string | null;
}): { leadId: string; mudanca: MudancaDeResponsavel } {
  const para =
    dados.paraAtendenteId == null || dados.paraAtendenteId === ""
      ? null
      : dados.paraAtendenteId;
  if (para == null) {
    return mudancaDevolucaoParaIa({
      ...dados,
      status: dados.status === "EM_IA" || dados.status == null ? "EM_IA" : dados.status,
    });
  }
  return {
    leadId: dados.leadId,
    mudanca: {
      ocorridoEm: dados.ocorridoEm,
      atendimentoId: dados.atendimentoId,
      atendenteId: para,
      atendenteNome: dados.atendenteNome ?? null,
      status: dados.status === "EM_ATENDIMENTO" || dados.status == null ? "EM_ATENDIMENTO" : dados.status,
    },
  };
}

/** Devolução à IA (#sair / modo-ia): remove responsável e marca EM_IA. */
export function mudancaDevolucaoParaIa(dados: {
  atendimentoId: string;
  leadId: string;
  ocorridoEm: string;
  status?: StatusAtendimento | null;
}): { leadId: string; mudanca: MudancaDeResponsavel } {
  return {
    leadId: dados.leadId,
    mudanca: {
      ocorridoEm: dados.ocorridoEm,
      atendimentoId: dados.atendimentoId,
      atendenteId: null,
      atendenteNome: null,
      status: dados.status ?? "EM_IA",
    },
  };
}

/**
 * Garante que o cartão exibido (lista ou snapshot) nunca mostre responsável anterior à
 * última mudança confirmada por evento — mesmo se a lista React Query ainda estiver atrasada.
 */
export function aplicarMarcaSeNecessario(
  cartao: CartaoAtendimento | null | undefined,
  registro: RegistroDeMudancas,
): CartaoAtendimento | null {
  if (!cartao) return null;
  const marca = registro.get(cartao.leadId);
  if (!marca) return cartao;
  if (
    cartao.atendenteId === marca.atendenteId
    && (marca.atendenteId !== null || cartao.atendenteNome == null)
    && cartao.status === marca.status
  ) {
    return cartao;
  }
  return aplicarResponsavelAoCartao(cartao, marca);
}
