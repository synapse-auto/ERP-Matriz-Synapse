import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

vi.mock("next/dynamic", () => ({
  default: () =>
    function SeletorMock({ onEscolher }: { onEscolher: (emoji: string) => void }) {
      return (
        <>
          <input aria-label="Buscar emoji" />
          <button type="button" onClick={() => onEscolher("👍🏽")}>
            👍🏽
          </button>
        </>
      );
    },
}));

import type { Textos } from "@/lib/config/schema";
import type { ChatMensagem } from "@/lib/chat-interno/types";

import { CabecalhoChatInterno, ComposerChatInterno, ListaMensagensChatInterno } from "./componentes-chat-interno";
import { TextosProvider } from "@/lib/config/textos-provider";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";

const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });

const mockTextosCompletos = {
  chatInterno: {
    titulo: "Chat interno",
    semMensagens: "Nenhuma mensagem ainda.",
    placeholder: "Escreva uma mensagem...",
    enviar: "Enviar",
    erroEnviar: "Não foi possível enviar a mensagem.",
    respostaCancelar: "Cancelar resposta",
    mensagemRemovida: "Mensagem removida",
    encaminharTitulo: "Encaminhar mensagem",
    encaminharDescricao: "Escolha uma conversa interna autorizada.",
    encaminharDestino: "Conversa de destino",
    encaminharConfirmar: "Encaminhar",
    encaminharCancelar: "Cancelar",
    encaminharErro: "Não foi possível encaminhar a mensagem.",
    tipoGrupo: "Grupo",
    tipoDireta: "Conversa direta",
    midias: { titulo: "Mídias compartilhadas", vazio: "Nenhuma mídia compartilhada.", carregando: "Carregando mídias...", erro: "Não foi possível carregar as mídias.", carregarMais: "Carregar mais", abrir: "Abrir {nome}", baixar: "Baixar {nome}" },
    participantesDoGrupo: "Participantes do grupo",
    retrair: "Retrair dados do grupo",
    reabrir: "Reabrir dados do grupo",
    sistema: {
      grupoCriado: "criou o grupo {nome}",
      participanteAdicionado: "adicionou {alvo}",
      participanteRemovido: "removeu {alvo}",
      participanteSaiu: "{alvo} saiu do grupo",
      nomeAlterado: "renomeou o grupo para {nome}",
      eventoDesconhecido: "atualização do grupo",
    },
  },
  atendimentos: {
    composer: {
      anexo: "A",
      anexoRemover: "A",
      anexoLegendaPlaceholder: "Legenda",
      anexoTipoNaoPermitido: "Tipo nao aceito.",
      anexoSoltar: "Solte os arquivos aqui",
      anexoEnviandoLote: "Enviando {atual} de {total}",
      emoji: "Emoji",
      audioGravando: "A",
      audioDescartar: "A",
      audioParar: "A",
      audioPreview: "A",
      audioEnviar: "A",
      audioSemMicrofone: "A",
      audioPermissaoNegada: "A",
      audioMicrofoneEmUso: "A",
      audioErroCaptura: "A",
      audioExcedeuLimite: "A",
    },
    media: { audio: "Áudio", reproduzir: "Reproduzir áudio", pausar: "Pausar áudio", posicao: "Posição do áudio", baixar: "A", documento: "A", imagem: "A" },
    mensagem: {
      hoje: "Hoje",
      ontem: "Ontem",
      acoes: {
        abrir: "Ações da mensagem", titulo: "Ações", copiar: "Copiar", copiada: "ok", copiarErro: "erro",
        reagir: "Reagir com {emoji}", reacaoQuantidade: "{emoji}, {quantidade}", reacaoMinha: "{emoji}, {quantidade}, sua reação",
        maisEmojis: "Mais emojis", seletorTitulo: "Escolher", seletorFechar: "Fechar", reacaoErro: "erro", responder: "Responder", encaminhar: "Encaminhar", excluir: "Excluir",
        rapidas: ["👍", "❤️", "😂", "😮", "😢", "🙏"],
        seletor: { search: "Buscar", searchNoResults: "Nenhum", pick: "Escolha", addCustom: "C", categories: { activity: "A", custom: "C", flags: "F", foods: "Fo", frequent: "R", nature: "N", objects: "O", people: "P", places: "V", search: "B", symbols: "S" }, skins: { choose: "Tom", 1: "1", 2: "2", 3: "3", 4: "4", 5: "5", 6: "6" } },
      },
      citacao: { resposta: "Resposta de {autor}", encaminhamento: "Encaminhada", cancelar: "Cancelar", origemIndisponivel: "Mensagem removida", imagem: "Imagem", audio: "Áudio", documento: "Documento" },
    },
  },
} as unknown as Textos;

