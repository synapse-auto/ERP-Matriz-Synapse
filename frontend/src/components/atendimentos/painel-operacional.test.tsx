import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { TextosSchema } from "@/lib/config/schema";
import { PainelOperacional } from "./painel-operacional";
import { ListaConversas } from "./lista-conversas";
import { useFilaOperacional, useSnapshotOperacional, totalizarOperacional, parametrosOperacionais, type SnapshotOperacional } from "@/lib/atendimento/painel-operacional";

const acesso = vi.hoisted(() => ({ permitido: true, erro: false, revisao: 1 }));
const api = vi.hoisted(() => vi.fn());
vi.mock("@/lib/api/http-client", () => ({ apiFetch: api }));
vi.mock("@/lib/auth/auth-store", () => ({ useAuthStore: (seletor: (s: unknown) => unknown) => seletor({ status: "autenticado", precisaTrocarSenha: false, papel: "GESTOR", usuarioId: "gestor-sintetico" }) }));
vi.mock("@/lib/gestao/use-gestao", () => ({ useMinhasPermissoes: () => ({ isError: acesso.erro, data: {
  usuarioId: "gestor-sintetico", revisao: acesso.revisao,
  capacidades: { "atendimentos.painel_operacional": { permitido: acesso.permitido } },
} }) }));
vi.mock("@/lib/config/textos-provider", () => ({ useTextos: () => TextosSchema.parse(JSON.parse(readFileSync(resolve(process.cwd(), "../backend/crm-app/src/main/resources/textos.json"), "utf8"))) }));

function snapshot(): SnapshotOperacional {
  const agora = new Date();
  return { atualizadoEm: agora.toISOString(), finalizadosInicio: agora.toISOString(), finalizadosFim: new Date(agora.getTime() + 86_400_000).toISOString(), fuso: "America/Sao_Paulo", limiteMinutos: 20, proximaTransicaoEm: new Date(agora.getTime() + 60_000).toISOString(), equipe: [
    { atendenteId: "ana-sintetica", nome: "Ana Sintética", fotoUrl: null, papel: "ATENDENTE", presenca: "ONLINE", entrada: false, elegivel: true,
      contagens: { novasMensagens: 7, novasConversas: 3, ativos: 4, pendentes: 2, semResposta: 1, finalizados: 2, emAberto: 4, potenciais: 0, esperaDesde: new Date(agora.getTime() - 1_800_000).toISOString() } },
    { atendenteId: null, nome: null, fotoUrl: null, papel: null, presenca: null, entrada: true, elegivel: false,
      contagens: { novasMensagens: 1, novasConversas: 1, ativos: 0, pendentes: 0, semResposta: 0, finalizados: 0, emAberto: 2, potenciais: 2, esperaDesde: null } },
  ] };
}

function renderizar(elemento: React.ReactNode) {
  const cache = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  return { ...render(<QueryClientProvider client={cache}>{elemento}</QueryClientProvider>), cache };
}

beforeEach(() => {
  acesso.permitido = true;
  acesso.erro = false;
  acesso.revisao = 1;
  api.mockReset();
  api.mockImplementation((url: string) => Promise.resolve(url.includes("/fila?")
    ? { itens: [{ atendimentoId: "atendimento-real", leadId: "lead-real", leadNome: "Cliente Sintético", leadFotoUrl: null, leadEmpresa: "Empresa Sintética", naoLidas: 2 }], proximoCursor: null }
    : snapshot()));
});

