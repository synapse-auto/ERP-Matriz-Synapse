import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { TextosProvider } from "@/lib/config/textos-provider";
import { textosReais } from "@/test/textos-reais";
import { TelaDeErro } from "./tela-de-erro";
import { ErroDeRota } from "./erro-de-rota";

vi.mock("next/navigation", () => ({ useRouter: () => ({ refresh: vi.fn() }) }));

beforeEach(() => {
  vi.restoreAllMocks();
  Object.defineProperty(navigator, "onLine", { configurable: true, value: true });
});

describe("telas de erro", () => {
  it("usa catálogo/marca da instância sem exibir rota nem detalhes técnicos", () => {
    render(<TextosProvider textos={{ ...textosReais, app: { ...textosReais.app, marca: "Instância sintética" } }}><TelaDeErro tipo="404" /></TextosProvider>);
    expect(screen.getByRole("heading", { name: textosReais.erros.naoEncontrada })).toBeInTheDocument();
    expect(screen.getByText("Instância sintética")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: textosReais.erros.inicio })).toHaveAttribute("href", "/");
  });

  it("volta quando há histórico e usa início quando não há", () => {
    const voltar = vi.spyOn(window.history, "back").mockImplementation(() => undefined);
    Object.defineProperty(window.history, "length", { configurable: true, value: 2 });
    Object.defineProperty(document, "referrer", { configurable: true, value: `${window.location.origin}/login` });
    render(<TelaDeErro tipo="404" />);
    fireEvent.click(screen.getByRole("button", { name: textosReais.erros.voltar }));
    expect(voltar).toHaveBeenCalledTimes(1);
  });

  it("só mostra tentativa em andamento enquanto a leitura real está pendente e bloqueia duplo clique", async () => {
    let concluir!: () => void;
    const tentar = vi.fn(() => new Promise<void>((resolve) => { concluir = resolve; }));
    render(<TelaDeErro tipo="backend" onTentarNovamente={tentar} />);
    const botao = screen.getByRole("button", { name: textosReais.erros.tentarNovamente });
    fireEvent.click(botao);
    fireEvent.click(botao);
    expect(tentar).toHaveBeenCalledTimes(1);
    expect(screen.getByRole("button", { name: textosReais.erros.tentando })).toBeDisabled();
    await act(async () => concluir());
    await waitFor(() => expect(botao).not.toBeDisabled());
  });

  it("não mostra stack/segredo do erro e só recupera por ação explícita", () => {
    const registrar = vi.spyOn(console, "error").mockImplementation(() => undefined);
    const retry = vi.fn();
    render(<ErroDeRota error={new Error("SEGREDO-do-erro")} retry={retry} />);
    expect(screen.queryByText(/SEGREDO/)).not.toBeInTheDocument();
    expect(registrar).toHaveBeenCalledWith("[tela] Falha de renderização", { escopo: "rota", digest: null });
    expect(retry).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole("button", { name: textosReais.erros.tentarNovamente }));
    expect(retry).toHaveBeenCalledTimes(1);
  });

  it("sem configuração/provider renderiza catálogo local e recusa logo perigosa", () => {
    const { container } = render(<TelaDeErro tipo="404" logoUrl="javascript:alert(1)" />);
    expect(screen.getByRole("heading", { name: textosReais.erros.naoEncontrada })).toBeInTheDocument();
    expect(container.querySelector("img")).toBeNull();
  });

  it("falha da logo conserva marca e controles sem imagem quebrada", () => {
    const { container } = render(<TelaDeErro tipo="404" marca="Instância sintética" logoUrl="/marca-inexistente.png" />);
    fireEvent.error(container.querySelector("img")!);
    expect(container.querySelector("img")).toBeNull();
    expect(screen.getByText("Instância sintética")).toBeInTheDocument();
    expect(screen.getByRole("link")).toBeInTheDocument();
  });

  it("perda de conexão é distinta de erro de renderização e não cria spinner fictício", () => {
    Object.defineProperty(navigator, "onLine", { configurable: true, value: false });
    render(<TelaDeErro tipo="renderizacao" />);
    expect(screen.getByRole("heading", { name: textosReais.erros.offline })).toBeInTheDocument();
    expect(screen.queryByText(textosReais.erros.tentando)).not.toBeInTheDocument();
  });
});
