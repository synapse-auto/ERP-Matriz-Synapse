import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { describe, it, expect, vi, beforeEach } from "vitest";
import textos from "../../../../backend/crm-app/src/main/resources/textos.json";
import { CompartilharContatoChat, ContatoCompartilhadoChat, usuarioDoContato } from "./contato-compartilhado-chat";
import * as api from "@/lib/chat-interno/api";
import { copiarTexto } from "@/lib/mensagens/copiar-texto";

const {push}=vi.hoisted(()=>({push:vi.fn()}));
vi.mock("next/navigation",()=>({useRouter:()=>({push})}));
vi.mock("@/lib/config/textos-provider",()=>({useTextos:()=>textos}));
vi.mock("@/lib/chat-interno/api",()=>({listarContatosChat:vi.fn(),abrirConversaDireta:vi.fn(),enviarContatoChat:vi.fn()}));
vi.mock("@/lib/mensagens/copiar-texto",()=>({copiarTexto:vi.fn().mockResolvedValue(true)}));

const id="11111111-1111-4111-8111-111111111111";
function montar(elemento:React.ReactNode){return render(<QueryClientProvider client={new QueryClient({defaultOptions:{queries:{retry:false},mutations:{retry:false}}})}>{elemento}</QueryClientProvider>);}
beforeEach(()=>{vi.clearAllMocks();vi.mocked(api.listarContatosChat).mockResolvedValue([{id,nome:"Pessoa interna",presenca:"ONLINE"}]);});

describe("contato do chat interno",()=>{
  it("não deduz usuário por nome, número, waId ou identidade externa",()=>{
    expect(usuarioDoContato(JSON.stringify({contatos:[{nome:"Pessoa interna",telefones:[{numero:"6133334444",waId:id}]}]}))).toBeNull();
    expect(usuarioDoContato(JSON.stringify({contatos:[{origem:"EXTERNO",usuarioId:id}]}))).toBeNull();
    expect(usuarioDoContato(JSON.stringify({contatos:[{origem:"INTERNO",usuarioId:id}]}))).toBe(id);
    expect(usuarioDoContato("<html>")).toBeNull();
  });
  it("mostra múltiplos números, copiar/ligar e nunca abre atendimento externo",async()=>{
    montar(<ContatoCompartilhadoChat mensagem={{id:"m",conversaId:"c",remetenteId:"u",remetenteNome:"Autor",conteudo:null,tipo:"CONTATO",enviadoEm:"2026-09-26T00:00:00Z",midiaMetadados:{contatos:[{nome:"Contato externo",telefones:[{numero:"6133334444"},{numero:"61999991234"}]}]}}}/>);
    expect(screen.getByText("Contato externo")).toBeVisible();
    expect(screen.getAllByRole("link")).toHaveLength(2);
    expect(screen.queryByRole("button",{name:"Abrir conversa interna"})).not.toBeInTheDocument();
    fireEvent.click(screen.getAllByRole("button",{name:/Copiar/})[0]);
    await waitFor(()=>expect(copiarTexto).toHaveBeenCalledWith("6133334444"));
    expect(api.listarContatosChat).not.toHaveBeenCalled();
  });
  it("abre somente pelo UUID explícito autorizado, depois de clique",async()=>{
    vi.mocked(api.abrirConversaDireta).mockResolvedValue({id:"conversa-autorizada"});
    montar(<ContatoCompartilhadoChat mensagem={{id:"m",conversaId:"c",remetenteId:"u",remetenteNome:"Autor",conteudo:null,enviadoEm:"2026-09-26T00:00:00Z",midiaMetadados:{contatos:[{nome:"Pessoa interna",origem:"INTERNO",usuarioId:id,telefones:[]}]}}}/>);
    const botao=await screen.findByRole("button",{name:"Abrir conversa interna"});
    expect(api.abrirConversaDireta).not.toHaveBeenCalled();fireEvent.click(botao);
    await waitFor(()=>expect(api.abrirConversaDireta).toHaveBeenCalledWith(id));
    await waitFor(()=>expect(push).toHaveBeenCalledWith("/chat-interno?conversaId=conversa-autorizada"));
  });
  it("destino não autorizado não recebe ação de abertura",async()=>{
    vi.mocked(api.listarContatosChat).mockResolvedValue([]);
    montar(<ContatoCompartilhadoChat mensagem={{id:"m",conversaId:"c",remetenteId:"u",remetenteNome:"Autor",conteudo:null,enviadoEm:"2026-09-26T00:00:00Z",midiaMetadados:{contatos:[{nome:"Inativo",origem:"INTERNO",usuarioId:id,telefones:[]}]}}}/>);
    await waitFor(()=>expect(api.listarContatosChat).toHaveBeenCalled());
    expect(screen.queryByRole("button",{name:"Abrir conversa interna"})).not.toBeInTheDocument();
  });
  it("envia contato externo em uma requisição e preserva campos/chave no erro",async()=>{
    vi.mocked(api.enviarContatoChat).mockRejectedValueOnce(new Error("Rede"));
    montar(<CompartilharContatoChat conversaId="c"/>);
    fireEvent.click(screen.getByRole("button",{name:"Compartilhar contato"}));
    const dialogo=screen.getByRole("dialog");
    fireEvent.click(within(dialogo).getByRole("combobox",{name:"Compartilhar contato"}));
    const externo=await screen.findByRole("option",{name:"Contato externo"});
    fireEvent.pointerDown(externo,{pointerType:"mouse"});
    fireEvent.click(externo);
    fireEvent.change(await within(dialogo).findByLabelText("Nome"),{target:{value:"Contato informado"}});
    fireEvent.change(within(dialogo).getByLabelText("Telefones (um por linha)"),{target:{value:"6133334444\n61999991234"}});
    fireEvent.click(within(dialogo).getByRole("button",{name:"Compartilhar contato"}));
    await waitFor(()=>expect(within(dialogo).getByRole("alert")).toBeVisible());
    expect(within(dialogo).getByLabelText("Nome")).toHaveValue("Contato informado");
    const chave=vi.mocked(api.enviarContatoChat).mock.calls[0][2];
    vi.mocked(api.enviarContatoChat).mockResolvedValueOnce({id:"m",conversaId:"c",remetenteId:"u",remetenteNome:"Autor",conteudo:null,enviadoEm:"2026-09-26T00:00:00Z"});
    fireEvent.click(within(dialogo).getByRole("button",{name:"Compartilhar contato"}));
    await waitFor(()=>expect(api.enviarContatoChat).toHaveBeenLastCalledWith("c",{nome:"Contato informado",telefones:["6133334444","61999991234"]},chave));
    await waitFor(()=>expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
  });
});
