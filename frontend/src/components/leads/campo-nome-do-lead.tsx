"use client";

import { useId, useState } from "react";

import { Input } from "@/components/ui/input";
import { useTextos } from "@/lib/config/textos-provider";
import { useSalvarFicha } from "@/lib/lead/use-painel-lead";
import { cn } from "@/lib/utils";

const TAMANHO_MAXIMO_NOME = 150;

type Props = {
  leadId: string;
  valorAtual: string;
  className?: string;
};

/**
 * Editor inline do nome do cliente. Vazio no blur nao chama a API — o schema e NOT NULL e a tela
 * inteira (card, cabecalho, busca) depende do campo. {@code key={leadId}} no pai remonta o estado;
 * nao sincronize com {@code useEffect}.
 */
export function CampoNomeDoLead({ leadId, valorAtual, className }: Props) {
  const textos = useTextos().painelLead.dados;
  const salvar = useSalvarFicha(leadId);
  const idCampo = useId();
  const [valor, setValor] = useState(valorAtual);
  const [erro, setErro] = useState(false);

  function persistir() {
    const canonico = valor.trim();
    if (!canonico) {
      setValor(valorAtual);
      setErro(true);
      return;
    }
    setValor(canonico);
    setErro(false);
    if (canonico === valorAtual) return;
    salvar.mutate(
      { nome: canonico },
      {
        onError: () => setErro(true),
        onSuccess: () => setErro(false),
      },
    );
  }

  return (
    <div className="min-w-0 w-full">
      <label htmlFor={idCampo} className="sr-only">
        {textos.nome}
      </label>
      <Input
        id={idCampo}
        type="text"
        autoComplete="off"
        maxLength={TAMANHO_MAXIMO_NOME}
        value={valor}
        disabled={salvar.isPending}
        aria-invalid={erro || undefined}
        className={cn(
          "h-auto border-transparent bg-transparent px-1 py-0.5 text-center font-bold shadow-none md:text-base",
          className,
        )}
        onChange={(evento) => {
          setErro(false);
          setValor(evento.target.value);
        }}
        onBlur={persistir}
        onKeyDown={(evento) => {
          if (evento.key === "Enter") evento.currentTarget.blur();
        }}
      />
      {erro && (
        <p role="alert" className="mt-1 text-[0.65rem] font-normal text-destructive">
          {textos.nomeInvalido}
        </p>
      )}
    </div>
  );
}
