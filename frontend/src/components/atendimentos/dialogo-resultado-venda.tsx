"use client";

import { Button } from "@/components/ui/button";
import type { ReactElement } from "react";
import { Popover, PopoverTrigger, PopoverContent, PopoverTitle, PopoverDescription } from "@/components/ui/popover";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import type { ResultadoVenda } from "@/lib/atendimento/types";
import { useTextos } from "@/lib/config/textos-provider";

type Props = {
  aberto: boolean;
  modo: "finalizacao" | "manual";
  processando: boolean;
  erro?: string | null;
  sucesso?: string | null;
  onSelecionar: (resultado: ResultadoVenda) => void;
  onFechar: () => void;
  gatilho?: ReactElement;
};

/** Uma escolha explícita de resultado; o servidor continua sendo a autoridade do registro. */
export function DialogoResultadoVenda({
  aberto,
  modo,
  processando,
  erro,
  sucesso,
  onSelecionar,
  onFechar,
  gatilho,
}: Props) {
  const textos = useTextos().atendimentos.finalizar;
  const titulo = modo === "finalizacao" ? textos.vendeuTitulo : textos.registrarVendaTitulo;
  const descricao = modo === "finalizacao" ? textos.vendeuDescricao : textos.registrarVendaDescricao;

  if (modo === "finalizacao" && gatilho) {
    return (
      <Popover open={aberto} onOpenChange={(open) => !open && !processando && onFechar()}>
        <PopoverTrigger render={gatilho} />
        <PopoverContent align="end" className="max-w-[calc(100vw-2rem)]">
          <PopoverTitle>{titulo}</PopoverTitle>
          <PopoverDescription>{descricao}</PopoverDescription>
          {erro && <p role="alert" className="text-sm text-destructive">{erro}</p>}
          <div className="flex flex-wrap gap-2">
            <Button type="button" onClick={() => onSelecionar("VENDEU")} disabled={processando}>
              {processando ? textos.registrando : textos.vendeu}
            </Button>
            <Button type="button" variant="outline" onClick={() => onSelecionar("NAO_VENDEU")} disabled={processando}>
              {processando ? textos.registrando : textos.naoVendeu}
            </Button>
            <Button type="button" variant="ghost" onClick={onFechar} disabled={processando}>
              {textos.cancelarResultado}
            </Button>
          </div>
        </PopoverContent>
      </Popover>
    );
  }

  return (
    <Dialog open={aberto} onOpenChange={(open) => !open && !processando && onFechar()}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{titulo}</DialogTitle>
          <DialogDescription>{descricao}</DialogDescription>
        </DialogHeader>

        {erro && <p role="alert" className="text-sm text-destructive">{erro}</p>}
        {sucesso && <p role="status" aria-live="polite" className="text-sm text-cor-sucesso">{sucesso}</p>}

        {!sucesso && (
          <DialogFooter>
            <Button type="button" variant="outline" onClick={onFechar} disabled={processando}>
              {modo === "finalizacao" ? textos.cancelarResultado : textos.cancelar}
            </Button>
            {modo === "finalizacao" && (
              <Button type="button" onClick={() => onSelecionar("NAO_VENDEU")} disabled={processando}>
                {processando ? textos.registrando : textos.naoVendeu}
              </Button>
            )}
            <Button type="button" onClick={() => onSelecionar("VENDEU")} disabled={processando}>
              {processando ? textos.registrando : modo === "finalizacao" ? textos.vendeu : textos.registrarVendaConfirmar}
            </Button>
          </DialogFooter>
        )}

        {sucesso && (
          <DialogFooter>
            <Button type="button" onClick={onFechar}>{textos.cancelar}</Button>
          </DialogFooter>
        )}
      </DialogContent>
    </Dialog>
  );
}