describe("painel operacional com componentes reais", () => {
  it("resposta atrasada de outro filtro não substitui a fila atual e revogação esconde dados", async () => {
    let concluirAna!: (pagina: unknown) => void;
    api.mockImplementation((url: string) => url.includes("ana")
      ? new Promise(resolve => { concluirAna = resolve; })
      : Promise.resolve({ itens: [{ atendimentoId: "bruno-real", leadNome: "Fila Bruno" }], proximoCursor: null }));
    function Sonda({ dono }: { dono: string }) {
      const fila = useFilaOperacional({ atendenteId: dono }, true);
      return <div>{fila.data?.pages.flatMap(p => p.itens).map(i => i.leadNome).join(",") ?? "sem dados"}</div>;
    }
    const tela = renderizar(<Sonda dono="ana" />);
    await waitFor(() => expect(api).toHaveBeenCalledOnce());
    tela.rerender(<QueryClientProvider client={tela.cache}><Sonda dono="bruno" /></QueryClientProvider>);
    await screen.findByText("Fila Bruno");
    await act(async () => concluirAna({ itens: [{ atendimentoId: "ana-real", leadNome: "Fila Ana" }], proximoCursor: null }));
    expect(screen.getByText("Fila Bruno")).toBeInTheDocument();
    expect(screen.queryByText("Fila Ana")).not.toBeInTheDocument();
    acesso.permitido = false;
    tela.rerender(<QueryClientProvider client={tela.cache}><Sonda dono="bruno" /></QueryClientProvider>);
    expect(screen.getByText("sem dados")).toBeInTheDocument();
  });
  it("lista lateral usa a fila paginada e preserva o Chat Interno sem segunda inbox de clientes", async () => {
    const abrir = vi.fn();
    const abrirInterno = vi.fn();
    const voltar = vi.fn();
    api.mockImplementation((url: string) => Promise.resolve(
      url.includes("/fila?") ? { itens: [{ atendimentoId: "atendimento-real", leadId: "lead-real", leadNome: "Cliente Sintético", leadFotoUrl: null,
        leadEmpresa: "Empresa Sintética", naoLidas: 2, atividadeEm: "2026-10-14T12:00:00Z", ultimaMensagemPreview: "Prévia sintética", etapaNome: "Negociação" }], proximoCursor: null }
      : url.includes("/chat-interno/conversas") ? [{ id: "grupo-real", tipo: "GRUPO", participantes: "Grupo sintético", ultimaMensagem: "Reunião sintética", ultimaMensagemEm: "2026-10-14T13:00:00Z", naoLidas: 1 }]
      : url.includes("/etapas") ? [{ id: "etapa-real", nome: "Negociação" }] : snapshot()));
    renderizar(<ListaConversas selecionadoId={null} visaoAtual="TODOS" painelOperacionalPermitido chatInternoHabilitado
      onAbrirAtendimento={abrirInterno} onAbrirFilaOperacional={abrir} onVoltarPainel={voltar} />);
    fireEvent.click(await screen.findByRole("button", { name: "Abrir atendimento de Cliente Sintético" }));
    expect(abrir).toHaveBeenCalledWith("atendimento-real");
    expect(await screen.findByText("Grupo sintético")).toBeInTheDocument();
    fireEvent.click(screen.getByText("Grupo sintético"));
    expect(abrirInterno).toHaveBeenCalledWith(expect.objectContaining({ tipo: "EQUIPE_INTERNA", conversaId: "grupo-real" }));
    fireEvent.click(screen.getByRole("button", { name: "Voltar ao painel de Atendimentos" }));
    expect(voltar).toHaveBeenCalledOnce();
    expect(screen.getByText("Prévia sintética")).toBeInTheDocument();
    expect(api.mock.calls.some(([url]) => url.includes("/inbox") || url.includes("/contagem"))).toBe(false);
    const busca = screen.getByPlaceholderText("Buscar cliente ou protocolo...");
    fireEvent.change(busca, { target: { value: "Cli" } });
    fireEvent.change(busca, { target: { value: "Cliente & Empresa" } });
    expect(screen.queryByText("Prévia sintética")).not.toBeInTheDocument();
    expect(api.mock.calls.some(([url]) => url.includes("busca=Cli"))).toBe(false);
    await waitFor(() => expect(api.mock.calls.filter(([url]) => url.includes("busca=Cliente+%26+Empresa"))).toHaveLength(2));
    expect(api.mock.calls.some(([url]) => url.includes("busca=Cli&"))).toBe(false);
  });
  it("renderiza indicadores completos, sem somar Pendentes novamente ao total", async () => {
    renderizar(<PainelOperacional onFiltrar={vi.fn()} onAbrir={vi.fn()} />);
    await screen.findByRole("heading", { name: "Equipe" });
    expect(totalizarOperacional(snapshot().equipe)).toMatchObject({ emAberto: 6, ativos: 4, pendentes: 2, novasMensagens: 8 });
    expect(screen.getByRole("button", { name: /Potenciais.*2/ })).toBeInTheDocument();
    expect(screen.getByText("Fila de entrada")).toBeInTheDocument();
    expect(api.mock.calls.some(([url]) => url.includes("/fila?"))).toBe(false);
  });

  it("expande sob demanda e abre o UUID correto sem mutação", async () => {
    const abrir = vi.fn();
    renderizar(<PainelOperacional onFiltrar={vi.fn()} onAbrir={abrir} />);
    const expandir = await screen.findByRole("button", { name: "Expandir filas de Ana Sintética" });
    fireEvent.click(expandir);
    expect(await screen.findByRole("button", { name: "Abrir atendimento de Cliente Sintético" })).toBeInTheDocument();
    expect(api.mock.calls.filter(([url]) => url.includes("/fila?")).length).toBe(1);
    expect(api.mock.calls.find(([url]) => url.includes("/fila?"))?.[0]).toContain("atendenteId=ana-sintetica");
    fireEvent.click(screen.getByRole("button", { name: "Abrir atendimento de Cliente Sintético" }));
    expect(abrir).toHaveBeenCalledWith("atendimento-real");
    expect(api.mock.calls.every(([, opcoes]) => !opcoes?.method)).toBe(true);
  });

  it("número clicado e abrir todos usam o mesmo filtro sem IDs congelados", async () => {
    const filtrar = vi.fn();
    renderizar(<PainelOperacional onFiltrar={filtrar} onAbrir={vi.fn()} />);
    fireEvent.click(await screen.findByRole("button", { name: "Ana Sintética: Pendentes (2)" }));
    expect(filtrar).toHaveBeenLastCalledWith({ atendenteId: "ana-sintetica", recorte: "PENDENTES", sinal: "TODOS" }, "Ana Sintética");
    fireEvent.click(screen.getByRole("button", { name: "Expandir filas de Ana Sintética" }));
    const secao = await screen.findByRole("region", { name: "Ana Sintética" });
    fireEvent.click(within(secao).getByRole("button", { name: /^Pendentes \(/ }));
    fireEvent.click(within(secao).getByRole("button", { name: "Filtrar na lista" }));
    expect(filtrar).toHaveBeenLastCalledWith({ atendenteId: "ana-sintetica", recorte: "PENDENTES" }, "Ana Sintética");
  });

  it("busca é de atendente e presença ausente não é online", async () => {
    renderizar(<PainelOperacional onFiltrar={vi.fn()} onAbrir={vi.fn()} />);
    await screen.findByRole("heading", { name: "Equipe" });
    fireEvent.change(screen.getByRole("textbox", { name: "Pesquisar atendente…" }), { target: { value: "ninguém" } });
    expect(screen.getByText("Nenhum atendente com esses filtros")).toBeInTheDocument();
    expect(api.mock.calls).toHaveLength(1);
  });

  it("falha isolada mostra feedback e tenta novamente sem esconder a lista externa", async () => {
    api.mockRejectedValueOnce(new Error("falha sintética"));
    renderizar(<PainelOperacional onFiltrar={vi.fn()} onAbrir={vi.fn()} />);
    await screen.findByRole("alert");
    fireEvent.click(screen.getByRole("button", { name: "Tentar novamente" }));
    await screen.findByRole("heading", { name: "Equipe" });
    expect(api).toHaveBeenCalledTimes(2);
  });

  it("capacidade ausente ou erro de revalidação fecha antes de consultar dados", async () => {
    acesso.permitido = false;
    function Sonda() { const q = useSnapshotOperacional(true); return <div>{q.data ? "privilegiado" : "fechado"}</div>; }
    const tela = renderizar(<Sonda />);
    expect(screen.getByText("fechado")).toBeInTheDocument();
    expect(api).not.toHaveBeenCalled();
    acesso.permitido = true;
    acesso.erro = true;
    tela.rerender(<QueryClientProvider client={tela.cache}><Sonda /></QueryClientProvider>);
    await waitFor(() => expect(screen.getByText("fechado")).toBeInTheDocument());
    expect(api).not.toHaveBeenCalled();
  });

  it("serializa apenas critérios reais e distingue fila de entrada de todos", () => {
    expect(parametrosOperacionais({ entrada: true, recorte: "NOVAS", busca: "Cliente & Empresa" }).toString()).toBe("entrada=true&recorte=NOVAS&busca=Cliente+%26+Empresa");
    expect(parametrosOperacionais({ entrada: false, atendenteId: undefined }).toString()).toBe("");
  });
});
