import {render,screen} from "@testing-library/react";
import {describe,expect,it,vi} from "vitest";
import {AvisoContaTeste} from "./aviso-conta-teste";

let usuarioTeste=false;
vi.mock("@/lib/equipe/use-equipe",()=>({useMeuUsuario:()=>({data:{usuarioTeste}})}));
vi.mock("@/lib/config/textos-provider",()=>({useTextos:()=>({usuariosTeste:{modoLeitura:"Conta de teste — somente leitura na operação normal."}})}));
describe("aviso da conta",()=>{
  it("conta comum nao recebe aviso; conta teste recebe indicacao persistente de leitura",()=>{
    usuarioTeste=false;
    const tela=render(<AvisoContaTeste/>);
    expect(screen.queryByRole("status")).not.toBeInTheDocument();
    usuarioTeste=true;
    tela.rerender(<AvisoContaTeste/>);
    expect(screen.getByRole("status")).toHaveTextContent("somente leitura");
  });
});
