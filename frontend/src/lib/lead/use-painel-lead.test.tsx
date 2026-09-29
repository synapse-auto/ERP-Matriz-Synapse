import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, renderHook, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import type { LeadFicha, MidiaDoLead, SolicitacaoResumoIa, TagDoLead } from "./types";

vi.mock("./api", () => ({
  atualizarLead: vi.fn(),
  desvincularTagDoLead: vi.fn(),
  listarCanais: vi.fn(),
  listarCamposCustomizados: vi.fn(),
  listarEtapas: vi.fn(),
  listarTagsDoLead: vi.fn(),
  listarTimeline: vi.fn(),
  listarMidiasDoLead: vi.fn(),
  listarTodasAsTags: vi.fn(),
  obterLead: vi.fn(),
  obterLeadNaAgenda: vi.fn(),
  obterEstadoResumoIa: vi.fn(),
  solicitarResumoIa: vi.fn(),
  vincularTagAoLead: vi.fn(),
}));
vi.mock("@/lib/query/tempos", () => ({ INTERVALO_REVALIDACAO_CACHE_MS: 25 }));

import * as api from "./api";
import { TIPOS_MIDIAS_DA_FICHA, useEstadoResumoIa, useLead, useMidiasDoLead, useSalvarFicha, useVincularTag } from "./use-painel-lead";

function wrapper(cache: QueryClient) {
  return function Wrapper({ children }: { children: ReactNode }) {
    return <QueryClientProvider client={cache}>{children}</QueryClientProvider>;
  };
}

const ficha: LeadFicha = {
  id: "lead-1",
  nome: "Cliente",
  fotoUrl: null,
  telefone: null,
  email: null,
  cpf: null,
  empresa: null,
  codigo: null,
  localizacao: null,
  canalOrigemId: null,
  status: "EM_ATENDIMENTO",
  etapaAtendimentoId: null,
  atendenteResponsavelId: "usuario-1",
  notas: "anterior",
  resumoIa: "resumo",
  resumoIaAtualizadoEm: "2026-08-03T00:00:00Z",
  numAtendimentos: 2,
  numMensagens: 3,
  criadoEm: "2026-08-03T00:00:00Z",
  dadosCustomizados: { codigo: "A" },
};

describe("mutações otimistas da ficha", () => {
  it("restaura notas e campos anteriores quando o servidor recusa", async () => {
    let rejeitar!: (erro: Error) => void;
    vi.mocked(api.atualizarLead).mockImplementation(
      () => new Promise((_resolver, rejeicao) => (rejeitar = rejeicao)),
    );
    const cache = new QueryClient({ defaultOptions: { mutations: { retry: false } } });
    cache.setQueryData(["lead", "lead-1"], ficha);
    const { result } = renderHook(() => useSalvarFicha("lead-1"), { wrapper: wrapper(cache) });

    result.current.mutate({ notas: "nova", dadosCustomizados: { codigo: "B" } });
    await waitFor(() => expect(cache.getQueryData<LeadFicha>(["lead", "lead-1"])?.notas).toBe("nova"));

    rejeitar(new Error("recusado"));
    await waitFor(() => expect(result.current.isError).toBe(true));

    expect(cache.getQueryData<LeadFicha>(["lead", "lead-1"])).toEqual(ficha);
  });

  it("aplica codigo otimista e restaura quando o servidor recusa", async () => {
    let rejeitar!: (erro: Error) => void;
    vi.mocked(api.atualizarLead).mockImplementation(
      () => new Promise((_resolver, rejeicao) => (rejeitar = rejeicao)),
    );
    const cache = new QueryClient({ defaultOptions: { mutations: { retry: false } } });
    cache.setQueryData(["lead", "lead-1"], ficha);
    cache.setQueryData(["atendimentos"], [{ leadId: "lead-1", leadCodigo: null }]);
    const { result } = renderHook(() => useSalvarFicha("lead-1"), { wrapper: wrapper(cache) });

    result.current.mutate({ codigo: "00421" });
    await waitFor(() =>
      expect(cache.getQueryData<LeadFicha>(["lead", "lead-1"])?.codigo).toBe("00421"),
    );

    rejeitar(new Error("recusado"));
    await waitFor(() => expect(result.current.isError).toBe(true));

    expect(cache.getQueryData<LeadFicha>(["lead", "lead-1"])).toEqual(ficha);
    expect(cache.getQueryData(["atendimentos"])).toEqual([{ leadId: "lead-1", leadCodigo: null }]);
  });

  it("espelha o codigo salvo no card da inbox", async () => {
    vi.mocked(api.atualizarLead).mockResolvedValue({ ...ficha, codigo: "00421" });
    const cache = new QueryClient({ defaultOptions: { mutations: { retry: false } } });
    cache.setQueryData(["lead", "lead-1"], ficha);
    cache.setQueryData(["atendimentos"], [{ leadId: "lead-1", leadCodigo: null }]);
    const { result } = renderHook(() => useSalvarFicha("lead-1"), { wrapper: wrapper(cache) });

    result.current.mutate({ codigo: "00421" });
    await waitFor(() =>
      expect(cache.getQueryData(["atendimentos"])).toEqual([{ leadId: "lead-1", leadCodigo: "00421" }]),
    );
  });

  it("aplica nome otimista e espelha no card da inbox", async () => {
    vi.mocked(api.atualizarLead).mockResolvedValue({ ...ficha, nome: "Maria Silva" });
    const cache = new QueryClient({ defaultOptions: { mutations: { retry: false } } });
    cache.setQueryData(["lead", "lead-1"], ficha);
    cache.setQueryData(["atendimentos"], [{ leadId: "lead-1", leadNome: "Cliente" }]);
    const { result } = renderHook(() => useSalvarFicha("lead-1"), { wrapper: wrapper(cache) });

    result.current.mutate({ nome: "Maria Silva" });
    await waitFor(() =>
      expect(cache.getQueryData<LeadFicha>(["lead", "lead-1"])?.nome).toBe("Maria Silva"),
    );
    await waitFor(() =>
      expect(cache.getQueryData(["atendimentos"])).toEqual([
        { leadId: "lead-1", leadNome: "Maria Silva" },
      ]),
    );
  });

  it("adiciona a tag otimista e restaura a lista quando o vínculo falha", async () => {
    let rejeitar!: (erro: Error) => void;
    vi.mocked(api.vincularTagAoLead).mockImplementation(
      () => new Promise((_resolver, rejeicao) => (rejeitar = rejeicao)),
    );
    const cache = new QueryClient({ defaultOptions: { mutations: { retry: false } } });
    const anterior: TagDoLead[] = [{ id: "tag-1", nome: "Atual", cor: "var(--primary)", icone: null }];
    const nova: TagDoLead = { id: "tag-2", nome: "Nova", cor: "var(--primary)", icone: null };
    cache.setQueryData(["lead", "lead-1", "tags"], anterior);
    const { result } = renderHook(() => useVincularTag("lead-1"), { wrapper: wrapper(cache) });

    result.current.mutate({ tag: nova });
    await waitFor(() =>
      expect(cache.getQueryData<TagDoLead[]>(["lead", "lead-1", "tags"])).toHaveLength(2),
    );

    rejeitar(new Error("recusado"));
    await waitFor(() => expect(result.current.isError).toBe(true));

    expect(cache.getQueryData<TagDoLead[]>(["lead", "lead-1", "tags"])).toEqual(anterior);
  });
});

