import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi, type Mock } from "vitest";

const apiFetchBlob = vi.fn();
const URLOriginal = URL;

vi.mock("@/lib/api/http-client", () => ({
  apiFetchBlob: (...argumentos: unknown[]) => apiFetchBlob(...argumentos),
}));

vi.mock("@/lib/chat-interno/api", () => ({
  atualizarFotoDoGrupoChat: vi.fn(),
  removerFotoDoGrupoChat: vi.fn(),
}));

import { ErroDeApi } from "@/lib/api/errors";
import { atualizarFotoDoGrupoChat, removerFotoDoGrupoChat } from "@/lib/chat-interno/api";
import type { ChatConversa } from "@/lib/chat-interno/types";
import type { Textos } from "@/lib/config/schema";
import { SecaoFotoDoGrupo } from "./foto-do-grupo";

const textos = {
  titulo: "Foto do grupo",
  alterar: "Alterar foto",
  remover: "Remover foto",
  escolher: "Escolher imagem para a foto do grupo",
  previa: "Prévia da nova foto do grupo",
  confirmar: "Salvar foto",
  enviando: "Enviando…",
  cancelar: "Cancelar",
  fotoAlt: "Foto do grupo {nome}",
  erroTipo: "Escolha uma imagem JPEG, PNG ou WebP.",
  erroPermissao: "Somente quem criou o grupo pode alterar a foto.",
  erroTamanho: "A imagem é grande demais.",
  erroImagem: "Não foi possível usar essa imagem.",
  erroIndisponivel: "O armazenamento de imagens está indisponível.",
  erroGenerico: "Não foi possível salvar a foto.",
} as unknown as Textos["chatInterno"]["fotoGrupo"];

const CONVERSAS: ChatConversa[] = [
  { id: "g1", tipo: "GRUPO", participantes: "Operação", ultimaMensagem: null, ultimaMensagemEm: null, naoLidas: 0, fotoUrl: "/api/v1/chat-interno/conversas/g1/foto?v=1", podeAlterarFoto: true },
  { id: "g2", tipo: "GRUPO", participantes: "Outro", ultimaMensagem: null, ultimaMensagemEm: null, naoLidas: 0, fotoUrl: null },
];

let cliente: QueryClient;
let revogadas: (url: string) => void;
let revogadasEspia: Mock<(url: string) => void>;

function renderizar(props: Partial<React.ComponentProps<typeof SecaoFotoDoGrupo>> = {}) {
  cliente = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  cliente.setQueryData(["chat-interno", "conversas"], CONVERSAS);
  return render(
    <QueryClientProvider client={cliente}>
      <SecaoFotoDoGrupo conversaId="g1" nome="Operação" fotoUrl={null} podeAlterar textos={textos} {...props} />
    </QueryClientProvider>,
  );
}

function escolher(arquivo: File) {
  fireEvent.change(screen.getByLabelText("Escolher imagem para a foto do grupo"), { target: { files: [arquivo] } });
}

const png = () => new File(["png"], "grupo.png", { type: "image/png" });

