import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ErroDeApi } from "@/lib/api/errors";
import { textosReais } from "@/test/textos-reais";
import { definirCapacidadesDeTeste } from "@/test/capacidades-de-teste";
import type { Cancelamento, PaginaCancelamentos } from "@/lib/cancelamentos/api";

const api = vi.hoisted(() => ({ obterConfiguracaoCancelamentos: vi.fn(), listarCancelamentos: vi.fn(), obterCancelamento: vi.fn() }));
const identidade = vi.hoisted(() => ({ usuarioId: "gestor", papel: "GESTOR", revisao: 1 }));
vi.mock("@/lib/cancelamentos/api", () => api);
vi.mock("@/lib/auth/auth-store", () => ({ useAuthStore: (seletor: (e: typeof identidade) => unknown) => seletor(identidade) }));
vi.mock("@/lib/config/textos-provider", () => ({ useTextos: () => textosReais }));
vi.mock("@/lib/gestao/use-gestao", () => ({ useMinhasPermissoes: () => ({ data: { revisao: identidade.revisao } }) }));
import Pagina from "@/app/(shell)/cancelamentos/page";

const t = textosReais.cancelamentos;
const item: Cancelamento = { id: "c1", contatoNome: "Contato sintético", profissionalNome: "Profissional A", tipoAgendamento: "Consulta",
  agendadoPara: "2026-10-25T14:30:00Z", registradoEm: "2026-10-18T12:00:00Z", descricao: "Motivo\nsegunda linha <script>alert(1)</script>" };
function montar() {
  const cache = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const resultado = render(<QueryClientProvider client={cache}><Pagina /></QueryClientProvider>);
  return { ...resultado, cache, atualizar: () => resultado.rerender(<QueryClientProvider client={cache}><Pagina /></QueryClientProvider>) };
}
beforeEach(() => {
  vi.resetAllMocks(); identidade.usuarioId = "gestor"; identidade.papel = "GESTOR"; identidade.revisao = 1;
  api.obterConfiguracaoCancelamentos.mockResolvedValue({ fusoHorario: "America/Sao_Paulo", inicioPadrao: "2026-10-01", fimPadrao: "2026-10-18",
    tamanhoPagina: 25, maximoPagina: 100, maximoDias: 90, maximoPaginas: 100, maximoBusca: 200 });
  api.listarCancelamentos.mockResolvedValue({ itens: [item], temProxima: false });
  api.obterCancelamento.mockResolvedValue(item);
});

