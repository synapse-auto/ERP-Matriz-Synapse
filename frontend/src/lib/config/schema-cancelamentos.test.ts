import { expect, it } from "vitest";
import { TextosSchema } from "./schema";
import { textosReais } from "@/test/textos-reais";
it("catalogo antigo recebe novos textos e capacidade sem perder personalizacao",()=>{
  const antigo=structuredClone(textosReais) as Record<string, unknown>;
  delete antigo.cancelamentos;
  const gestao=antigo.gestao as typeof textosReais.gestao;
  delete gestao.modulos.cancelamentos;
  delete gestao.capacidades["cancelamentos.ver"];
  const resultado=TextosSchema.parse(antigo);
  expect(resultado.cancelamentos.titulo).toBe("Cancelamentos");
  expect(resultado.gestao.capacidades["cancelamentos.ver"]).toBeTruthy();
  expect(resultado.gestao.modulos.cancelamentos.rotulo).toBe("Cancelamentos");
});
