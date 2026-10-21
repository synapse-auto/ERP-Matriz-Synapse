import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ErroDeApi } from "@/lib/api/errors";
import { textosReais } from "@/test/textos-reais";
import { definirCapacidadesDeTeste } from "@/test/capacidades-de-teste";
import type { Cancelamento, PaginaCancelamentos } from "@/lib/cancelamentos/api";
import { TooltipProvider } from "@/components/ui/tooltip";

const api = vi.hoisted(() => ({ obterConfiguracaoCancelamentos: vi.fn(), listarCancelamentos: vi.fn(), obterCancelamento: vi.fn() }));
const identidade = vi.hoisted(() => ({ usuarioId: "gestor", papel: "GESTOR", revisao: 1 }));
const viewport = vi.hoisted(() => ({ estreita: false }));
vi.mock("@/lib/cancelamentos/api", () => api);
vi.mock("@/lib/auth/auth-store", () => ({ useAuthStore: (seletor: (e: typeof identidade) => unknown) => seletor(identidade) }));
vi.mock("@/lib/config/textos-provider", () => ({ useTextos: () => textosReais }));
vi.mock("@/lib/gestao/use-gestao", () => ({ useMinhasPermissoes: () => ({ data: { revisao: identidade.revisao } }) }));
vi.mock("./use-lista-em-cartoes", () => ({ useListaEmCartoes: () => viewport.estreita }));
import Pagina from "@/app/(shell)/cancelamentos/page";

const t = textosReais.cancelamentos;
const item: Cancelamento = { id: "c1", contatoNome: "Contato sintético", profissionalNome: "Profissional A", tipoAgendamento: "Consulta",
  agendadoPara: "2026-10-25T14:30:00Z", registradoEm: null, recebidoEm: "2026-10-18T12:00:00Z", canceladoEm: null,
  descricao: "Motivo\nsegunda linha <script>alert(1)</script>" };
function montar() {
  const cache = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const resultado = render(<TooltipProvider><QueryClientProvider client={cache}><Pagina /></QueryClientProvider></TooltipProvider>);
  return { ...resultado, cache, atualizar: () => resultado.rerender(<TooltipProvider><QueryClientProvider client={cache}><Pagina /></QueryClientProvider></TooltipProvider>) };
}
beforeEach(() => {
  vi.resetAllMocks(); identidade.usuarioId = "gestor"; identidade.papel = "GESTOR"; identidade.revisao = 1;
  viewport.estreita = false;
  api.obterConfiguracaoCancelamentos.mockResolvedValue({ fusoHorario: "America/Sao_Paulo", inicioPadrao: "2026-10-01", fimPadrao: "2026-10-18",
    tamanhoPagina: 25, maximoPagina: 100, maximoDias: 90, maximoPaginas: 100, maximoBusca: 200 });
  api.listarCancelamentos.mockResolvedValue({ itens: [item], temProxima: false });
  api.obterCancelamento.mockResolvedValue(item);
});

