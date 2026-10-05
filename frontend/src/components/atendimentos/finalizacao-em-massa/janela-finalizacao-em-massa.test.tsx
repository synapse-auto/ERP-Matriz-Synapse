import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { ErroDeApi } from "@/lib/api/errors";
import type {
  OperacaoDeFinalizacao,
  PreviaDeFinalizacao,
} from "@/lib/finalizacao-em-massa/types";
import { textosReais } from "@/test/textos-reais";

const api = vi.hoisted(() => ({
  preverFinalizacao: vi.fn(),
  iniciarFinalizacao: vi.fn(),
  obterOperacaoDeFinalizacao: vi.fn(),
  listarOperacoesDeFinalizacao: vi.fn(),
  listarItensDaFinalizacao: vi.fn(),
}));
vi.mock("@/lib/finalizacao-em-massa/api", () => api);

vi.mock("@/lib/atendimento/use-transferir-finalizar", () => ({
  useQuantidadeAtendimentosFinalizaveis: () => ({
    data: {
      quantidade: 3,
      porAtendente: [
        { atendenteId: "u-clayton", nome: "Clayton", quantidade: 2 },
        { atendenteId: "u-nayara", nome: "Nayara", quantidade: 1 },
      ],
    },
    isLoading: false,
    isError: false,
    refetch: vi.fn(),
  }),
}));

vi.mock("@/lib/config/textos-provider", () => ({ useTextos: () => textosReais }));

// Os seletores com popover/calendário têm testes próprios; aqui interessa o fluxo da janela.
vi.mock("@/components/ui/seletor-data", () => ({
  SeletorData: ({ id, valor, onChange }: { id: string; valor: string; onChange: (valor: string) => void }) => (
    <input id={id} value={valor} onChange={(evento) => onChange(evento.target.value)} />
  ),
}));
vi.mock("@/components/ui/seletor-multiplo", () => ({
  SeletorMultiplo: ({
    valores,
    opcoes,
    onChange,
  }: {
    valores: string[];
    opcoes: { valor: string; rotulo: string }[];
    onChange: (valores: string[]) => void;
  }) => (
    <div role="group">
      {opcoes.map((opcao) => (
        <label key={opcao.valor}>
          <input
            type="checkbox"
            checked={valores.includes(opcao.valor)}
            onChange={() =>
              onChange(
                valores.includes(opcao.valor)
                  ? valores.filter((valor) => valor !== opcao.valor)
                  : [...valores, opcao.valor],
              )
            }
          />
          {opcao.rotulo}
        </label>
      ))}
    </div>
  ),
}));

import { JanelaFinalizacaoEmMassa } from "./janela-finalizacao-em-massa";

const textos = textosReais.atendimentos.finalizacaoEmMassa;

function previa(parcial: Partial<PreviaDeFinalizacao> = {}): PreviaDeFinalizacao {
  return {
    total: 3,
    porAtendente: [
      { atendenteId: "u-clayton", nome: "Clayton", quantidade: 2 },
      { atendenteId: "u-nayara", nome: "Nayara", quantidade: 1 },
    ],
    periodoInicio: "2026-09-01T03:00:00Z",
    periodoFim: "2026-09-03T03:00:00Z",
    fuso: "America/Sao_Paulo",
    limite: 5000,
    excedeLimite: false,
    ...parcial,
  };
}

function operacao(parcial: Partial<OperacaoDeFinalizacao> = {}): OperacaoDeFinalizacao {
  return {
    id: "op-1",
    solicitanteId: "gestor-1",
    atendenteIds: ["u-clayton"],
    periodo: {
      inicio: "2026-09-01T03:00:00Z",
      fim: "2026-09-03T03:00:00Z",
      fuso: "America/Sao_Paulo",
      de: "2026-09-01",
      ate: "2026-09-02",
      horaInicio: null,
      horaFim: null,
    },
    status: "PENDENTE",
    encontrados: 10,
    pendentes: 10,
    processados: 0,
    finalizados: 0,
    ignorados: 0,
    falhas: 0,
    percentual: 0,
    criadaEm: "2026-09-03T12:00:00Z",
    iniciadaEm: null,
    concluidaEm: null,
    repetida: false,
    ...parcial,
  };
}

function renderizar(aberta = true) {
  const cliente = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const invalidar = vi.spyOn(cliente, "invalidateQueries");
  const onFechar = vi.fn();
  render(
    <QueryClientProvider client={cliente}>
      <JanelaFinalizacaoEmMassa aberta={aberta} onFechar={onFechar} />
    </QueryClientProvider>,
  );
  return { invalidar, onFechar };
}

