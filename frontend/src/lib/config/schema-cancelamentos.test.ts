import { expect, it } from "vitest";
import { TextosSchema } from "./schema";
import { textosReais } from "@/test/textos-reais";
import { readFileSync } from "node:fs";
import path from "node:path";
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
it("rotulos da Femina sao configuracao e nao rebatizam tipo como especialidade",()=>{
  const sobreposicao=JSON.parse(readFileSync(path.resolve(import.meta.dirname,"../../../../docker/provisionamento/instancias/femina/cancelamentos.textos.override.json"),"utf8"));
  const catalogo=structuredClone(textosReais);
  const resultado=TextosSchema.parse({...catalogo,cancelamentos:{...catalogo.cancelamentos,...sobreposicao.cancelamentos}});
  expect(resultado.cancelamentos.contato).toBe("Paciente");
  expect(resultado.cancelamentos.profissional).toBe("Médico");
  expect(resultado.cancelamentos.agendamento).toBe("Data e hora da agenda");
  expect(resultado.cancelamentos.canceladoEm).toBe("Data e hora do cancelamento");
  expect(resultado.cancelamentos.tipo).toBe("Tipo de agendamento");
  expect(catalogo.cancelamentos.contato).toBe("Contato");
});
it("catalogos antigos recebem identificacao e ordenacao sem sobrescrever personalizacao",()=>{
  const antigo=structuredClone(textosReais) as unknown as {cancelamentos:Record<string,unknown>};
  for(const chave of ["identificacaoTitulo","identificacaoAjuda","cpf","telefone","ordenacao"]) delete antigo.cancelamentos[chave];
  antigo.cancelamentos.contato="Paciente";
  const resultado=TextosSchema.parse(antigo);
  expect(resultado.cancelamentos.contato).toBe("Paciente");
  expect(resultado.cancelamentos.identificacaoTitulo).toBeTruthy();
  expect(resultado.cancelamentos.ordenacao).toContain("{base}");
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
