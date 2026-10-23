import { QueryClient } from "@tanstack/react-query";
import { describe, expect, it } from "vitest";
import { revogarCacheDeClientes } from "./revogar-cache-clientes";
import { CHAVE_MINHAS_PERMISSOES } from "./use-gestao";

describe("revogacao de acesso a clientes", () => {
  it("remove dados externos e seletores, preserva conversa interna e ignora resposta atrasada", async () => {
    const cache = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    cache.setQueryData(CHAVE_MINHAS_PERMISSOES, {
      usuarioId: "interno", capacidades: {
        "clientes.acessar": { permitido: true },
        "atendimentos.ver": { permitido: true, alcance: "MEUS" },
        "chat_interno.usar": { permitido: true },
      },
    });
    cache.setQueryData(["chat-interno", "mensagens", "equipe"], ["interna"]);
    cache.setQueryData(["lead", "cliente"], { nome: "sintetico" });
    cache.setQueryData(["dialogos", "atendimentos", "TODOS"], ["cliente"]);
    cache.setQueryData(["automacao", "telemetria"], { clientesTransferidos: 42 });
    cache.setQueryData(["automacao", "config"], ["configuracao permitida"]);
    let terminar!: (valor: string[]) => void;
    const voo = cache.fetchQuery({ queryKey: ["mensagens", "cliente"],
      queryFn: () => new Promise<string[]>(resolve => { terminar = resolve; }) }).catch(() => undefined);
    revogarCacheDeClientes(cache);
    terminar(["resposta antiga"]);
    await voo;
    expect(cache.getQueryData(["lead", "cliente"])).toBeUndefined();
    expect(cache.getQueryData(["dialogos", "atendimentos", "TODOS"])).toBeUndefined();
    expect(cache.getQueryData(["mensagens", "cliente"])).toBeUndefined();
    expect(cache.getQueryData(["automacao", "telemetria"])).toBeUndefined();
    expect(cache.getQueryData(["automacao", "config"])).toEqual(["configuracao permitida"]);
    expect(cache.getQueryData(["chat-interno", "mensagens", "equipe"])).toEqual(["interna"]);
    expect(cache.getQueryData(CHAVE_MINHAS_PERMISSOES)).toMatchObject({ capacidades: {
      "clientes.acessar": { permitido: false }, "chat_interno.usar": { permitido: true },
    } });
    cache.clear();
  });
});
