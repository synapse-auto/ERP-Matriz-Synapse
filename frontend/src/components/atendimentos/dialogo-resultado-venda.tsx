"use client";

import { Button } from "@/components/ui/button";
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
}: Props) {
  const textos = useTextos().atendimentos.finalizar;
  const titulo = modo === "finalizacao" ? textos.vendeuTitulo : textos.registrarVendaTitulo;
  const descricao = modo === "finalizacao" ? textos.vendeuDescricao : textos.registrarVendaDescricao;

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
