import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { StrictMode } from "react";

const apiFetchBlob = vi.fn();
const URLOriginal = URL;

vi.mock("@/lib/api/http-client", () => ({
  apiFetchBlob: (...argumentos: unknown[]) => apiFetchBlob(...argumentos),
}));

import type { Textos } from "@/lib/config/schema";
import { AvatarDoGrupo } from "./avatar-do-grupo";
import { CabecalhoChatInterno } from "./componentes-chat-interno";

function comProvider(filho: React.ReactNode) {
  const cliente = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return <QueryClientProvider client={cliente}>{filho}</QueryClientProvider>;
}

describe("AvatarDoGrupo", () => {
  beforeEach(() => {
    apiFetchBlob.mockReset();
    class URLComBlob extends URLOriginal {
      static createObjectURL = vi.fn(() => "blob:foto-do-grupo");
      static revokeObjectURL = vi.fn();
    }
    vi.stubGlobal("URL", URLComBlob);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("grupo com foto: carrega a imagem pelo cliente autenticado, pela URL versionada", async () => {
    apiFetchBlob.mockResolvedValue(new Blob(["png"], { type: "image/png" }));

    render(comProvider(
      <AvatarDoGrupo id="g1" nome="Operação" tamanho="lista" fotoUrl="/api/v1/chat-interno/conversas/g1/foto?v=7" fotoAlt="Foto do grupo Operação" />,
    ));

    await waitFor(() => expect(screen.getByRole("img", { name: "Foto do grupo Operação" })).toHaveAttribute("src", "blob:foto-do-grupo"));
    expect(apiFetchBlob).toHaveBeenCalledWith("/api/v1/chat-interno/conversas/g1/foto?v=7");
  });

  it("versão nova da URL busca a imagem de novo: o cache nunca serve a foto antiga", async () => {
    apiFetchBlob.mockResolvedValue(new Blob(["png"], { type: "image/png" }));
    const cliente = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    const arvore = (url: string) => (
      <QueryClientProvider client={cliente}>
        <AvatarDoGrupo id="g1" nome="Operação" tamanho="lista" fotoUrl={url} fotoAlt="foto" />
      </QueryClientProvider>
    );
    const { rerender } = render(arvore("/api/v1/chat-interno/conversas/g1/foto?v=1"));
    await waitFor(() => expect(apiFetchBlob).toHaveBeenCalledTimes(1));

    rerender(arvore("/api/v1/chat-interno/conversas/g1/foto?v=2"));

    await waitFor(() => expect(apiFetchBlob).toHaveBeenCalledTimes(2));
    expect(apiFetchBlob).toHaveBeenLastCalledWith("/api/v1/chat-interno/conversas/g1/foto?v=2");
  });

  it("reabrir uma foto já em cache no StrictMode mantém a URL exibida válida", async () => {
    let sequencia = 0;
    vi.mocked(URL.createObjectURL).mockImplementation(() => `blob:grupo-${++sequencia}`);
    const cliente = new QueryClient();
    const caminho = "/api/v1/chat-interno/conversas/g1/foto?v=1";
    cliente.setQueryData(["avatar", caminho], new Blob(["png"], { type: "image/png" }));
    render(<StrictMode><QueryClientProvider client={cliente}><AvatarDoGrupo id="g1" nome="Grupo" tamanho="painel" fotoUrl={caminho} fotoAlt="foto" /></QueryClientProvider></StrictMode>);
    const imagem = await screen.findByRole("img", { name: "foto" });
    expect(URL.revokeObjectURL).not.toHaveBeenCalledWith(imagem.getAttribute("src"));
  });

  it("grupo sem foto: o ícone de grupo de sempre, sem imagem e sem buscar nada", () => {
    const { container } = render(comProvider(<AvatarDoGrupo id="g1" nome="Operação" tamanho="painel" fotoUrl={null} />));

    expect(screen.queryByRole("img")).not.toBeInTheDocument();
    expect(container.querySelector("svg")).not.toBeNull();
    expect(container.firstElementChild).toHaveClass("size-16", "rounded-xl", "bg-primary/15", "text-primary");
    expect(apiFetchBlob).not.toHaveBeenCalled();
  });

  it("foto que não carrega mantém o ícone de grupo, com a cor do design system", async () => {
    apiFetchBlob.mockRejectedValue(new Error("404"));

    const { container } = render(comProvider(<AvatarDoGrupo id="g1" nome="Operação Vidro" tamanho="lista" fotoUrl="/api/v1/chat-interno/conversas/g1/foto?v=1" />));

    await waitFor(() => expect(apiFetchBlob).toHaveBeenCalledOnce());
    expect(container.querySelector(".lucide-users-round")).toBeInTheDocument();
    expect(screen.queryByRole("img")).not.toBeInTheDocument();
  });

  it("cada lugar usa o seu tamanho", () => {
    const { container: lista } = render(comProvider(<AvatarDoGrupo id="a" nome="A" tamanho="lista" />));
    const { container: cabecalho } = render(comProvider(<AvatarDoGrupo id="b" nome="B" tamanho="cabecalho" />));
    const { container: painel } = render(comProvider(<AvatarDoGrupo id="c" nome="C" tamanho="painel" />));

    expect(lista.firstElementChild).toHaveClass("size-10");
    expect(cabecalho.firstElementChild).toHaveClass("size-10");
    expect(painel.firstElementChild).toHaveClass("size-16");
  });
});

describe("CabecalhoChatInterno com foto de grupo", () => {
  const textos = {
    titulo: "Chat interno",
    tipoGrupo: "Grupo",
    tipoDireta: "Conversa direta",
    retrair: "Retrair",
    reabrir: "Reabrir dados do grupo",
    fotoGrupo: { fotoAlt: "Foto do grupo {nome}" },
  } as unknown as Textos["chatInterno"];

  beforeEach(() => {
    apiFetchBlob.mockReset();
    class URLComBlob extends URLOriginal {
      static createObjectURL = vi.fn(() => "blob:cabecalho");
      static revokeObjectURL = vi.fn();
    }
    vi.stubGlobal("URL", URLComBlob);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("mostra a foto do grupo no cabeçalho", async () => {
    apiFetchBlob.mockResolvedValue(new Blob(["png"], { type: "image/png" }));

    render(comProvider(
      <CabecalhoChatInterno
        textos={textos}
        conversa={{ id: "g1", tipo: "GRUPO", participantes: "Operação", ultimaMensagem: null, ultimaMensagemEm: null, naoLidas: 0, fotoUrl: "/api/v1/chat-interno/conversas/g1/foto?v=3" }}
      />,
    ));

    expect(await screen.findByRole("img", { name: "Foto do grupo Operação" })).toHaveAttribute("src", "blob:cabecalho");
    expect(screen.getByText("Grupo")).toBeInTheDocument();
  });

  it("sem foto, o cabeçalho do grupo mantém o ícone", () => {
    const { container } = render(comProvider(
      <CabecalhoChatInterno
        textos={textos}
        conversa={{ id: "g1", tipo: "GRUPO", participantes: "Operação", ultimaMensagem: null, ultimaMensagemEm: null, naoLidas: 0, fotoUrl: null }}
      />,
    ));

    expect(screen.queryByRole("img")).not.toBeInTheDocument();
    expect(container.querySelector("header span.bg-primary\\/15 svg")).not.toBeNull();
  });
});
