import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

import { TIPOS_DE_ANEXO_ACEITOS } from "@/lib/atendimento/arquivos-do-composer";

import { ZonaSoltarArquivos } from "./zona-soltar-arquivos";

function dataTransferCom(files: File[]): DataTransfer {
  return { files, types: ["Files"] } as unknown as DataTransfer;
}

describe("ZonaSoltarArquivos", () => {
  it("entrega os arquivos soltos do explorador e mostra o rotulo enquanto arrasta", () => {
    const onArquivos = vi.fn();
    render(
      <ZonaSoltarArquivos
        accept={TIPOS_DE_ANEXO_ACEITOS}
        rotulo="Solte os arquivos aqui"
        onArquivos={onArquivos}
      >
        <p>chat</p>
      </ZonaSoltarArquivos>,
    );

    const zona = screen.getByText("chat").parentElement as HTMLElement;
    fireEvent.dragEnter(zona, { dataTransfer: { types: ["Files"] } });
    expect(screen.getByRole("status")).toHaveTextContent("Solte os arquivos aqui");

    fireEvent.drop(zona, {
      dataTransfer: dataTransferCom([
        new File(["a"], "a.png", { type: "image/png" }),
        new File(["b"], "b.exe", { type: "application/x-msdownload" }),
      ]),
    });

    expect(onArquivos).toHaveBeenCalledWith({
      aceitos: [expect.objectContaining({ name: "a.png" })],
      rejeitados: [expect.objectContaining({ name: "b.exe" })],
    });
    expect(screen.queryByRole("status")).not.toBeInTheDocument();
  });

  it("nao aceita soltar quando a zona esta desabilitada", () => {
    const onArquivos = vi.fn();
    render(
      <ZonaSoltarArquivos
        accept={TIPOS_DE_ANEXO_ACEITOS}
        disabled
        rotulo="Solte os arquivos aqui"
        onArquivos={onArquivos}
      >
        <p>chat</p>
      </ZonaSoltarArquivos>,
    );

    const zona = screen.getByText("chat").parentElement as HTMLElement;
    fireEvent.dragEnter(zona, { dataTransfer: { types: ["Files"] } });
    expect(screen.queryByRole("status")).not.toBeInTheDocument();
    fireEvent.drop(zona, {
      dataTransfer: dataTransferCom([
        new File(["a"], "a.png", { type: "image/png" }),
      ]),
    });
    expect(onArquivos).not.toHaveBeenCalled();
  });
});
