"use client";

import { useTextos } from "@/lib/config/textos-provider";

/**
 * Toda rota de menu que ainda não tem tela construída cai aqui — nunca em dado mockado. "Esta área
 * ainda não foi construída" é a verdade; um array de exemplo fingindo ser produção não seria.
 */
export function Placeholder() {
  const textos = useTextos();
  return (
    <div className="flex flex-1 items-center justify-center p-8 text-center text-texto-suave">
      <p>{textos.estados.emConstrucao}</p>
    </div>
  );
}
