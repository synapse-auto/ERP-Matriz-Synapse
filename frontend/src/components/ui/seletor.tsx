"use client";

import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { cn } from "@/lib/utils";

export interface OpcaoDoSeletor {
  valor: string;
  rotulo: string;
  desabilitada?: boolean;
}

interface SeletorProps {
  valor: string;
  opcoes: OpcaoDoSeletor[];
  onChange: (valor: string) => void;
  placeholder: string;
  className?: string;
  id?: string;
  ariaLabel?: string;
  obrigatorio?: boolean;
  desabilitado?: boolean;
}

/** Escolha simples baseada no Select do shadcn, sem aparência nativa do navegador. */
export function Seletor({
  valor,
  opcoes,
  onChange,
  placeholder,
  className,
  id,
  ariaLabel,
  obrigatorio,
  desabilitado,
}: SeletorProps) {
  return (
    <Select
      value={valor || null}
      items={[
        { value: null, label: placeholder },
        ...opcoes.map((opcao) => ({ value: opcao.valor, label: opcao.rotulo })),
      ]}
      disabled={desabilitado}
      onValueChange={(novoValor) => onChange(novoValor ?? "")}
    >
      <SelectTrigger
        id={id}
        aria-label={ariaLabel}
        aria-required={obrigatorio}
        className={cn("w-full", className)}
      >
        <SelectValue placeholder={placeholder} />
      </SelectTrigger>
      <SelectContent>
        <SelectItem value={null}>{placeholder}</SelectItem>
        {opcoes.map((opcao) => (
          <SelectItem key={opcao.valor} value={opcao.valor} disabled={opcao.desabilitada}>
            {opcao.rotulo}
          </SelectItem>
        ))}
      </SelectContent>
    </Select>
  );
}