describe("Cancelamentos na rota real", () => {
  it("nega URL direta sem capacidade e nem inicia leituras", () => {
    definirCapacidadesDeTeste({ negadas: ["cancelamentos.ver"] }); montar();
    expect(screen.getByText(textosReais.gestao.acesso.semAcessoTitulo)).toBeInTheDocument();
    expect(api.obterConfiguracaoCancelamentos).not.toHaveBeenCalled(); expect(api.listarCancelamentos).not.toHaveBeenCalled();
  });
  it("configuracao e lista tem loading e nenhum registro ficticio", async () => {
    api.obterConfiguracaoCancelamentos.mockReturnValue(new Promise(() => {})); montar();
    expect(screen.getByRole("status")).toHaveTextContent(t.carregando);
    expect(screen.queryByText(item.contatoNome!)).not.toBeInTheDocument();
  });
  it("lista datas no fuso; detalhe acessivel preserva quebras e escapa HTML sem link inferido", async () => {
    montar();
    expect(await screen.findByText(item.contatoNome!)).toBeInTheDocument();
    expect(screen.getByText("25/10/2026, 11:30")).toBeInTheDocument();
    const detalhes = screen.getByRole("button", { name: t.detalhes }); detalhes.focus(); fireEvent.click(detalhes);
    const dialogo = await screen.findByRole("dialog");
    expect(await within(dialogo).findByText(/segunda linha/)).toHaveTextContent("<script>alert(1)</script>");
    expect(dialogo.querySelector("script")).toBeNull(); expect(within(dialogo).queryByRole("link")).toBeNull();
    expect(within(dialogo).getByText(t.vinculoIndisponivel)).toBeInTheDocument();
    fireEvent.click(within(dialogo).getByRole("button", { name: t.fechar }));
    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
  });
  it("nao configurado, indisponivel, vazio e 403 sao estados distintos sem erro tecnico", async () => {
    api.listarCancelamentos.mockRejectedValueOnce(new ErroDeApi(503,{ codigo: "INTEGRACAO_NAO_CONFIGURADA", detail: "SEGREDO" },""));
    const r = montar();
    expect(await screen.findByText(t.naoConfigurada)).toBeInTheDocument(); expect(screen.queryByText(t.vazio)).not.toBeInTheDocument();
    expect(screen.queryByText("SEGREDO")).not.toBeInTheDocument(); r.unmount();
    api.listarCancelamentos.mockRejectedValueOnce(new ErroDeApi(503,{ codigo: "FONTE_INDISPONIVEL" },""));
    const segundo = montar(); expect(await screen.findByText(t.indisponivel)).toBeInTheDocument();
    api.listarCancelamentos.mockResolvedValueOnce({ itens: [], temProxima: false });
    fireEvent.click(screen.getByRole("button", { name: t.tentar }));
    expect(await screen.findByText(t.vazio)).toBeInTheDocument(); segundo.unmount();
    api.listarCancelamentos.mockRejectedValueOnce(new ErroDeApi(403,null,"")); montar();
    expect(await screen.findByText(t.semAcesso)).toBeInTheDocument();
  });
  it("combina filtros no servidor e reinicia pagina; nao filtra pagina local", async () => {
    api.listarCancelamentos.mockResolvedValue({ itens:[item],temProxima:true }); montar();
    await screen.findByText(item.contatoNome!); fireEvent.click(screen.getByRole("button",{name:t.proxima}));
    await waitFor(() => expect(api.listarCancelamentos).toHaveBeenLastCalledWith(expect.anything(),1,25,expect.anything()));
    fireEvent.change(screen.getByLabelText(t.nome),{target:{value:"Outro"}});
    fireEvent.change(screen.getByLabelText(t.profissional),{target:{value:"Profissional"}});
    fireEvent.change(screen.getByLabelText(t.tipo),{target:{value:"Retorno"}});
    fireEvent.click(screen.getByRole("combobox",{name:t.base}));
    const agendamento=await screen.findByRole("option",{name:t.agendamento});
    fireEvent.pointerDown(agendamento,{pointerType:"mouse",button:0}); fireEvent.click(agendamento);
    fireEvent.click(screen.getByRole("combobox",{name:t.descricaoFiltro}));
    const semDescricao=await screen.findByRole("option",{name:t.semDescricao});
    fireEvent.pointerDown(semDescricao,{pointerType:"mouse",button:0}); fireEvent.click(semDescricao);
    fireEvent.click(screen.getByRole("button",{name:t.aplicar}));
    await waitFor(() => expect(api.listarCancelamentos).toHaveBeenLastCalledWith(expect.objectContaining({nome:"Outro",profissional:"Profissional",tipo:"Retorno",base:"AGENDAMENTO",descricao:"SEM_DESCRICAO"}),0,25,expect.anything()));
  });
  it("resposta atrasada de outro filtro nao substitui a consulta atual", async () => {
    let resolver!: (v: PaginaCancelamentos) => void;
    api.listarCancelamentos.mockReturnValueOnce(new Promise<PaginaCancelamentos>(r => { resolver = r; })); montar();
    await screen.findByRole("button",{name:t.aplicar});
    await waitFor(() => expect(api.listarCancelamentos).toHaveBeenCalledTimes(1));
    api.listarCancelamentos.mockResolvedValueOnce({itens:[{...item,contatoNome:"Resultado novo"}],temProxima:false});
    fireEvent.change(screen.getByLabelText(t.nome),{target:{value:"Novo"}}); fireEvent.click(screen.getByRole("button",{name:t.aplicar}));
    await screen.findByText("Resultado novo");
    await act(async () => resolver({itens:[{...item,contatoNome:"Resultado antigo"}],temProxima:false}));
    expect(screen.queryByText("Resultado antigo")).not.toBeInTheDocument(); expect(screen.getByText("Resultado novo")).toBeInTheDocument();
  });
  it("troca de usuario ou revisao nao mostra registros do anterior durante nova leitura", async () => {
    const r=montar(); await screen.findByText(item.contatoNome!);
    api.obterConfiguracaoCancelamentos.mockReturnValue(new Promise(() => {})); identidade.usuarioId="outro"; identidade.revisao++;
    r.atualizar(); expect(screen.queryByText(item.contatoNome!)).not.toBeInTheDocument();
  });
  it("ausencias e descricao em branco sao explicitas; erro de detalhe nao fecha a consulta", async () => {
    api.listarCancelamentos.mockResolvedValueOnce({itens:[{...item,descricao:"   ",agendadoPara:null,profissionalNome:null}],temProxima:false});
    api.obterCancelamento.mockRejectedValue(new ErroDeApi(404,null,"")); montar();
    await screen.findByText(item.contatoNome!); expect(within(screen.getByRole("table")).getByText(t.semDescricao)).toBeInTheDocument();
    expect(screen.getAllByText(t.naoInformado)).toHaveLength(2);
    fireEvent.click(screen.getByRole("button",{name:t.detalhes})); expect(await screen.findByText(t.naoEncontrado)).toBeInTheDocument();
    expect(screen.getByText(item.contatoNome!)).toBeInTheDocument();
  });
});
