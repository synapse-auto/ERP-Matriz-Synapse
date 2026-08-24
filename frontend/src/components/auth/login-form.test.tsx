import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { useAuthStore } from "@/lib/auth/auth-store";
import { TextosProvider } from "@/lib/config/textos-provider";
import type { Textos } from "@/lib/config/schema";

const navegar = vi.fn();

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: navegar }),
}));

import { LoginForm } from "./login-form";

const TEXTOS = {
  auth: { mostrarSenha: "Mostrar senha", ocultarSenha: "Ocultar senha" },
  login: {
    marcaSynapse: "Synapse",
    titulo: "Entrar no painel",
    subtitulo: "Acesse sua conta para continuar.",
    apresentacaoTitulo: "",
    apresentacaoSubtitulo: "",
    destaqueAtendimentos: "",
    destaqueAutomacao: "",
    destaqueEquipe: "",
    campoEmail: "E-mail",
    campoSenha: "Senha",
    manterSessaoAtiva: "Manter sessão ativa neste dispositivo",
    botaoEntrar: "Entrar no painel",
    entrando: "Entrando...",
    erroCredenciais: "E-mail ou senha inválidos.",
    erroGenerico: "Não foi possível entrar. Tente novamente.",
    ambienteSeguro: "Ambiente seguro",
    abrindoPainel: "Abrindo seu painel…",
    erroAbrirPainel: "",
    tentarAbrirPainel: "",
  },
} as Textos;

function renderizarFormulario() {
  return render(
    <TextosProvider textos={TEXTOS}>
      <LoginForm />
    </TextosProvider>,
  );
}

function preencherCredenciais() {
  fireEvent.change(screen.getByLabelText("E-mail"), { target: { value: "ana@empresa.test" } });
  fireEvent.change(screen.getByLabelText("Senha"), { target: { value: "senha" } });
}

describe("LoginForm", () => {
  beforeEach(() => {
    navegar.mockReset();
    vi.unstubAllGlobals();
    window.localStorage.clear();
    window.sessionStorage.clear();
    useAuthStore.setState({
      accessToken: null,
      expiraEm: null,
      email: null,
      papel: null,
      usuarioId: null,
      precisaTrocarSenha: false,
      status: "nao-autenticado",
      aberturaDoPainel: "ociosa",
    });
  });

  it.each([false, true])("não armazena access token no Web Storage com manter sessão=%s", async (manterSessaoAtiva) => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue({
        ok: true,
        json: async () => ({ accessToken: "access-token-sensível", expiraEmSegundos: 900 }),
      }),
    );
    renderizarFormulario();
    preencherCredenciais();

    const caixa = screen.getByLabelText("Manter sessão ativa neste dispositivo");
    if (manterSessaoAtiva) fireEvent.click(caixa);
    fireEvent.click(screen.getByRole("button", { name: "Entrar no painel" }));

    await waitFor(() => expect(navegar).toHaveBeenCalledWith("/"));
    expect(fetch).toHaveBeenCalledWith(
      "/api/auth/login",
      expect.objectContaining({
        body: JSON.stringify({ email: "ana@empresa.test", senha: "senha", manterSessaoAtiva }),
      }),
    );
    expect(Object.values(window.localStorage)).not.toContain("access-token-sensível");
    expect(Object.values(window.sessionStorage)).not.toContain("access-token-sensível");
  });

  it("anuncia erro de credenciais e o associa aos campos", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue({ ok: false, status: 401 }));
    renderizarFormulario();
    preencherCredenciais();

    fireEvent.submit(screen.getByRole("button", { name: "Entrar no painel" }).closest("form")!);

    const erro = await screen.findByRole("alert");
    expect(erro).toHaveTextContent("E-mail ou senha inválidos.");
    expect(screen.getByLabelText("E-mail")).toHaveAttribute("aria-describedby", erro.id);
    expect(screen.getByLabelText("Senha")).toHaveAttribute("aria-describedby", erro.id);
  });

  it("mantém o erro genérico separado de credenciais", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue({ ok: false, status: 500 }));
    renderizarFormulario();
    preencherCredenciais();

    fireEvent.submit(screen.getByRole("button", { name: "Entrar no painel" }).closest("form")!);

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Não foi possível entrar. Tente novamente.",
    );
  });

  it("oferece controle acessível para mostrar e ocultar a senha", () => {
    renderizarFormulario();

    const botao = screen.getByRole("button", { name: "Mostrar senha" });
    fireEvent.click(botao);

    expect(screen.getByRole("button", { name: "Ocultar senha" })).toBeInTheDocument();
    expect(screen.getByLabelText("Senha")).toHaveAttribute("type", "text");
  });
});