function preencherFiltros() {
  fireEvent.click(screen.getByLabelText("Clayton"));
  fireEvent.change(screen.getByLabelText(textos.periodo.de), { target: { value: "2026-09-01" } });
  fireEvent.change(screen.getByLabelText(textos.periodo.ate), { target: { value: "2026-09-02" } });
}

async function irParaConfirmacao() {
  preencherFiltros();
  const revisar = screen.getByRole("button", { name: textos.confirmacao.revisar });
  await waitFor(() => expect(revisar).toBeEnabled());
  fireEvent.click(revisar);
}

beforeEach(() => {
  Object.values(api).forEach((mock) => mock.mockReset());
  api.preverFinalizacao.mockResolvedValue(previa());
  api.listarOperacoesDeFinalizacao.mockResolvedValue([]);
  api.listarItensDaFinalizacao.mockResolvedValue({ itens: [], pagina: 0, tamanho: 50 });
});

afterEach(() => {
  vi.useRealTimers();
});

describe("JanelaFinalizacaoEmMassa — filtros e prévia", () => {
  it("começa pedindo os filtros, sem consultar prévia e com o botão de revisar bloqueado", () => {
    renderizar();

    expect(screen.getByText(textos.problemas.SEM_ATENDENTE)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: textos.confirmacao.revisar })).toBeDisabled();
    expect(api.preverFinalizacao).not.toHaveBeenCalled();
  });

  it("envia à prévia exatamente os filtros escolhidos e mostra o total e a contagem por atendente", async () => {
    renderizar();
    preencherFiltros();
    fireEvent.change(screen.getByLabelText(textos.periodo.horaInicio), { target: { value: "09:00" } });

    expect(await screen.findByText("3 atendimento(s) serão finalizados")).toBeInTheDocument();
    expect(api.preverFinalizacao).toHaveBeenLastCalledWith({
      atendenteIds: ["u-clayton"],
      de: "2026-09-01",
      ate: "2026-09-02",
      horaInicio: "09:00",
      horaFim: null,
    });
    const lista = screen.getByRole("list", { name: textos.previa.porAtendente });
    expect(lista).toHaveTextContent("Clayton");
    expect(lista).toHaveTextContent("Nayara");
    expect(screen.getByText("Fuso considerado: America/Sao_Paulo")).toBeInTheDocument();
  });

  it("sem resultado avisa e bloqueia a revisão", async () => {
    api.preverFinalizacao.mockResolvedValue(previa({ total: 0 }));
    renderizar();
    preencherFiltros();

    expect(await screen.findByText(textos.previa.nenhum)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: textos.confirmacao.revisar })).toBeDisabled();
  });

  it("acima do limite por operação bloqueia a revisão e explica", async () => {
    api.preverFinalizacao.mockResolvedValue(previa({ total: 6000, excedeLimite: true }));
    renderizar();
    preencherFiltros();

    expect(await screen.findByRole("alert")).toHaveTextContent("limite de 5000 por operação");
    expect(screen.getByRole("button", { name: textos.confirmacao.revisar })).toBeDisabled();
  });

  it("período invertido não consulta a prévia", () => {
    renderizar();
    fireEvent.click(screen.getByLabelText("Clayton"));
    fireEvent.change(screen.getByLabelText(textos.periodo.de), { target: { value: "2026-09-05" } });
    fireEvent.change(screen.getByLabelText(textos.periodo.ate), { target: { value: "2026-09-02" } });

    expect(screen.getByText(textos.problemas.PERIODO_INVERTIDO)).toBeInTheDocument();
    expect(api.preverFinalizacao).not.toHaveBeenCalled();
  });

  it("falha na prévia mostra a mensagem do servidor e permite tentar de novo", async () => {
    api.preverFinalizacao.mockRejectedValueOnce(
      new ErroDeApi(403, { detail: "Você não tem acesso a este atendente." }, "erro"),
    );
    renderizar();
    preencherFiltros();

    expect(await screen.findByText("Você não tem acesso a este atendente.")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: textosReais.estados.tentarNovamente }));

    expect(await screen.findByText("3 atendimento(s) serão finalizados")).toBeInTheDocument();
  });
});

