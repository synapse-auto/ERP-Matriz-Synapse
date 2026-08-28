"use client";
import { useCallback, useEffect, useState } from "react";
import { listarParticipantes } from "./api";

const UUID =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

export function useParticipantes(atendimentoId: string) {
  const valido = UUID.test(atendimentoId);
  const [data, setData] = useState<Awaited<ReturnType<typeof listarParticipantes>>>([]);
  const recarregar = useCallback(async () => {
    if (!UUID.test(atendimentoId)) return;
    try {
      setData(await listarParticipantes(atendimentoId));
    } catch {
      setData([]);
    }
  }, [atendimentoId]);
  useEffect(() => {
    if (!UUID.test(atendimentoId)) return;
    let ativo = true;
    listarParticipantes(atendimentoId)
      .then((resultado) => { if (ativo) setData(resultado); })
      .catch(() => { if (ativo) setData([]); });
    return () => { ativo = false; };
  }, [atendimentoId]);
  return { data: valido ? data : [], recarregar };
}