describe("SecaoFotoDoGrupo", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    apiFetchBlob.mockResolvedValue(new Blob(["png"], { type: "image/png" }));
    revogadasEspia = vi.fn<(url: string) => void>();
    revogadas = (url: string) => revogadasEspia(url);
    let contador = 0;
    class URLComBlob extends URLOriginal {
      static createObjectURL = vi.fn(() => `blob:previa-${++contador}`);
      static revokeObjectURL = revogadas;
    }
    vi.stubGlobal("URL", URLComBlob);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  describe("permissão vinda do backend", () => {
    it("sem permissão: só o avatar, nenhum controle de alterar nem de remover", () => {
      renderizar({ podeAlterar: false, fotoUrl: "/api/v1/chat-interno/conversas/g1/foto?v=1" });

      expect(screen.queryByRole("button", { name: "Alterar foto" })).not.toBeInTheDocument();
      expect(screen.queryByRole("button", { name: "Remover foto" })).not.toBeInTheDocument();
      expect(screen.queryByLabelText("Escolher imagem para a foto do grupo")).not.toBeInTheDocument();
    });

    it("com permissão e sem foto: oferece alterar, não remover", () => {
      renderizar();

      expect(screen.getByRole("button", { name: "Alterar foto" })).toBeEnabled();
      expect(screen.queryByRole("button", { name: "Remover foto" })).not.toBeInTheDocument();
    });

    it("com permissão e com foto: oferece alterar e remover", () => {
      renderizar({ fotoUrl: "/api/v1/chat-interno/conversas/g1/foto?v=1" });

      expect(screen.getByRole("button", { name: "Alterar foto" })).toBeEnabled();
      expect(screen.getByRole("button", { name: "Remover foto" })).toBeEnabled();
    });

    it("o seletor só aceita JPEG, PNG e WebP", () => {
      renderizar();

      expect(screen.getByLabelText("Escolher imagem para a foto do grupo")).toHaveAttribute("accept", expect.stringContaining("image/webp"));
    });
  });

  describe("troca de foto", () => {
    it("mostra a prévia, só envia depois de confirmar e atualiza a lista na hora", async () => {
      vi.mocked(atualizarFotoDoGrupoChat).mockResolvedValue({ fotoUrl: "/api/v1/chat-interno/conversas/g1/foto?v=2" });
      renderizar({ fotoUrl: "/api/v1/chat-interno/conversas/g1/foto?v=1" });
      const arquivo = png();

      escolher(arquivo);

      expect(screen.getByRole("img", { name: "Prévia da nova foto do grupo" })).toHaveAttribute("src", "blob:previa-1");
      expect(atualizarFotoDoGrupoChat).not.toHaveBeenCalled();
      expect(screen.getByRole("button", { name: "Salvar foto" })).toBeEnabled();
      expect(screen.getByRole("button", { name: "Cancelar" })).toBeEnabled();
      expect(screen.queryByRole("button", { name: "Alterar foto" })).not.toBeInTheDocument();

      fireEvent.click(screen.getByRole("button", { name: "Salvar foto" }));

      await waitFor(() => expect(atualizarFotoDoGrupoChat).toHaveBeenCalledWith("g1", arquivo));
      await waitFor(() => expect(screen.queryByRole("img", { name: "Prévia da nova foto do grupo" })).not.toBeInTheDocument());
      const lista = cliente.getQueryData<ChatConversa[]>(["chat-interno", "conversas"]);
      expect(lista?.find((c) => c.id === "g1")?.fotoUrl).toBe("/api/v1/chat-interno/conversas/g1/foto?v=2");
      expect(lista?.find((c) => c.id === "g2")?.fotoUrl).toBeNull();
      expect(revogadasEspia).toHaveBeenCalledWith("blob:previa-1");
      expect(screen.queryByRole("alert")).not.toBeInTheDocument();
    });

    it.each(["alterar", "remover"])("%s atualiza todas as páginas da inbox sem alterar outros grupos, conversas diretas ou leads", async (acao) => {
      vi.mocked(atualizarFotoDoGrupoChat).mockResolvedValue({ fotoUrl: "/api/v1/chat-interno/conversas/g1/foto?v=2" });
      vi.mocked(removerFotoDoGrupoChat).mockResolvedValue({ fotoUrl: null });
      renderizar({ fotoUrl: CONVERSAS[0].fotoUrl });
      const alvo = { tipo: "EQUIPE_INTERNA", tipoConversa: "GRUPO", conversaId: "g1", avatarUrl: "antiga" };
      const outro = { ...alvo, conversaId: "g2" };
      const direta = { ...alvo, tipoConversa: "DIRETA" };
      const lead = { tipo: "ATENDIMENTO", atendimentoId: "a1", leadFotoUrl: "foto-lead" };
      const chave = ["atendimentos", "inbox", "TODOS"];
      const inbox = { pages: [{ itens: [alvo, outro, direta, lead], proximoCursor: "cursor" }, { itens: [alvo], proximoCursor: null }], pageParams: [null, "cursor"] };
      cliente.setQueryData(chave, inbox);
      cliente.setQueryData(["atendimentos", "inbox", "PENDENTES"], inbox);
      if (acao === "alterar") {
        escolher(png());
        fireEvent.click(screen.getByRole("button", { name: "Salvar foto" }));
      } else {
        fireEvent.click(screen.getByRole("button", { name: "Remover foto" }));
      }
      const esperado = acao === "alterar" ? "/api/v1/chat-interno/conversas/g1/foto?v=2" : null;
      await waitFor(() => {
        for (const visao of ["TODOS", "PENDENTES"]) {
          const resultado = cliente.getQueryData<typeof inbox>(["atendimentos", "inbox", visao])!;
          expect(resultado.pages[0].itens[0]).toEqual({ ...alvo, avatarUrl: esperado });
          expect(resultado.pages[1].itens[0]).toEqual({ ...alvo, avatarUrl: esperado });
          expect(resultado.pages[0].itens.slice(1)).toEqual([outro, direta, lead]);
          expect(resultado.pageParams).toEqual(inbox.pageParams);
          expect(resultado.pages[0].proximoCursor).toBe("cursor");
        }
      });
    });

    it("um GET de conversas iniciado antes da confirmação não restaura a foto anterior", async () => {
      vi.mocked(atualizarFotoDoGrupoChat).mockResolvedValue({ fotoUrl: "/api/v1/chat-interno/conversas/g1/foto?v=2" });
      renderizar();
      let responder: (lista: ChatConversa[]) => void = () => undefined;
      const leituraAntiga = cliente.fetchQuery({
        queryKey: ["chat-interno", "conversas"],
        queryFn: () => new Promise<ChatConversa[]>((resolver) => { responder = resolver; }),
      }).catch(() => undefined);
      escolher(png());
      fireEvent.click(screen.getByRole("button", { name: "Salvar foto" }));
      await waitFor(() => expect(cliente.getQueryData<ChatConversa[]>(["chat-interno", "conversas"])?.[0].fotoUrl).toContain("v=2"));
      await act(async () => {
        responder(CONVERSAS);
        await leituraAntiga;
      });
      expect(cliente.getQueryData<ChatConversa[]>(["chat-interno", "conversas"])?.[0].fotoUrl).toContain("v=2");
    });

    it("cancelar descarta a prévia sem enviar nada", () => {
      renderizar();
      escolher(png());

      fireEvent.click(screen.getByRole("button", { name: "Cancelar" }));

      expect(screen.queryByRole("img", { name: "Prévia da nova foto do grupo" })).not.toBeInTheDocument();
      expect(atualizarFotoDoGrupoChat).not.toHaveBeenCalled();
      expect(revogadasEspia).toHaveBeenCalledWith("blob:previa-1");
      expect(screen.getByRole("button", { name: "Alterar foto" })).toBeEnabled();
    });

    it("impede o duplo envio: um clique, uma chamada, botões travados e texto de andamento", async () => {
      let concluir: (valor: { fotoUrl: string }) => void = () => undefined;
      vi.mocked(atualizarFotoDoGrupoChat).mockReturnValue(new Promise((resolver) => { concluir = resolver; }));
      renderizar();
      escolher(png());
      const salvar = screen.getByRole("button", { name: "Salvar foto" });

      fireEvent.click(salvar);
      fireEvent.click(salvar);
      fireEvent.click(salvar);

      await waitFor(() => expect(screen.getByRole("button", { name: "Enviando…" })).toBeDisabled());
      expect(screen.getByRole("button", { name: "Enviando…" })).toHaveAttribute("aria-busy", "true");
      expect(screen.getByRole("button", { name: "Cancelar" })).toBeDisabled();
      expect(screen.getByLabelText("Escolher imagem para a foto do grupo")).toBeDisabled();
      expect(atualizarFotoDoGrupoChat).toHaveBeenCalledTimes(1);

      await act(async () => concluir({ fotoUrl: "/api/v1/chat-interno/conversas/g1/foto?v=9" }));
      await waitFor(() => expect(screen.queryByRole("button", { name: "Enviando…" })).not.toBeInTheDocument());
    });

    it("arquivo que não é JPEG, PNG ou WebP é recusado antes de qualquer prévia ou envio", () => {
      renderizar();

      escolher(new File(["MZ"], "programa.png", { type: "application/x-msdownload" }));

      expect(screen.getByRole("alert")).toHaveTextContent("Escolha uma imagem JPEG, PNG ou WebP.");
      expect(screen.queryByRole("img", { name: "Prévia da nova foto do grupo" })).not.toBeInTheDocument();
      expect(atualizarFotoDoGrupoChat).not.toHaveBeenCalled();
    });

    it("o erro de tipo some quando o usuário escolhe uma imagem válida", () => {
      renderizar();
      escolher(new File(["x"], "doc.pdf", { type: "application/pdf" }));
      expect(screen.getByRole("alert")).toBeInTheDocument();

      escolher(png());

      expect(screen.queryByRole("alert")).not.toBeInTheDocument();
      expect(screen.getByRole("img", { name: "Prévia da nova foto do grupo" })).toBeInTheDocument();
    });

    it.each([
      [403, "Somente quem criou o grupo pode alterar a foto."],
      [413, "A imagem é grande demais."],
      [422, "Não foi possível usar essa imagem."],
      [503, "O armazenamento de imagens está indisponível."],
    ])("erro %i do backend mostra a mensagem do catálogo e permite tentar de novo", async (status, mensagem) => {
      vi.mocked(atualizarFotoDoGrupoChat).mockRejectedValueOnce(new ErroDeApi(status, { detail: "texto cru do servidor" }, "falha"));
      renderizar();
      escolher(png());

      fireEvent.click(screen.getByRole("button", { name: "Salvar foto" }));

      expect(await screen.findByRole("alert")).toHaveTextContent(mensagem);
      expect(screen.queryByText("texto cru do servidor")).not.toBeInTheDocument();
      expect(screen.getByRole("img", { name: "Prévia da nova foto do grupo" })).toBeInTheDocument();
      expect(screen.getByRole("button", { name: "Salvar foto" })).toBeEnabled();
      expect(cliente.getQueryData<ChatConversa[]>(["chat-interno", "conversas"])?.[0].fotoUrl).toContain("v=1");
    });

    it("falha de rede ou desconhecida mostra o erro genérico e não perde a prévia", async () => {
      vi.mocked(atualizarFotoDoGrupoChat).mockRejectedValueOnce(new TypeError("Failed to fetch"));
      renderizar();
      escolher(png());

      fireEvent.click(screen.getByRole("button", { name: "Salvar foto" }));

      expect(await screen.findByRole("alert")).toHaveTextContent("Não foi possível salvar a foto.");
      expect(screen.getByRole("img", { name: "Prévia da nova foto do grupo" })).toBeInTheDocument();
    });

    it("repetir depois de um erro envia de novo e conclui", async () => {
      vi.mocked(atualizarFotoDoGrupoChat)
        .mockRejectedValueOnce(new ErroDeApi(503, null, "falha"))
        .mockResolvedValueOnce({ fotoUrl: "/api/v1/chat-interno/conversas/g1/foto?v=5" });
      renderizar();
      escolher(png());
      fireEvent.click(screen.getByRole("button", { name: "Salvar foto" }));
      await screen.findByRole("alert");

      fireEvent.click(screen.getByRole("button", { name: "Salvar foto" }));

      await waitFor(() => expect(atualizarFotoDoGrupoChat).toHaveBeenCalledTimes(2));
      await waitFor(() => expect(screen.queryByRole("alert")).not.toBeInTheDocument());
      expect(cliente.getQueryData<ChatConversa[]>(["chat-interno", "conversas"])?.[0].fotoUrl).toContain("v=5");
    });
  });

  describe("remoção", () => {
    it("remove a foto, volta ao avatar padrão e atualiza a lista na hora", async () => {
      vi.mocked(removerFotoDoGrupoChat).mockResolvedValue({ fotoUrl: null });
      renderizar({ fotoUrl: "/api/v1/chat-interno/conversas/g1/foto?v=1" });

      fireEvent.click(screen.getByRole("button", { name: "Remover foto" }));

      await waitFor(() => expect(removerFotoDoGrupoChat).toHaveBeenCalledWith("g1"));
      await waitFor(() => expect(cliente.getQueryData<ChatConversa[]>(["chat-interno", "conversas"])?.[0].fotoUrl).toBeNull());
    });

    it("falha na remoção aparece na tela e o botão volta a funcionar", async () => {
      vi.mocked(removerFotoDoGrupoChat).mockRejectedValueOnce(new ErroDeApi(503, null, "falha"));
      renderizar({ fotoUrl: "/api/v1/chat-interno/conversas/g1/foto?v=1" });

      fireEvent.click(screen.getByRole("button", { name: "Remover foto" }));

      expect(await screen.findByRole("alert")).toHaveTextContent("O armazenamento de imagens está indisponível.");
      expect(screen.getByRole("button", { name: "Remover foto" })).toBeEnabled();
    });

    it("não permite remover duas vezes enquanto a primeira não terminou", async () => {
      let concluir: (valor: { fotoUrl: null }) => void = () => undefined;
      vi.mocked(removerFotoDoGrupoChat).mockReturnValue(new Promise((resolver) => { concluir = resolver; }));
      renderizar({ fotoUrl: "/api/v1/chat-interno/conversas/g1/foto?v=1" });
      const remover = screen.getByRole("button", { name: "Remover foto" });

      fireEvent.click(remover);
      fireEvent.click(remover);

      await waitFor(() => expect(remover).toBeDisabled());
      expect(removerFotoDoGrupoChat).toHaveBeenCalledTimes(1);
      await act(async () => concluir({ fotoUrl: null }));
    });
  });
});