const textos = mockTextosCompletos.chatInterno;

const mensagens: ChatMensagem[] = [
  { id: "m1", conversaId: "c1", remetenteId: "u1", remetenteNome: "Ana", conteudo: "Olá", enviadoEm: "2026-08-27T12:00:00Z" },
  { id: "m2", conversaId: "c1", remetenteId: "u2", remetenteNome: "Bruno", conteudo: "Tudo bem?", enviadoEm: "2026-08-27T12:01:00Z" },
];

describe("componentes de apresentação do chat interno", () => {
  it("não oferece finalização, transferência ou controles de lead no cabeçalho interno", () => {
    render(<CabecalhoChatInterno textos={textos} conversa={{ id: "c1", tipo: "DIRETA", participantes: "Bruno Almeida", ultimaMensagem: "Oi", ultimaMensagemEm: "2026-08-27T12:00:00Z", naoLidas: 0 }} />);

    expect(screen.getByText("Bruno Almeida")).toBeInTheDocument();
    expect(screen.getByText("Conversa direta")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Finalizar" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Transferir" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Mais ações" })).not.toBeInTheDocument();
    expect(screen.queryByText("Finalizar Todos")).not.toBeInTheDocument();
  });

  it("exibe o controle para reabrir o painel de grupo somente quando ele está fechado", () => {
    const onGerenciarGrupo = vi.fn();
    const conversa = { id: "g1", tipo: "GRUPO" as const, participantes: "Operação", ultimaMensagem: "Oi", ultimaMensagemEm: "2026-08-27T12:00:00Z", naoLidas: 0 };

    const { rerender } = render(
      <CabecalhoChatInterno conversa={conversa} textos={textos} onGerenciarGrupo={onGerenciarGrupo} />,
    );

    const reabrir = screen.getByRole("button", { name: "Reabrir dados do grupo" });
    expect(reabrir).toHaveAttribute("aria-expanded", "false");
    expect(reabrir).toHaveAttribute("aria-controls", "painel-grupo");
    fireEvent.click(reabrir);
    expect(onGerenciarGrupo).toHaveBeenCalledOnce();

    rerender(
      <CabecalhoChatInterno conversa={conversa} textos={textos} painelGrupoAberto onGerenciarGrupo={onGerenciarGrupo} />,
    );
    expect(screen.queryByRole("button", { name: "Reabrir dados do grupo" })).not.toBeInTheDocument();
  });

  it("renderiza mensagem de sistema do grupo no centro", () => {
    const sistema: ChatMensagem[] = [{
      id: "s1",
      conversaId: "c1",
      remetenteId: "u1",
      remetenteNome: "Ana",
      tipo: "SISTEMA",
      conteudo: JSON.stringify({ evento: "GRUPO_CRIADO", nome: "Ops" }),
      enviadoEm: "2026-09-01T12:00:00Z",
    }];
    render(
      <TextosProvider textos={mockTextosCompletos}>
        <ListaMensagensChatInterno mensagens={sistema} usuarioAtual="u1" textos={textos} onDefinirReacao={vi.fn()} onRemoverReacao={vi.fn()} />
      </TextosProvider>,
    );
    expect(screen.getByText("Ana criou o grupo Ops")).toBeInTheDocument();
    expect(screen.getByText("Ana criou o grupo Ops").closest("[data-slot='mensagem-sistema-chat']")).toBeTruthy();
  });

  it("posiciona a mensagem própria pela id real e identifica o remetente recebido", () => {
    const { container } = render(<TextosProvider textos={mockTextosCompletos}><ListaMensagensChatInterno mensagens={mensagens} usuarioAtual="u1" textos={textos} onDefinirReacao={vi.fn()} onRemoverReacao={vi.fn()} /></TextosProvider>);
    const linhas = container.querySelectorAll('[data-slot="interacao-mensagem"]');
    expect(linhas[0].parentElement).toHaveClass("justify-end");
    expect(linhas[1].parentElement).toHaveClass("justify-start");
    expect(screen.getByText("Bruno")).toBeInTheDocument();
    expect(screen.getByText("Tudo bem?")).toBeInTheDocument();
    expect(screen.getByText("Olá").closest("div")).toHaveClass(
      "w-fit",
      "max-w-full",
      "rounded-2xl",
      "rounded-tr-md",
    );
    expect(screen.getByText("Tudo bem?").closest("div")).toHaveClass(
      "w-fit",
      "max-w-full",
      "rounded-2xl",
      "rounded-tl-md",
      "border",
    );
    expect(container.querySelector('[data-slot="historico-chat-interno"]')).toHaveClass(
      "min-h-0",
      "flex-1",
      "overscroll-contain",
    );
  });

  it("oferece responder, encaminhar e excluir quando a tela fornece os callbacks", async () => {
    const responder = vi.fn();
    const encaminhar = vi.fn();
    const excluir = vi.fn().mockResolvedValue(undefined);
    render(
      <TextosProvider textos={mockTextosCompletos}>
        <ListaMensagensChatInterno
          mensagens={mensagens}
          usuarioAtual="u1"
          textos={textos}
          onDefinirReacao={vi.fn()}
          onRemoverReacao={vi.fn()}
          onResponder={responder}
          onEncaminhar={encaminhar}
          onExcluir={excluir}
        />
      </TextosProvider>,
    );
    fireEvent.click(screen.getAllByRole("button", { name: "Ações da mensagem" })[0]);
    fireEvent.click(screen.getByRole("button", { name: "Responder" }));
    expect(responder).toHaveBeenCalledWith(mensagens[0]);
    fireEvent.click(screen.getAllByRole("button", { name: "Ações da mensagem" })[0]);
    fireEvent.click(screen.getByRole("button", { name: "Encaminhar" }));
    expect(encaminhar).toHaveBeenCalledWith(mensagens[0]);
    fireEvent.click(screen.getAllByRole("button", { name: "Ações da mensagem" })[0]);
    fireEvent.click(screen.getByRole("button", { name: "Excluir" }));
    await waitFor(() => expect(excluir).toHaveBeenCalledWith(mensagens[0]));
  });

  it("oferece editar somente para texto próprio e marca a mensagem editada", async () => {
    const editar = vi.fn();
    const textosEdicao = { ...textos, editar: "Editar", mensagemEditada: "Editada" };
    const { rerender } = render(
      <TextosProvider textos={mockTextosCompletos}>
        <ListaMensagensChatInterno
          mensagens={mensagens}
          usuarioAtual="u1"
          textos={textosEdicao}
          onDefinirReacao={vi.fn()}
          onRemoverReacao={vi.fn()}
          onEditar={editar}
        />
      </TextosProvider>,
    );
    fireEvent.click(screen.getAllByRole("button", { name: "Ações da mensagem" })[0]);
    fireEvent.click(screen.getByRole("button", { name: "Editar" }));
    expect(editar).toHaveBeenCalledWith(mensagens[0]);
    expect(screen.getByText("Tudo bem?")).toBeInTheDocument();

    const editada = { ...mensagens[0], editadoEm: "2026-08-27T12:03:00Z" };
    rerender(<TextosProvider textos={mockTextosCompletos}><ListaMensagensChatInterno mensagens={[editada]} usuarioAtual="u1" textos={textosEdicao} onDefinirReacao={vi.fn()} onRemoverReacao={vi.fn()} /></TextosProvider>);
    expect(screen.getByText(/Editada/)).toBeInTheDocument();
    rerender(<TextosProvider textos={mockTextosCompletos}><ListaMensagensChatInterno mensagens={[{ ...editada, tipo: "AUDIO", conteudo: null }]} usuarioAtual="u1" textos={textosEdicao} onDefinirReacao={vi.fn()} onRemoverReacao={vi.fn()} onEditar={editar} /></TextosProvider>);
    expect(screen.queryByRole("button", { name: "Editar" })).not.toBeInTheDocument();
  });

  it("renderiza tombstone sem conteúdo nem mídia e mantém a referência segura", () => {
    const removida: ChatMensagem = {
      ...mensagens[1],
      id: "m-removida",
      removida: true,
      conteudo: null,
      midiaUrl: null,
      citacao: {
        origemId: "m-origem",
        tipoReferencia: "RESPOSTA",
        autor: "Ana",
        tipoConteudo: "TEXTO",
        previa: "",
        origemRemovida: true,
      },
    };
    const { container } = render(<TextosProvider textos={mockTextosCompletos}><ListaMensagensChatInterno mensagens={[removida]} usuarioAtual="u1" textos={textos} onDefinirReacao={vi.fn()} onRemoverReacao={vi.fn()} /></TextosProvider>);
    expect(container.querySelector('[data-slot="mensagem-removida-chat"]')).toHaveTextContent("Mensagem removida");
    expect(screen.queryByText("Tudo bem?")).not.toBeInTheDocument();
    expect(screen.getByText("Resposta de Ana")).toBeInTheDocument();
    expect(screen.getAllByText("Mensagem removida").length).toBeGreaterThanOrEqual(1);
  });

  it("renderiza áudio enviado com o player da bolha, sem o controle nativo", () => {
    const comAudio: ChatMensagem[] = [{
      id: "m-audio",
      conversaId: "c1",
      remetenteId: "u1",
      remetenteNome: "Ana",
      tipo: "AUDIO",
      conteudo: null,
      midiaUrl: "https://example.test/voz.m4a",
      enviadoEm: "2026-08-27T12:02:00Z",
    }];
    render(
      <TextosProvider textos={mockTextosCompletos}>
        <ListaMensagensChatInterno mensagens={comAudio} usuarioAtual="u1" textos={textos} onDefinirReacao={vi.fn()} onRemoverReacao={vi.fn()} />
      </TextosProvider>,
    );
    expect(document.querySelector('[data-slot="player-audio"]')).toBeInTheDocument();
    expect(document.querySelector("audio[controls]")).toBeNull();
  });

  it("insere separadores quando a mensagem muda de dia, incluindo mensagem de sistema", () => {
    const historico: ChatMensagem[] = [
      { ...mensagens[0], id: "d1", enviadoEm: "2026-09-01T12:00:00Z" },
      { id: "s1", conversaId: "c1", remetenteId: "u2", remetenteNome: "Bruno", tipo: "SISTEMA", conteudo: "atualização", enviadoEm: "2026-09-01T13:00:00Z" },
      { ...mensagens[1], id: "d2", enviadoEm: "2026-09-02T12:00:00Z" },
    ];
    render(<TextosProvider textos={mockTextosCompletos}><ListaMensagensChatInterno mensagens={historico} usuarioAtual="u1" textos={textos} onDefinirReacao={vi.fn()} onRemoverReacao={vi.fn()} /></TextosProvider>);
    expect(document.querySelectorAll('[data-slot="separador-data-chat-interno"]')).toHaveLength(2);
    expect(screen.getByText("atualização")).toBeInTheDocument();
  });

  it("rola até o fim somente quando chega uma nova última mensagem", () => {
    const { rerender } = render(<TextosProvider textos={mockTextosCompletos}><ListaMensagensChatInterno mensagens={mensagens} usuarioAtual="u1" textos={textos} onDefinirReacao={vi.fn()} onRemoverReacao={vi.fn()} /></TextosProvider>);
    const historico = document.querySelector('[data-slot="historico-chat-interno"]') as HTMLDivElement;
    Object.defineProperty(historico, "scrollHeight", { configurable: true, value: 640 });
    historico.scrollTop = 0;
    const nova = { ...mensagens[1], id: "m3", conteudo: "nova mensagem" };
    rerender(<TextosProvider textos={mockTextosCompletos}><ListaMensagensChatInterno mensagens={[...mensagens, nova]} usuarioAtual="u1" textos={textos} onDefinirReacao={vi.fn()} onRemoverReacao={vi.fn()} /></TextosProvider>);
    expect(historico.scrollTop).toBe(640);
  });

  it("envia por Enter, preserva Shift+Enter e mantém o texto quando falha", async () => {
    const enviar = vi.fn().mockRejectedValue(new Error("falha"));
    render(<QueryClientProvider client={client}><TextosProvider textos={mockTextosCompletos}><ComposerChatInterno textos={textos} onEnviar={enviar} erro /></TextosProvider></QueryClientProvider>);
    const campo = screen.getByPlaceholderText(textos.placeholder);
    fireEvent.change(campo, { target: { value: "mensagem" } });
    fireEvent.keyDown(campo, { key: "Enter", shiftKey: true });
    expect(enviar).not.toHaveBeenCalled();
    fireEvent.keyDown(campo, { key: "Enter", shiftKey: false });
    await waitFor(() => expect(enviar).toHaveBeenCalledWith("mensagem"));
    expect(campo).toHaveValue("mensagem");
    expect(screen.getByRole("alert")).toHaveTextContent(textos.erroEnviar);
  });

  it("mantém o foco e o cursor no composer após enviar texto com sucesso", async () => {
    const enviar = vi.fn().mockResolvedValue(undefined);
    render(<QueryClientProvider client={client}><TextosProvider textos={mockTextosCompletos}><ComposerChatInterno textos={textos} onEnviar={enviar} /></TextosProvider></QueryClientProvider>);
    const campo = screen.getByPlaceholderText(textos.placeholder);
    fireEvent.change(campo, { target: { value: "próxima mensagem" } });
    campo.focus();
    fireEvent.keyDown(campo, { key: "Enter", shiftKey: false });

    await waitFor(() => expect(enviar).toHaveBeenCalledWith("próxima mensagem"));
    await waitFor(() => expect(campo).toHaveFocus());
    expect(campo).toHaveValue("");
  });

  it("carrega edição no composer, salva com Enter e cancela com Escape", async () => {
    const salvar = vi.fn().mockResolvedValue(undefined);
    const cancelar = vi.fn();
    const textosEdicao = { ...textos, editar: "Editar", salvarEdicao: "Salvar edição", cancelarEdicao: "Cancelar edição" };
    render(<QueryClientProvider client={client}><TextosProvider textos={mockTextosCompletos}><ComposerChatInterno textos={textosEdicao} onEnviar={vi.fn()} edicao={mensagens[0]} onSalvarEdicao={salvar} onCancelarEdicao={cancelar} /></TextosProvider></QueryClientProvider>);
    const campo = await screen.findByDisplayValue("Olá");
    fireEvent.change(campo, { target: { value: "Olá editada" } });
    fireEvent.keyDown(campo, { key: "Enter" });
    await waitFor(() => expect(salvar).toHaveBeenCalledWith("Olá editada"));
    expect(cancelar).toHaveBeenCalled();
    fireEvent.keyDown(campo, { key: "Escape" });
    expect(cancelar).toHaveBeenCalledTimes(2);
  });

  it("abre o catálogo de emoji e insere no texto sem enviar", async () => {
    const enviar = vi.fn();
    render(
      <QueryClientProvider client={client}>
        <TextosProvider textos={mockTextosCompletos}>
          <ComposerChatInterno textos={textos} onEnviar={enviar} />
        </TextosProvider>
      </QueryClientProvider>,
    );
    fireEvent.click(screen.getByRole("button", { name: "Emoji" }));
    fireEvent.click(await screen.findByRole("button", { name: "👍🏽" }));
    expect(screen.getByPlaceholderText(textos.placeholder)).toHaveValue("👍🏽");
    expect(enviar).not.toHaveBeenCalled();
  });

  it("enfileira varios arquivos e envia midia em sequencia", async () => {
    const enviar = vi.fn();
    const enviarMidia = vi.fn().mockResolvedValue(undefined);
    render(
      <QueryClientProvider client={client}>
        <TextosProvider textos={mockTextosCompletos}>
          <ComposerChatInterno textos={textos} onEnviar={enviar} onEnviarMidia={enviarMidia} />
        </TextosProvider>
      </QueryClientProvider>,
    );
    const input = document.querySelector('input[type="file"]') as HTMLInputElement;
    expect(input.multiple).toBe(true);
    fireEvent.change(input, {
      target: {
        files: [
          new File(["a"], "a.png", { type: "image/png" }),
          new File(["b"], "b.pdf", { type: "application/pdf" }),
        ],
      },
    });
    fireEvent.click(screen.getByLabelText(textos.enviar));
    await waitFor(() => expect(enviarMidia).toHaveBeenCalledTimes(2));
    expect(enviarMidia.mock.calls[0]?.[0]).toEqual(expect.objectContaining({ name: "a.png" }));
    expect(enviarMidia.mock.calls[1]?.[0]).toEqual(expect.objectContaining({ name: "b.pdf" }));
    expect(enviar).not.toHaveBeenCalled();
  });

  it("cola imagem no mesmo fluxo de anexos e preserva colagem de texto", () => {
    const enviar = vi.fn();
    const enviarMidia = vi.fn();
    render(
      <QueryClientProvider client={client}>
        <TextosProvider textos={mockTextosCompletos}>
          <ComposerChatInterno textos={textos} onEnviar={enviar} onEnviarMidia={enviarMidia} />
        </TextosProvider>
      </QueryClientProvider>,
    );
    const campo = screen.getByPlaceholderText(textos.placeholder);
    const imagem = new File(["bytes"], "print.png", { type: "image/png" });
    fireEvent.paste(campo, {
      clipboardData: { items: [{ kind: "file", getAsFile: () => imagem }] },
    });
    expect(screen.getByText("print.png")).toBeInTheDocument();
    expect(enviarMidia).not.toHaveBeenCalled();
    fireEvent.paste(campo, {
      clipboardData: { items: [{ kind: "string", getAsFile: () => null }] },
    });
    expect(enviar).not.toHaveBeenCalled();
  });

  it("mostra a prévia da resposta e permite cancelar antes de enviar", () => {
    const cancelar = vi.fn();
    render(
      <QueryClientProvider client={client}>
        <TextosProvider textos={mockTextosCompletos}>
          <ComposerChatInterno textos={textos} onEnviar={vi.fn()} resposta={mensagens[1]} onCancelarResposta={cancelar} />
        </TextosProvider>
      </QueryClientProvider>,
    );
    expect(screen.getByText("Resposta de Bruno")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Cancelar resposta" }));
    expect(cancelar).toHaveBeenCalledOnce();
  });
});