describe("Cancelamentos na rota real", () => {
  it("separa recebimento de cancelamento ausente e so cria link com par canonico autorizado", async()=>{
    api.obterCancelamento.mockResolvedValue({...item,leadId:"lead-canonico",atendimentoId:"atendimento-canonico"});
    montar();await screen.findByText(item.contatoNome!);
    expect(screen.queryByText("18/10/2026, 09:00")).not.toBeInTheDocument();
    expect(screen.getByRole("columnheader",{name:t.canceladoEm})).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button",{name:t.detalhes}));
    const dialogo=await screen.findByRole("dialog");
    expect(await within(dialogo).findByRole("link",{name:t.abrirAtendimento})).toHaveAttribute("href","/atendimentos?leadId=lead-canonico&atendimentoId=atendimento-canonico");
    expect(within(dialogo).getByText(t.canceladoEm)).toBeInTheDocument();
    expect(within(dialogo).getAllByText(t.naoInformado).length).toBeGreaterThan(0);
    expect(within(dialogo).getByText("18/10/2026, 09:00")).toBeInTheDocument();
  });
  it("coluna usa a ocorrencia no fuso e explicita a ordenacao por registro", async()=>{
    api.listarCancelamentos.mockResolvedValue({itens:[{...item,canceladoEm:"2026-10-17T01:30:00Z"}],temProxima:false});
    montar(); await screen.findByText(item.contatoNome!);
    expect(screen.getByText("16/10/2026, 22:30")).toBeInTheDocument();
    expect(screen.queryByText("18/10/2026, 09:00")).not.toBeInTheDocument();
    expect(screen.getByText(t.ordenacao.replace("{base}",t.registro))).toBeInTheDocument();
  });
  it("identificacao atual so aparece no detalhe e nao e inferida dos dados da lista", async()=>{
    api.obterCancelamento.mockResolvedValue({...item,identificacao:{cpf:"00000000000",telefone:"556100000001"}});
    montar(); await screen.findByText(item.contatoNome!);
    expect(api.obterCancelamento).not.toHaveBeenCalled();
    expect(screen.queryByText("00000000000")).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole("button",{name:t.detalhes}));
    const dialogo=await screen.findByRole("dialog");
    expect(await within(dialogo).findByText("00000000000")).toBeInTheDocument();
    expect(within(dialogo).getByText("556100000001")).toBeInTheDocument();
    expect(within(dialogo).getByText(t.identificacaoAjuda)).toBeInTheDocument();
    expect(within(dialogo).getByText("Consulta")).toBeInTheDocument();
  });
  it("detalhe ausente ou negado nao reaproveita identificacao de outro registro", async()=>{
    api.listarCancelamentos.mockResolvedValue({itens:[item,{...item,id:"c2",contatoNome:"Outro contato"}],temProxima:false});
    api.obterCancelamento.mockResolvedValueOnce({...item,identificacao:{cpf:"00000000000",telefone:null}})
      .mockRejectedValueOnce(new ErroDeApi(404,{},""));
    montar(); await screen.findByText(item.contatoNome!);
    fireEvent.click(screen.getAllByRole("button",{name:t.detalhes})[0]);
    expect(await screen.findByText("00000000000")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button",{name:t.fechar}));
    await waitFor(()=>expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
    fireEvent.click(screen.getAllByRole("button",{name:t.detalhes})[1]);
    expect(await screen.findByText(t.naoEncontrado)).toBeInTheDocument();
    expect(screen.queryByText("00000000000")).not.toBeInTheDocument();
    expect(screen.queryByText(t.identificacaoTitulo)).not.toBeInTheDocument();
  });
  it("revalidacao oculta dados pessoais anteriores enquanto a autorizacao atual e conferida",async()=>{
    api.obterCancelamento.mockResolvedValue({...item,identificacao:{cpf:"00000000000",telefone:null}});
    const r=montar(); await screen.findByText(item.contatoNome!);
    fireEvent.click(screen.getByRole("button",{name:t.detalhes}));
    expect(await screen.findByText("00000000000")).toBeInTheDocument();
    api.obterCancelamento.mockReturnValue(new Promise(()=>{}));
    await act(async()=>{ void r.cache.invalidateQueries({predicate:q=>q.queryKey[2]==="detalhe"}); });
    await waitFor(()=>expect(screen.queryByText("00000000000")).not.toBeInTheDocument());
    expect(within(screen.getByRole("dialog")).getByRole("status")).toBeInTheDocument();
  });
  it("tela estreita mostra cartoes completos com detalhes sem duplicar a tabela", async()=>{
    viewport.estreita=true; montar();
    const cartao=await screen.findByRole("article",{name:item.contatoNome!});
    expect(within(cartao).getByText(item.profissionalNome!)).toBeInTheDocument();
    expect(within(cartao).getByText("25/10/2026, 11:30")).toBeInTheDocument();
    expect(within(cartao).getByRole("button",{name:t.detalhes})).toBeInTheDocument();
    expect(screen.queryByRole("table")).not.toBeInTheDocument();
  });
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
    expect(await screen.findByText(t.vazioPeriodo)).toBeInTheDocument(); segundo.unmount();
    api.listarCancelamentos.mockRejectedValueOnce(new ErroDeApi(403,null,"")); montar();
    expect(await screen.findByText(t.semAcesso)).toBeInTheDocument();
  });
  it("combina filtros no servidor e reinicia pagina; nao filtra pagina local", async () => {
    api.listarCancelamentos.mockResolvedValue({ itens:[item],temProxima:true }); montar();
    await screen.findByText(item.contatoNome!); fireEvent.click(screen.getByRole("button",{name:t.proxima}));
    await waitFor(() => expect(api.listarCancelamentos).toHaveBeenLastCalledWith(expect.anything(),1,25,expect.anything()));
    fireEvent.click(screen.getByRole("button",{name:t.maisFiltros}));
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
    fireEvent.click(screen.getByRole("button",{name:t.limpar}));
    await waitFor(() => expect(api.listarCancelamentos).toHaveBeenLastCalledWith(expect.objectContaining({nome:"",profissional:"",tipo:"",base:"REGISTRO",descricao:"TODAS"}),0,25,expect.anything()));
    expect(screen.getByLabelText(t.nome)).toHaveValue("");
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
    expect(screen.getAllByText(t.naoInformado)).toHaveLength(3);
    fireEvent.click(screen.getByRole("button",{name:t.detalhes})); expect(await screen.findByText(t.naoEncontrado)).toBeInTheDocument();
    expect(screen.getByText(item.contatoNome!)).toBeInTheDocument();
  });

  it("remove avisos tecnicos permanentes e deixa fuso e limite na ajuda acessivel", async () => {
    montar(); await screen.findByText(item.contatoNome!);
    expect(screen.queryByText(t.notaDatas)).not.toBeInTheDocument();
    expect(screen.queryByText(/Nenhuma alteração é realizada/)).not.toBeInTheDocument();
    expect(screen.queryByText(/Horários em America/)).not.toBeInTheDocument();
    const ajuda=screen.getByRole("button",{name:t.ajudaPeriodo});
    await act(async()=>ajuda.focus());
    const tooltip=await screen.findByRole("tooltip");
    expect(ajuda).toHaveAttribute("aria-describedby",tooltip.id);
    expect(tooltip).toHaveTextContent("90"); expect(tooltip).toHaveTextContent("America/Sao_Paulo");
  });
  it("mais filtros nao consulta ao abrir, digitar ou recolher; sinaliza apenas os aplicados", async () => {
    montar(); await screen.findByText(item.contatoNome!);
    const abrir=screen.getByRole("button",{name:t.maisFiltros});
    expect(abrir).toHaveAttribute("aria-expanded","false");
    expect(screen.getByLabelText(t.nome)).not.toBeVisible();
    fireEvent.click(abrir); expect(screen.getByLabelText(t.nome)).toBeVisible();
    fireEvent.change(screen.getByLabelText(t.nome),{target:{value:"Contato"}});
    fireEvent.change(screen.getByLabelText(t.profissional),{target:{value:"Profissional"}});
    fireEvent.click(abrir); expect(screen.getByLabelText(t.nome)).not.toBeVisible();
    expect(api.listarCancelamentos).toHaveBeenCalledTimes(1);
    fireEvent.click(screen.getByRole("button",{name:t.aplicar}));
    await waitFor(()=>expect(api.listarCancelamentos).toHaveBeenCalledTimes(2));
    expect(screen.getByText(t.filtrosAtivos.replace("{quantidade}","2"))).toBeInTheDocument();
    expect(abrir).toHaveAttribute("aria-expanded","false");
    fireEvent.click(abrir); expect(screen.getByLabelText(t.nome)).toHaveValue("Contato");
    expect(api.listarCancelamentos).toHaveBeenCalledTimes(2);
  });
  it("primeira pagina vazia nao inventa total nem exibe navegacao ou pagina isolada", async () => {
    api.listarCancelamentos.mockResolvedValue({itens:[],temProxima:false}); montar();
    expect(await screen.findByText(t.vazioPeriodo)).toBeInTheDocument();
    expect(screen.queryByRole("navigation",{name:t.paginacao})).not.toBeInTheDocument();
    expect(screen.queryByText(t.pagina.replace("{pagina}","1"))).not.toBeInTheDocument();
    expect(screen.queryByText(t.naoConfigurada)).not.toBeInTheDocument();
  });
  it("busca vazia distingue filtros aplicados e permite limpar sem alterar a API", async () => {
    montar(); await screen.findByText(item.contatoNome!);
    fireEvent.click(screen.getByRole("button",{name:t.maisFiltros}));
    fireEvent.change(screen.getByLabelText(t.nome),{target:{value:"Sem resultado"}});
    api.listarCancelamentos.mockResolvedValueOnce({itens:[],temProxima:false});
    fireEvent.click(screen.getByRole("button",{name:t.aplicar}));
    await screen.findByText(t.vazio);
    expect(screen.queryByText(t.vazioPeriodo)).not.toBeInTheDocument();
    fireEvent.click(within(screen.getByRole("status")).getByRole("button",{name:t.limpar}));
    await screen.findByText(item.contatoNome!);
    expect(screen.getByLabelText(t.nome)).toHaveValue("");
    expect(screen.getByRole("button",{name:t.maisFiltros})).toHaveAttribute("aria-expanded","false");
    expect(api.listarCancelamentos).toHaveBeenLastCalledWith(expect.objectContaining({nome:"",descricao:"TODAS"}),0,25,expect.anything());
  });
  it("pagina posterior vazia preserva volta sem inventar proxima ou numero total", async () => {
    api.listarCancelamentos.mockResolvedValueOnce({itens:[item],temProxima:true})
      .mockResolvedValueOnce({itens:[],temProxima:false});
    montar(); await screen.findByText(item.contatoNome!);
    fireEvent.click(screen.getByRole("button",{name:t.proxima}));
    await screen.findByText(t.paginaVazia);
    expect(screen.getByRole("button",{name:t.anterior})).toBeEnabled();
    expect(screen.getByRole("button",{name:t.proxima})).toBeDisabled();
    fireEvent.click(screen.getByRole("button",{name:t.anterior}));
    await screen.findByText(item.contatoNome!);
    await waitFor(()=>expect(api.listarCancelamentos).toHaveBeenLastCalledWith(expect.anything(),0,25,expect.anything()));
  });
  it("pagina unica preenchida dispensa botoes de navegacao sem utilidade", async () => {
    montar(); await screen.findByText(item.contatoNome!);
    expect(screen.queryByRole("navigation",{name:t.paginacao})).not.toBeInTheDocument();
  });
  it("loading da lista nao inventa registros e falha nao vira vazio", async () => {
    api.listarCancelamentos.mockReturnValueOnce(new Promise(()=>{}));
    const r=montar();
    await waitFor(()=>expect(api.listarCancelamentos).toHaveBeenCalledTimes(1));
    expect(await screen.findByRole("status",{name:t.carregando})).toHaveAttribute("aria-busy","true");
    expect(screen.queryByRole("table")).not.toBeInTheDocument(); r.unmount();
    api.listarCancelamentos.mockRejectedValueOnce(new ErroDeApi(503,{codigo:"FONTE_INDISPONIVEL"},""));
    montar(); expect(await screen.findByRole("alert")).toHaveTextContent(t.indisponivel);
    expect(screen.queryByText(t.vazioPeriodo)).not.toBeInTheDocument();
  });
  it("erro de periodo usa o limite real, associa aos campos e nao inicia outra leitura", async () => {
    api.obterConfiguracaoCancelamentos.mockResolvedValue({fusoHorario:"America/Sao_Paulo",inicioPadrao:"2026-10-01",fimPadrao:"2026-10-18",
      tamanhoPagina:25,maximoPagina:100,maximoDias:17,maximoPaginas:100,maximoBusca:200});
    montar(); await screen.findByText(item.contatoNome!);
    fireEvent.click(screen.getByRole("button",{name:t.aplicar}));
    const erro=screen.getByRole("alert"); expect(erro).toHaveTextContent(t.periodoExcedido.replace("{dias}","17"));
    expect(screen.getByRole("group",{name:t.periodo})).toHaveAttribute("aria-describedby",erro.id);
    expect(screen.getByLabelText(t.inicio)).toHaveFocus();
    expect(api.listarCancelamentos).toHaveBeenCalledTimes(1);
    expect(screen.getByText(item.contatoNome!)).toBeInTheDocument();
  });
  it("limite de paginas permanece efetivo e nao permite consultar alem dele", async () => {
    api.obterConfiguracaoCancelamentos.mockResolvedValue({fusoHorario:"America/Sao_Paulo",inicioPadrao:"2026-10-01",fimPadrao:"2026-10-18",
      tamanhoPagina:25,maximoPagina:100,maximoDias:90,maximoPaginas:1,maximoBusca:200});
    api.listarCancelamentos.mockResolvedValue({itens:[item],temProxima:true});
    montar(); await screen.findByText(item.contatoNome!);
    expect(screen.getByRole("button",{name:t.proxima})).toBeDisabled();
    expect(screen.getByText(t.limitePaginas)).toBeInTheDocument();
    expect(api.listarCancelamentos).toHaveBeenCalledTimes(1);
  });
});
