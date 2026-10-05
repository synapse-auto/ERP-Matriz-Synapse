import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("@/lib/chat-interno/api", () => ({
  buscarDestinosDoEncaminhamento: vi.fn(),
  previaDoEncaminhamentoAoCliente: vi.fn(),
  encaminharAoCliente: vi.fn(),
  listarEncaminhamentosAoCliente: vi.fn(),
}));

import { ErroDeApi } from "@/lib/api/errors";
import {
  buscarDestinosDoEncaminhamento,
  encaminharAoCliente,
  listarEncaminhamentosAoCliente,
  previaDoEncaminhamentoAoCliente,
} from "@/lib/chat-interno/api";
import type {
  ChatMensagem,
  DestinoDoEncaminhamento,
  EncaminhamentoAoCliente,
  PreviaDoEncaminhamentoAoCliente,
} from "@/lib/chat-interno/types";
import { textosReais } from "@/test/textos-reais";
import { DialogoEncaminharAoCliente } from "./dialogo-encaminhar-ao-cliente";

const textos = textosReais.chatInterno.encaminharCliente;

const MENSAGEM: ChatMensagem = {
  id: "m1", conversaId: "c1", remetenteId: "u1", remetenteNome: "Bruno", tipo: "TEXTO",
  conteudo: "Segue o orçamento", enviadoEm: "2026-10-05T12:00:00Z",
};

function destino(sobras: Partial<DestinoDoEncaminhamento> = {}): DestinoDoEncaminhamento {
  return {
    atendimentoId: "a1", clienteNome: "Maria Cliente", telefoneMascarado: "5561*****1234",
    statusAtendimento: "EM_ATENDIMENTO", responsavelNome: "Ana", ...sobras,
  };
}

function previa(sobras: Partial<PreviaDoEncaminhamentoAoCliente> = {}): PreviaDoEncaminhamentoAoCliente {
  return {
    atendimentoId: "a1", clienteNome: "Maria Cliente", telefoneMascarado: "5561*****1234",
    statusAtendimento: "EM_ATENDIMENTO", responsavelNome: "Ana", efeito: "MANTEM_RESPONSAVEL_E_CONVIDA",
    tipo: "TEXTO", texto: "Segue o orçamento", legenda: null, nomeArquivo: null, mimetype: null,
    tamanhoBytes: null, podeEnviar: true, bloqueio: null, ...sobras,
  };
}

function envio(sobras: Partial<EncaminhamentoAoCliente> = {}): EncaminhamentoAoCliente {
  return {
    id: "e1", atendimentoId: "a1", mensagemInternaId: "m1", mensagemExternaId: "x1", tipo: "TEXTO",
    statusEntrega: "PENDENTE", erroEntrega: null, transferiuOLead: false, conviteCriado: true,
    reutilizado: false, ...sobras,
  };
}

let cliente: QueryClient;
const onFechar = vi.fn();

function renderizar(mensagem: ChatMensagem | null = MENSAGEM) {
  cliente = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={cliente}>
      <DialogoEncaminharAoCliente mensagem={mensagem} conversaId="c1" textos={textos} onFechar={onFechar} />
    </QueryClientProvider>,
  );
}

async function escolherMaria() {
  fireEvent.click(await screen.findByRole("button", { name: textos.escolher.replace("{cliente}", "Maria Cliente") }));
}

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(buscarDestinosDoEncaminhamento).mockResolvedValue([
    destino(),
    destino({ atendimentoId: "a3", clienteNome: "Carla Potencial", statusAtendimento: "EM_IA", responsavelNome: null, telefoneMascarado: "5561*****9999" }),
  ]);
  vi.mocked(previaDoEncaminhamentoAoCliente).mockResolvedValue(previa());
  vi.mocked(listarEncaminhamentosAoCliente).mockResolvedValue([envio()]);
});

afterEach(() => cliente?.clear());

