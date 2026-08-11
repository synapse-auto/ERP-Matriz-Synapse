"use client";

import { Seletor } from "@/components/ui/seletor";
import { SeletorData } from "@/components/ui/seletor-data";
import { cn } from "@/lib/utils";

interface SeletorDataHoraProps {
  valor: string;
  onChange: (valor: string) => void;
  placeholderData: string;
  rotuloHora: string;
  rotuloMinuto: string;
  className?: string;
  id?: string;
  obrigatorio?: boolean;
}

const HORAS = Array.from({ length: 24 }, (_, hora) => String(hora).padStart(2, "0"));
const MINUTOS = Array.from({ length: 60 }, (_, minuto) => String(minuto).padStart(2, "0"));

function decompor(valor: string): [string, string, string] {
  const partes = valor.match(/^(\d{4}-\d{2}-\d{2})(?:T(\d{2})?(?::(\d{2})?)?)?/);
  return partes ? [partes[1], partes[2] ?? "", partes[3] ?? ""] : ["", "", ""];
}

function compor(data: string, hora: string, minuto: string): string {
  if (!data) return "";
  if (!hora) return `${data}T`;
  return `${data}T${hora}:${minuto}`;
}

export function dataHoraCompleta(valor: string): boolean {
  return /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/.test(valor);
}

/** Data e hora locais sem widget nativo: Calendar shadcn + dois Selects shadcn. */
export function SeletorDataHora({
  valor,
  onChange,
  placeholderData,
  rotuloHora,
  rotuloMinuto,
  className,
  id,
  obrigatorio,
}: SeletorDataHoraProps) {
  const [data, hora, minuto] = decompor(valor);

  return (
    <div className={cn("grid grid-cols-[minmax(10rem,1fr)_5.5rem_5.5rem] gap-2", className)}>
      <SeletorData
        id={id}
        valor={data}
        placeholder={placeholderData}
        obrigatorio={obrigatorio}
        onChange={(novaData) => onChange(compor(novaData, hora, minuto))}
      />
      <Seletor
        valor={hora}
        ariaLabel={rotuloHora}
        placeholder={rotuloHora}
        opcoes={HORAS.map((item) => ({ valor: item, rotulo: item }))}
        desabilitado={!data}
        onChange={(novaHora) => onChange(compor(data, novaHora, minuto))}
      />
      <Seletor
        valor={minuto}
        ariaLabel={rotuloMinuto}
        placeholder={rotuloMinuto}
        opcoes={MINUTOS.map((item) => ({ valor: item, rotulo: item }))}
        desabilitado={!data || !hora}
        onChange={(novoMinuto) => onChange(compor(data, hora, novoMinuto))}
      />
    </div>
  );
}