describe("ficha aberta pela Agenda", () => {
  it("consulta o endpoint colaborativo sem contaminar a chave da ficha normal", async () => {
    vi.mocked(api.obterLeadNaAgenda).mockResolvedValue(ficha);
    const cache = new QueryClient({ defaultOptions: { queries: { retry: false } } });

    const { result } = renderHook(() => useLead("lead-1", "agenda"), { wrapper: wrapper(cache) });

    await waitFor(() => expect(result.current.data).toEqual(ficha));
    expect(api.obterLeadNaAgenda).toHaveBeenCalledWith("lead-1");
    expect(cache.getQueryData(["lead", "agenda", "lead-1"])).toEqual(ficha);
    expect(cache.getQueryData(["lead", "lead-1"])).toBeUndefined();
  });
});

describe("mídias da ficha", () => {
  it("passa os tipos ao endpoint e separa o cache filtrado do completo", async () => {
    const imagem = { mensagemId: "imagem", tipo: "IMAGEM" } as MidiaDoLead;
    const audio = { mensagemId: "audio", tipo: "AUDIO" } as MidiaDoLead;
    vi.mocked(api.listarMidiasDoLead).mockImplementation(async (_id, _pagina, _tamanho, tipos) =>
      tipos ? [imagem] : [audio, imagem],
    );
    const cache = new QueryClient({ defaultOptions: { queries: { retry: false } } });

    const filtrado = renderHook(() => useMidiasDoLead("lead-1", TIPOS_MIDIAS_DA_FICHA), { wrapper: wrapper(cache) });
    await waitFor(() => expect(filtrado.result.current.data?.pages[0]).toEqual([imagem]));
    const completo = renderHook(() => useMidiasDoLead("lead-1"), { wrapper: wrapper(cache) });
    await waitFor(() => expect(completo.result.current.data?.pages[0]).toEqual([audio, imagem]));

    expect(api.listarMidiasDoLead).toHaveBeenCalledWith("lead-1", 0, 20, TIPOS_MIDIAS_DA_FICHA);
    expect(cache.getQueryData(["lead", "lead-1", "midias", "IMAGEM,VIDEO,DOCUMENTO"])).toBeDefined();
    expect(cache.getQueryData(["lead", "lead-1", "midias", "todos"])).toBeDefined();
  });
});