describe("DialogoEncaminharAoCliente", () => {
  it("fechado quando não há mensagem: nada é consultado", () => {
    renderizar(null);

    expect(screen.queryByRole("dialog")).toBeNull();
    expect(buscarDestinosDoEncaminhamento).not.toHaveBeenCalled();
  });

  it("lista os destinos do servidor, com telefone mascarado e responsável", async () => {
    renderizar();

    expect(await screen.findByRole("button", { name: /Maria Cliente/ })).toBeInTheDocument();
    expect(screen.getByText("5561*****1234")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Carla Potencial/ })).toBeInTheDocument();
    expect(screen.getByText("5561*****9999")).toBeInTheDocument();
    expect(screen.getByText(textos.semResponsavel)).toBeInTheDocument();
    expect(buscarDestinosDoEncaminhamento).toHaveBeenCalledWith("");
  });

  it("a busca vai ao servidor depois de uma pausa na digitação, uma vez só", async () => {
    renderizar();
    await screen.findByRole("button", { name: /Maria Cliente/ });
    vi.mocked(buscarDestinosDoEncaminhamento).mockClear();
    vi.mocked(buscarDestinosDoEncaminhamento).mockResolvedValue([destino({ atendimentoId: "a3", clienteNome: "Carla Potencial" })]);

    const campo = screen.getByLabelText(textos.buscar);
    fireEvent.change(campo, { target: { value: "c" } });
    fireEvent.change(campo, { target: { value: "ca" } });
    fireEvent.change(campo, { target: { value: " carla " } });

    await waitFor(() => expect(buscarDestinosDoEncaminhamento).toHaveBeenCalledWith("carla"));
    expect(buscarDestinosDoEncaminhamento).toHaveBeenCalledTimes(1);
    expect(await screen.findByRole("button", { name: /Carla Potencial/ })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Maria Cliente/ })).toBeNull();
  });

  it("sem destino disponível diz o motivo; busca sem resultado diz outro; o campo de busca continua à mão", async () => {
    vi.mocked(buscarDestinosDoEncaminhamento).mockResolvedValue([]);
    renderizar();

    expect(await screen.findByText(textos.semDestinos)).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText(textos.buscar), { target: { value: "zzz" } });
    expect(await screen.findByText(textos.semResultado)).toBeInTheDocument();
    expect(screen.getByLabelText(textos.buscar)).toBeInTheDocument();
  });

  it("falha ao buscar aparece como erro, não como lista vazia, e a busca segue disponível", async () => {
    vi.mocked(buscarDestinosDoEncaminhamento).mockRejectedValue(new Error("falha"));
    renderizar();

    expect(await screen.findByText(textos.erroDestinos)).toBeInTheDocument();
    expect(screen.queryByText(textos.semDestinos)).toBeNull();
    expect(screen.getByLabelText(textos.buscar)).toBeInTheDocument();
  });

  it("mostra a prévia com cliente, telefone mascarado, responsável e o efeito, e só então permite enviar", async () => {
    renderizar();
    await escolherMaria();

    expect(await screen.findByText("5561*****1234")).toBeInTheDocument();
    expect(screen.getByText("Maria Cliente")).toBeInTheDocument();
    expect(screen.getByText("Ana")).toBeInTheDocument();
    expect(screen.getByText("Segue o orçamento")).toBeInTheDocument();
    expect(screen.getByText(textos.efeito.MANTEM_RESPONSAVEL_E_CONVIDA)).toBeInTheDocument();
    expect(previaDoEncaminhamentoAoCliente).toHaveBeenCalledWith("a1", "c1", "m1");
    expect(encaminharAoCliente).not.toHaveBeenCalled();
    expect(screen.getByRole("button", { name: textos.confirmar })).toBeEnabled();
  });

  it("cada efeito tem o seu texto do catálogo", async () => {
    vi.mocked(previaDoEncaminhamentoAoCliente).mockResolvedValue(previa({ efeito: "ASSUME_O_LEAD", responsavelNome: null }));
    renderizar();
    await escolherMaria();

    expect(await screen.findByText(textos.efeito.ASSUME_O_LEAD)).toBeInTheDocument();
    expect(screen.getByText(textos.semResponsavel, { selector: "dd" })).toBeInTheDocument();
  });

  it("bloqueio vindo do backend desabilita o envio e explica o motivo", async () => {
    vi.mocked(previaDoEncaminhamentoAoCliente).mockResolvedValue(
      previa({ podeEnviar: false, bloqueio: "FORA_DA_JANELA" }),
    );
    renderizar();
    await escolherMaria();

    expect(await screen.findByText(textos.bloqueio.FORA_DA_JANELA)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: textos.confirmar })).toBeDisabled();
  });

  it("mostra o arquivo e o tipo quando a mensagem é mídia", async () => {
    vi.mocked(previaDoEncaminhamentoAoCliente).mockResolvedValue(
      previa({ tipo: "DOCUMENTO", texto: null, legenda: "segue", nomeArquivo: "orcamento.pdf", mimetype: "application/pdf" }),
    );
    renderizar();
    await escolherMaria();

    expect(await screen.findByText("orcamento.pdf")).toBeInTheDocument();
    expect(screen.getByText(textos.tipo.DOCUMENTO)).toBeInTheDocument();
    expect(screen.getByText("segue")).toBeInTheDocument();
  });

  it("confirmar envia com a chave de idempotência, mostra o acompanhamento e atualiza as listas", async () => {
    vi.mocked(encaminharAoCliente).mockResolvedValue(envio());
    const invalidar = vi.fn();
    renderizar();
    cliente.invalidateQueries = invalidar as never;
    await escolherMaria();
    fireEvent.click(await screen.findByRole("button", { name: textos.confirmar }));

    await waitFor(() => expect(encaminharAoCliente).toHaveBeenCalledTimes(1));
    const [atendimento, conversa, mensagem, chave] = vi.mocked(encaminharAoCliente).mock.calls[0];
    expect([atendimento, conversa, mensagem]).toEqual(["a1", "c1", "m1"]);
    expect(chave).toMatch(/^[0-9a-f-]{36}$/);
    expect(await screen.findByText(textos.status.PENDENTE)).toBeInTheDocument();
    expect(screen.getByText(textos.conviteCriado)).toBeInTheDocument();
    expect(invalidar).toHaveBeenCalledWith({ queryKey: ["atendimentos"] });
  });

  it("o acompanhamento passa de PENDENTE para ENVIADO sem recarregar", async () => {
    vi.mocked(encaminharAoCliente).mockResolvedValue(envio());
    vi.mocked(listarEncaminhamentosAoCliente).mockResolvedValue([envio({ statusEntrega: "ENVIADO" })]);
    renderizar();
    await escolherMaria();
    fireEvent.click(await screen.findByRole("button", { name: textos.confirmar }));

    expect(await screen.findByText(textos.status.PENDENTE)).toBeInTheDocument();
    await waitFor(() => expect(screen.getByText(textos.status.ENVIADO)).toBeInTheDocument(), { timeout: 4000 });
  });

  it("falha do provedor aparece como FALHOU, com a explicação", async () => {
    vi.mocked(encaminharAoCliente).mockResolvedValue(envio({ statusEntrega: "FALHOU", conviteCriado: false }));
    vi.mocked(listarEncaminhamentosAoCliente).mockResolvedValue([envio({ statusEntrega: "FALHOU" })]);
    renderizar();
    await escolherMaria();
    fireEvent.click(await screen.findByRole("button", { name: textos.confirmar }));

    expect(await screen.findByText(textos.status.FALHOU)).toBeInTheDocument();
    expect(screen.getByText(textos.falhou)).toBeInTheDocument();
  });

  it("assumir o lead e reenvio idempotente são avisados", async () => {
    vi.mocked(encaminharAoCliente).mockResolvedValue(
      envio({ transferiuOLead: true, conviteCriado: false, reutilizado: true, statusEntrega: "ENVIADO" }),
    );
    renderizar();
    await escolherMaria();
    fireEvent.click(await screen.findByRole("button", { name: textos.confirmar }));

    expect(await screen.findByText(textos.assumiu)).toBeInTheDocument();
    expect(screen.getByText(textos.jaEnviado)).toBeInTheDocument();
  });

  it("duplo clique envia uma vez só", async () => {
    let resolver: (valor: EncaminhamentoAoCliente) => void = () => {};
    vi.mocked(encaminharAoCliente).mockReturnValue(new Promise((resolve) => { resolver = resolve; }));
    renderizar();
    await escolherMaria();
    const botao = await screen.findByRole("button", { name: textos.confirmar });

    // Três cliques no mesmo instante, antes de qualquer novo render: só a trava síncrona os segura.
    act(() => {
      botao.click();
      botao.click();
      botao.click();
    });

    await waitFor(() => expect(encaminharAoCliente).toHaveBeenCalledTimes(1));
    expect(await screen.findByRole("button", { name: textos.enviando })).toBeDisabled();
    expect(encaminharAoCliente).toHaveBeenCalledTimes(1);
    resolver(envio());
    expect(await screen.findByText(textos.status.PENDENTE)).toBeInTheDocument();
  });

  it("recusa do backend vira texto do catálogo; tentar de novo reaproveita a mesma chave", async () => {
    vi.mocked(encaminharAoCliente)
      .mockRejectedValueOnce(new ErroDeApi(503, null, "indisponivel"))
      .mockResolvedValueOnce(envio());
    renderizar();
    await escolherMaria();
    fireEvent.click(await screen.findByRole("button", { name: textos.confirmar }));

    expect(await screen.findByText(textos.erro.generico)).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: textos.confirmar }));
    await waitFor(() => expect(encaminharAoCliente).toHaveBeenCalledTimes(2));
    const chaves = vi.mocked(encaminharAoCliente).mock.calls.map((chamada) => chamada[3]);
    expect(chaves[0]).toBe(chaves[1]);
  });

  it.each([
    [403, null, textos.erro["403"]],
    [404, null, textos.erro["404"]],
    [409, null, textos.erro["409"]],
    [422, null, textos.erro["422"]],
    [422, "ARQUIVO_ACIMA_DO_LIMITE", textos.bloqueio.ARQUIVO_ACIMA_DO_LIMITE],
    [409, "ATENDIMENTO_FINALIZADO", textos.bloqueio.ATENDIMENTO_FINALIZADO],
  ])("HTTP %s (%s) usa o texto certo do catálogo", async (status, motivo, esperado) => {
    vi.mocked(encaminharAoCliente).mockRejectedValue(
      new ErroDeApi(status, { status, detail: "x", ...(motivo ? { motivo } : {}) }, "falha"),
    );
    renderizar();
    await escolherMaria();
    fireEvent.click(await screen.findByRole("button", { name: textos.confirmar }));

    expect(await screen.findByText(esperado)).toBeInTheDocument();
  });

  it("voltar da prévia leva de volta à lista e descarta a chave; cancelar fecha", async () => {
    renderizar();
    await escolherMaria();
    fireEvent.click(await screen.findByRole("button", { name: textos.voltar }));

    expect(await screen.findByLabelText(textos.buscar)).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: textos.cancelar }));
    expect(onFechar).toHaveBeenCalled();
  });
});
