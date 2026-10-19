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
it("catalogo anterior ao refinamento recebe ajuda e vazio novos sem perder textos personalizados",()=>{
  const antigo=structuredClone(textosReais) as unknown as {cancelamentos:Record<string,unknown>};
  delete antigo.cancelamentos.maisFiltros;delete antigo.cancelamentos.vazioPeriodo;delete antigo.cancelamentos.periodoExcedido;
  antigo.cancelamentos.titulo="Agenda da operação";
  const resultado=TextosSchema.parse(antigo);
  expect(resultado.cancelamentos.titulo).toBe("Agenda da operação");
  expect(resultado.cancelamentos.maisFiltros).toBe("Mais filtros");
  expect(resultado.cancelamentos.vazioPeriodo).toBeTruthy();
  expect(resultado.cancelamentos.periodoExcedido).toContain("{dias}");
});