const solicitacao: SolicitacaoResumoIa = {
  solicitacaoId: "solicitacao-1",
  leadId: "lead-1",
  atendimentoId: "atendimento-1",
  status: "PENDENTE",
  solicitadoEm: "2026-09-29T10:00:00Z",
  atualizadoEm: "2026-09-29T10:00:00Z",
  erroCodigo: null,
  erroMensagem: null,
};

describe("convergência do resumo por IA", () => {
  beforeEach(() => vi.mocked(api.obterEstadoResumoIa).mockReset());

  it.each(["PENDENTE", "PROCESSANDO"] as const)(
    "revalida %s enquanto a ficha está aberta e para em estado terminal",
    async (status) => {
      vi.mocked(api.obterEstadoResumoIa).mockResolvedValue({ ...solicitacao, status });
      const cache = new QueryClient({ defaultOptions: { queries: { retry: false } } });
      const painel = renderHook(() => useEstadoResumoIa("atendimento-1"), { wrapper: wrapper(cache) });
      await waitFor(() => expect(painel.result.current.data?.status).toBe(status));
      const query = cache.getQueryCache().find({ queryKey: ["resumo-ia", "atendimento-1"] });
      const intervalo = (query?.options as { refetchInterval?: (query: unknown) => number | false }).refetchInterval;
      expect(typeof intervalo).toBe("function");
      if (!query || typeof intervalo !== "function") throw new Error("intervalo do resumo ausente");
      expect(intervalo(query)).toBe(25);

      act(() => cache.setQueryData(["resumo-ia", "atendimento-1"], { ...solicitacao, status: "CONCLUIDO" }));
      expect(intervalo(query)).toBe(false);
      painel.unmount();
      cache.clear();
    },
  );

  it.each(["CONCLUIDO", "FALHOU"] as const)(
    "atualiza a ficha após %s persistido, mesmo sem evento WebSocket",
    async (status) => {
      vi.mocked(api.obterEstadoResumoIa).mockResolvedValue({ ...solicitacao, status });
      const cache = new QueryClient({ defaultOptions: { queries: { retry: false } } });
      cache.setQueryData(["lead", "lead-1"], ficha);
      const painel = renderHook(() => useEstadoResumoIa("atendimento-1"), { wrapper: wrapper(cache) });

      await waitFor(() => expect(painel.result.current.data?.status).toBe(status));
      await waitFor(() => expect(cache.getQueryState(["lead", "lead-1"])?.isInvalidated).toBe(true));
      const query = cache.getQueryCache().find({ queryKey: ["resumo-ia", "atendimento-1"] });
      const intervalo = (query?.options as { refetchInterval?: (query: unknown) => number | false }).refetchInterval;
      if (!query || typeof intervalo !== "function") throw new Error("intervalo do resumo ausente");
      expect(intervalo(query)).toBe(false);
      painel.unmount();
      cache.clear();
    },
  );

  it("recupera o estado terminal sem WebSocket após perder o evento", async () => {
    vi.mocked(api.obterEstadoResumoIa)
      .mockResolvedValueOnce(solicitacao)
      .mockResolvedValueOnce({ ...solicitacao, status: "CONCLUIDO" });
    const cache = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    cache.setQueryData(["lead", "lead-1"], ficha);
    const painel = renderHook(() => useEstadoResumoIa("atendimento-1"), { wrapper: wrapper(cache) });

    await waitFor(() => expect(painel.result.current.data?.status).toBe("CONCLUIDO"));
    expect(api.obterEstadoResumoIa).toHaveBeenCalledTimes(2);
    await waitFor(() => expect(cache.getQueryState(["lead", "lead-1"])?.isInvalidated).toBe(true));
    painel.unmount();
    cache.clear();
  });

  it("não consulta nem mistura estados ao trocar para outro atendimento", async () => {
    vi.mocked(api.obterEstadoResumoIa).mockImplementation(async (id) =>
      id === "atendimento-1" ? solicitacao : { ...solicitacao, atendimentoId: id, status: "FALHOU" },
    );
    const cache = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    const painel = renderHook(({ id }) => useEstadoResumoIa(id), {
      initialProps: { id: "atendimento-1" as string | null },
      wrapper: wrapper(cache),
    });
    await waitFor(() => expect(painel.result.current.data?.status).toBe("PENDENTE"));
    painel.rerender({ id: "atendimento-2" });
    await waitFor(() => expect(painel.result.current.data?.atendimentoId).toBe("atendimento-2"));
    expect(painel.result.current.data?.status).toBe("FALHOU");
    const consultasAntesDeDesmontar = vi.mocked(api.obterEstadoResumoIa).mock.calls.length;
    painel.rerender({ id: null });
    expect(painel.result.current.data).toBeUndefined();
    expect(api.obterEstadoResumoIa).toHaveBeenCalledWith("atendimento-1");
    expect(api.obterEstadoResumoIa).toHaveBeenCalledWith("atendimento-2");
    expect(api.obterEstadoResumoIa).toHaveBeenCalledTimes(consultasAntesDeDesmontar);
    painel.unmount();
    cache.clear();
  });
});