describe("JanelaFinalizacaoEmMassa — confirmação", () => {
  it("exige uma segunda etapa com o aviso de que não dá para desfazer e o resumo dos filtros", async () => {
    renderizar();
    await irParaConfirmacao();

    expect(screen.getByText(textos.confirmacao.aviso)).toBeInTheDocument();
    expect(screen.getByText(/3 atendimento\(s\) de Clayton, entre 01\/09\/2026 e 02\/09\/2026\./)).toBeInTheDocument();
    expect(api.iniciarFinalizacao).not.toHaveBeenCalled();

    fireEvent.click(screen.getByRole("button", { name: textos.confirmacao.voltar }));
    expect(screen.getByLabelText("Clayton")).toBeChecked();
  });

  it("duplo clique em confirmar cria uma operação só, com Idempotency-Key", async () => {
    api.iniciarFinalizacao.mockReturnValue(new Promise(() => {}));
    renderizar();
    await irParaConfirmacao();

    const confirmar = screen.getByRole("button", { name: "Finalizar 3" });
    fireEvent.click(confirmar);
    fireEvent.click(confirmar);

    await waitFor(() => expect(api.iniciarFinalizacao).toHaveBeenCalled());
    await act(async () => {
      await Promise.resolve();
    });
    expect(api.iniciarFinalizacao).toHaveBeenCalledTimes(1);
    const [filtro, chave] = api.iniciarFinalizacao.mock.calls[0];
    expect(filtro).toEqual({
      atendenteIds: ["u-clayton"],
      de: "2026-09-01",
      ate: "2026-09-02",
      horaInicio: null,
      horaFim: null,
    });
    expect(chave).toMatch(/^fm-/);
    expect(await screen.findByRole("button", { name: textos.confirmacao.iniciando })).toBeDisabled();
  });

  it("erro do servidor mostra o motivo e a nova tentativa reaproveita a mesma chave", async () => {
    api.iniciarFinalizacao
      .mockRejectedValueOnce(new ErroDeApi(409, { detail: "Já existe uma finalização em massa em andamento." }, "erro"))
      .mockResolvedValueOnce(operacao());
    api.obterOperacaoDeFinalizacao.mockResolvedValue(operacao());
    renderizar();
    await irParaConfirmacao();

    fireEvent.click(screen.getByRole("button", { name: "Finalizar 3" }));
    expect(await screen.findByText("Já existe uma finalização em massa em andamento.")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Finalizar 3" }));

    await waitFor(() => expect(api.iniciarFinalizacao).toHaveBeenCalledTimes(2));
    expect(api.iniciarFinalizacao.mock.calls[1][1]).toBe(api.iniciarFinalizacao.mock.calls[0][1]);
  });
});

describe("JanelaFinalizacaoEmMassa — acompanhamento e resultado", () => {
  it("mostra o progresso, atualiza a lista de atendimentos e chega ao resultado final", async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    api.iniciarFinalizacao.mockResolvedValue(operacao());
    api.obterOperacaoDeFinalizacao
      .mockResolvedValueOnce(operacao({ status: "EM_ANDAMENTO", processados: 4, finalizados: 4, pendentes: 6, percentual: 40 }))
      .mockResolvedValue(
        operacao({
          status: "CONCLUIDA",
          processados: 10,
          finalizados: 8,
          ignorados: 2,
          pendentes: 0,
          percentual: 100,
          concluidaEm: "2026-09-03T12:01:00Z",
        }),
      );
    const { invalidar } = renderizar();
    await irParaConfirmacao();

    fireEvent.click(screen.getByRole("button", { name: "Finalizar 3" }));

    expect(await screen.findByText("4 de 10 processados")).toBeInTheDocument();
    expect(screen.getByRole("progressbar")).toHaveAttribute("aria-valuenow", "40");
    expect(screen.getByText(textos.andamento.segundoPlano)).toBeInTheDocument();
    expect(invalidar).toHaveBeenCalledWith({ queryKey: ["atendimentos"] });

    await act(async () => {
      await vi.advanceTimersByTimeAsync(2000);
    });

    expect(await screen.findByText(textos.resultado.tituloParcial)).toBeInTheDocument();
    expect(screen.getByText("8 finalizado(s)")).toBeInTheDocument();
    expect(screen.getByText("2 ignorado(s)")).toBeInTheDocument();
    expect(screen.queryByText(/com falha/)).not.toBeInTheDocument();
  });

  it("ao reabrir com operação ativa, vai direto para o andamento e não oferece novo filtro", async () => {
    api.listarOperacoesDeFinalizacao.mockResolvedValue([
      operacao({ status: "EM_ANDAMENTO", processados: 5, finalizados: 5, percentual: 50 }),
    ]);
    api.obterOperacaoDeFinalizacao.mockResolvedValue(
      operacao({ status: "EM_ANDAMENTO", processados: 5, finalizados: 5, percentual: 50 }),
    );
    renderizar();

    expect(await screen.findByText("5 de 10 processados")).toBeInTheDocument();
    expect(screen.queryByLabelText(textos.periodo.de)).not.toBeInTheDocument();
  });

  it("operações recentes reabrem o resultado com o motivo de cada item ignorado", async () => {
    const concluida = operacao({
      status: "CONCLUIDA",
      processados: 7,
      finalizados: 5,
      ignorados: 2,
      pendentes: 0,
      encontrados: 7,
      percentual: 100,
    });
    api.listarOperacoesDeFinalizacao.mockResolvedValue([concluida]);
    api.obterOperacaoDeFinalizacao.mockResolvedValue(concluida);
    api.listarItensDaFinalizacao.mockImplementation((_id: string, status: string) =>
      Promise.resolve({
        itens:
          status === "IGNORADO"
            ? [
                {
                  atendimentoId: "a-1",
                  atendenteId: "u-clayton",
                  atendenteNome: "Clayton",
                  leadNome: "Maria",
                  status: "IGNORADO",
                  motivo: "TRANSFERIDO",
                  processadoEm: "2026-09-03T12:00:30Z",
                },
              ]
            : [],
        pagina: 0,
        tamanho: 50,
      }),
    );
    renderizar();

    fireEvent.click(await screen.findByRole("button", { name: textos.recentes.abrir }));
    expect(await screen.findByText(textos.resultado.tituloParcial)).toBeInTheDocument();
    fireEvent.click(screen.getByRole("tab", { name: textos.resultado.abas.IGNORADO }));

    expect(await screen.findByText("Maria")).toBeInTheDocument();
    expect(screen.getByText(/Foi transferido para outro atendente/)).toBeInTheDocument();
    expect(api.listarItensDaFinalizacao).toHaveBeenCalledWith("op-1", "IGNORADO", 0, 50);
  });

  it("falhas aparecem no resumo e em aba própria", async () => {
    const comFalha = operacao({
      status: "CONCLUIDA",
      processados: 3,
      finalizados: 2,
      falhas: 1,
      pendentes: 0,
      encontrados: 3,
      percentual: 100,
    });
    api.listarOperacoesDeFinalizacao.mockResolvedValue([comFalha]);
    api.obterOperacaoDeFinalizacao.mockResolvedValue(comFalha);
    renderizar();

    fireEvent.click(await screen.findByRole("button", { name: textos.recentes.abrir }));

    expect(await screen.findByText("1 com falha")).toBeInTheDocument();
    expect(screen.getByRole("tab", { name: textos.resultado.abas.FALHA })).toBeInTheDocument();
  });

  it("'Nova finalização' volta ao formulário vazio, sem reabrir a operação concluída", async () => {
    const concluida = operacao({ status: "CONCLUIDA", processados: 10, finalizados: 10, pendentes: 0, percentual: 100 });
    api.listarOperacoesDeFinalizacao.mockResolvedValue([concluida]);
    api.obterOperacaoDeFinalizacao.mockResolvedValue(concluida);
    renderizar();
    fireEvent.click(await screen.findByRole("button", { name: textos.recentes.abrir }));
    await screen.findByText(textos.resultado.titulo);

    fireEvent.click(screen.getByRole("button", { name: textos.resultado.nova }));

    expect(await screen.findByLabelText(textos.periodo.de)).toHaveValue("");
    expect(screen.getByLabelText("Clayton")).not.toBeChecked();
  });
});

describe("JanelaFinalizacaoEmMassa — janela", () => {
  it("fechada, não renderiza nada nem consulta o servidor", () => {
    renderizar(false);

    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    expect(api.listarOperacoesDeFinalizacao).not.toHaveBeenCalled();
    expect(api.preverFinalizacao).not.toHaveBeenCalled();
  });

  it("fechar avisa o pai sem cancelar nada no servidor", () => {
    const { onFechar } = renderizar();

    fireEvent.click(screen.getByRole("button", { name: textos.fechar }));

    expect(onFechar).toHaveBeenCalledTimes(1);
    expect(api.iniciarFinalizacao).not.toHaveBeenCalled();
  });
});
