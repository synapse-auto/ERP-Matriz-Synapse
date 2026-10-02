"use client";

import { useState } from "react";
import { Download } from "lucide-react";

import { Button } from "@/components/ui/button";
import { baixarDestinatariosCsv } from "@/lib/campanhas/api";
import { useTextos } from "@/lib/config/textos-provider";

function salvarArquivo(blob: Blob, nome: string) {
  const endereco = URL.createObjectURL(blob);
  const ancora = document.createElement("a");
  ancora.href = endereco;
  ancora.download = nome;
  ancora.click();
  URL.revokeObjectURL(endereco);
}

/** CSV autenticado: baixa como blob (um link simples não leva o Bearer) e salva com o nome do backend. */
export function BotaoExportarCsv({ campanhaId }: { campanhaId: string }) {
  const textos = useTextos().campanhas.detalhe.destinatarios;
  const [exportando, setExportando] = useState(false);
  const [falhou, setFalhou] = useState(false);

  async function exportar() {
    setFalhou(false);
    setExportando(true);
    try {
      const { blob, nome } = await baixarDestinatariosCsv(campanhaId);
      salvarArquivo(blob, nome ?? `campanha-${campanhaId}.csv`);
    } catch {
      setFalhou(true);
    } finally {
      setExportando(false);
    }
  }

  return (
    <div className="flex flex-col items-end gap-1">
      <Button type="button" variant="outline" disabled={exportando} onClick={() => void exportar()}>
        <Download aria-hidden />
        {exportando ? textos.exportando : textos.exportar}
      </Button>
      {falhou && (
        <p role="alert" className="text-xs text-destructive">
          {textos.erroExportar}
        </p>
      )}
    </div>
  );
}
