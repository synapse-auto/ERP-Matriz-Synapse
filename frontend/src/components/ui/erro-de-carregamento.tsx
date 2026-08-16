"use client";

import { RefreshCw } from "lucide-react";

import { useTextos } from "@/lib/config/textos-provider";
import { cn } from "@/lib/utils";

import { Button } from "./button";

interface Props {
  mensagem?: string;
  onTentarNovamente: () => void | Promise<unknown>;
  className?: string;
}

/** Falha local e recuperavel: preserva a estrutura da tela e oferece nova tentativa no lugar do dado. */
export function ErroDeCarregamento({ mensagem, onTentarNovamente, className }: Props) {
  const textos = useTextos().estados;

  return (
    <div
      role="alert"
      className={cn(
        "flex flex-wrap items-center justify-between gap-3 rounded-lg border border-destructive/20 bg-destructive/5 p-3 text-sm text-destructive",
        className,
      )}
    >
      <p>{mensagem ?? textos.erroGenerico}</p>
      <Button
        type="button"
        variant="outline"
        size="sm"
        onClick={() => void onTentarNovamente()}
      >
        <RefreshCw className="size-3.5" />
        {textos.tentarNovamente}
      </Button>
    </div>
  );
}
