"use client";

import { Check, ChevronDown } from "lucide-react";

import { Button } from "@/components/ui/button";
import type { OpcaoDoSeletor } from "@/components/ui/seletor";
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from "@/components/ui/popover";
import { cn } from "@/lib/utils";

interface SeletorMultiploProps {
  valores: string[];
  opcoes: OpcaoDoSeletor[];
  onChange: (valores: string[]) => void;
  placeholder: string;
  className?: string;
  ariaLabel?: string;
}

/** Seleção múltipla temática para os casos que o Select simples não representa. */
export function SeletorMultiplo({
  valores,
  opcoes,
  onChange,
  placeholder,
  className,
  ariaLabel,
}: SeletorMultiploProps) {
  const selecionadas = opcoes.filter((opcao) => valores.includes(opcao.valor));
  const rotulo = selecionadas.length
    ? selecionadas.map((opcao) => opcao.rotulo).join(", ")
    : placeholder;

  function alternar(valor: string) {
    onChange(
      valores.includes(valor)
        ? valores.filter((atual) => atual !== valor)
        : [...valores, valor],
    );
  }

  return (
    <Popover>
      <PopoverTrigger
        render={
          <Button
            type="button"
            variant="outline"
            role="combobox"
            aria-label={ariaLabel}
            className={cn(
              "h-10 justify-between rounded-lg border-input bg-card px-3 text-xs font-medium",
              className,
            )}
          />
        }
      >
        <span className="truncate">{rotulo}</span>
        <ChevronDown className="size-4 shrink-0 text-muted-foreground" />
      </PopoverTrigger>
      <PopoverContent
        align="start"
        sideOffset={6}
        className="max-h-72 min-w-44 w-(--anchor-width) overflow-y-auto rounded-lg border border-border bg-popover p-1.5 shadow-lg"
      >
        <div role="listbox" aria-multiselectable="true">
          {opcoes.map((opcao) => {
            const marcada = valores.includes(opcao.valor);
            return (
              <button
                key={opcao.valor}
                type="button"
                role="option"
                aria-selected={marcada}
                disabled={opcao.desabilitada}
                className={cn(
                  "flex min-h-9 w-full items-center gap-2 rounded-md px-2 py-1.5 text-left text-xs outline-none transition-colors hover:bg-accent focus-visible:bg-accent disabled:opacity-50",
                  marcada && "bg-accent/70 text-accent-foreground",
                )}
                onClick={() => alternar(opcao.valor)}
              >
                <span className="flex size-4 shrink-0 items-center justify-center rounded-sm border border-input bg-card">
                  {marcada && <Check className="size-3" />}
                </span>
                <span className="truncate">{opcao.rotulo}</span>
              </button>
            );
          })}
        </div>
      </PopoverContent>
    </Popover>
  );
}
