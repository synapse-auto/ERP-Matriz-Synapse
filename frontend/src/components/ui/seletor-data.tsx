"use client";

import { CalendarDays } from "lucide-react";
import { ptBR } from "date-fns/locale";

import { Button } from "@/components/ui/button";
import { Calendar } from "@/components/ui/calendar";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import { cn } from "@/lib/utils";

interface SeletorDataProps {
  valor: string;
  onChange: (valor: string) => void;
  placeholder: string;
  className?: string;
  id?: string;
  obrigatorio?: boolean;
}

function paraDataLocal(valor: string): Date | undefined {
  const partes = valor.match(/^(\d{4})-(\d{2})-(\d{2})$/);
  if (!partes) return undefined;
  return new Date(Number(partes[1]), Number(partes[2]) - 1, Number(partes[3]));
}

function paraIsoLocal(data: Date): string {
  const ano = data.getFullYear();
  const mes = String(data.getMonth() + 1).padStart(2, "0");
  const dia = String(data.getDate()).padStart(2, "0");
  return `${ano}-${mes}-${dia}`;
}

/** Data em Popover + Calendar do shadcn, sempre localizada para pt-BR e semana no domingo. */
export function SeletorData({
  valor,
  onChange,
  placeholder,
  className,
  id,
  obrigatorio,
}: SeletorDataProps) {
  const selecionada = paraDataLocal(valor);

  return (
    <Popover>
      <PopoverTrigger
        render={
          <Button
            id={id}
            type="button"
            variant="outline"
            aria-required={obrigatorio}
            className={cn(
              "w-full justify-between font-normal",
              !selecionada && "text-muted-foreground",
              className,
            )}
          />
        }
      >
        <span>
          {selecionada
            ? new Intl.DateTimeFormat("pt-BR", { dateStyle: "short" }).format(selecionada)
            : placeholder}
        </span>
        <CalendarDays className="size-(--tamanho-icone-interface)" />
      </PopoverTrigger>
      <PopoverContent align="start" className="w-auto p-0">
        <Calendar
          mode="single"
          locale={ptBR}
          weekStartsOn={0}
          selected={selecionada}
          defaultMonth={selecionada}
          onSelect={(data) => onChange(data ? paraIsoLocal(data) : "")}
        />
      </PopoverContent>
    </Popover>
  );
}
