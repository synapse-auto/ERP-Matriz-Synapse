"use client";

import { useRef, useState, type DragEvent, type ReactNode } from "react";

import { arquivosDeDataTransfer } from "@/lib/atendimento/arquivos-do-composer";
import { cn } from "@/lib/utils";

type Props = {
  accept: string;
  disabled?: boolean;
  rotulo: string;
  onArquivos: (resultado: { aceitos: File[]; rejeitados: File[] }) => void;
  children: ReactNode;
  className?: string;
};

function contemArquivos(evento: DragEvent): boolean {
  return Array.from(evento.dataTransfer?.types ?? []).includes("Files");
}

/**
 * Zona de arrastar-e-soltar do explorador. Nao envia sozinha: so entrega arquivos
 * filtrados ao composer, que segue o POST de midia um a um.
 */
export function ZonaSoltarArquivos({
  accept,
  disabled = false,
  rotulo,
  onArquivos,
  children,
  className,
}: Props) {
  const [arrastando, setArrastando] = useState(false);
  const contador = useRef(0);

  function aoEntrar(evento: DragEvent<HTMLDivElement>) {
    if (disabled || !contemArquivos(evento)) return;
    evento.preventDefault();
    contador.current += 1;
    setArrastando(true);
  }

  function aoPassar(evento: DragEvent<HTMLDivElement>) {
    if (disabled || !contemArquivos(evento)) return;
    evento.preventDefault();
    evento.dataTransfer.dropEffect = "copy";
  }

  function aoSair() {
    if (disabled) return;
    contador.current = Math.max(0, contador.current - 1);
    if (contador.current === 0) setArrastando(false);
  }

  function aoSoltar(evento: DragEvent<HTMLDivElement>) {
    if (disabled) return;
    evento.preventDefault();
    contador.current = 0;
    setArrastando(false);
    onArquivos(arquivosDeDataTransfer(evento.dataTransfer, accept));
  }

  return (
    <div
      className={cn("relative flex min-h-0 min-w-0 flex-1 flex-col", className)}
      onDragEnter={aoEntrar}
      onDragOver={aoPassar}
      onDragLeave={aoSair}
      onDrop={aoSoltar}
    >
      {children}
      {arrastando && (
        <div
          className="absolute inset-0 z-30 flex items-center justify-center rounded-lg border-2 border-dashed border-primary bg-background/80 text-sm font-medium text-foreground"
          role="status"
        >
          {rotulo}
        </div>
      )}
    </div>
  );
}
