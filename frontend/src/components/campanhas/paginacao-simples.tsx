"use client";

import { Button } from "@/components/ui/button";
import { interpolarCatalogo } from "@/lib/atendimento/variaveis-do-template";
import { TAMANHO_DA_PAGINA } from "@/lib/campanhas/hooks";
import { useTextos } from "@/lib/config/textos-provider";

interface Props {
  pagina: number;
  total: number;
  rotulo: string;
  aoMudar: (pagina: number) => void;
}

export function PaginacaoSimples({ pagina, total, rotulo, aoMudar }: Props) {
  const textos = useTextos().campanhas.lista;
  const totalDePaginas = Math.max(1, Math.ceil(total / TAMANHO_DA_PAGINA));
  if (totalDePaginas <= 1) return null;
  return (
    <nav aria-label={rotulo} className="flex items-center justify-end gap-3 text-sm">
      <Button variant="outline" size="sm" disabled={pagina === 0} onClick={() => aoMudar(pagina - 1)}>
        {textos.paginaAnterior}
      </Button>
      <span aria-live="polite">{interpolarCatalogo(textos.paginaDe, { pagina: String(pagina + 1), total: String(totalDePaginas) })}</span>
      <Button variant="outline" size="sm" disabled={pagina + 1 >= totalDePaginas} onClick={() => aoMudar(pagina + 1)}>
        {textos.paginaSeguinte}
      </Button>
    </nav>
  );
}
