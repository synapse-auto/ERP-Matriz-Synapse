"use client";
import { useCallback, useEffect, useState } from "react";
import { listarParticipantes } from "./api";
export function useParticipantes(atendimentoId: string) {
  const [data, setData] = useState<Awaited<ReturnType<typeof listarParticipantes>>>([]);
  const recarregar = useCallback(async () => {
    try {
      setData(await listarParticipantes(atendimentoId));
    } catch {
      setData([]);
    }
  }, [atendimentoId]);
  useEffect(() => {
    let ativo = true;
    listarParticipantes(atendimentoId)
      .then((resultado) => { if (ativo) setData(resultado); })
      .catch(() => { if (ativo) setData([]); });
    return () => { ativo = false; };
  }, [atendimentoId]);
  return { data, recarregar };
}
