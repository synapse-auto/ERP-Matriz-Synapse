import{apiFetch}from"@/lib/api/http-client";import type{AvaliacoesEquipe,MeuUsuario,PapelGerenciavel,StatusPresenca,UsuarioEquipe}from"./types";
export const listarEquipe=()=>apiFetch<UsuarioEquipe[]>("/api/v1/usuarios");export const avaliacoesEquipe=()=>apiFetch<AvaliacoesEquipe>("/api/v1/equipe/avaliacoes");export const obterMeuUsuario=()=>apiFetch<MeuUsuario>("/api/v1/me");
export function criarUsuario(d:{nome:string;email:string;senha:string;papel:PapelGerenciavel}){return apiFetch<UsuarioEquipe>("/api/v1/usuarios",{method:"POST",body:JSON.stringify(d)})}
export function editarUsuario(id:string,d:{nome:string;email:string;papel:PapelGerenciavel}){return apiFetch<UsuarioEquipe>(`/api/v1/usuarios/${id}`,{method:"PUT",body:JSON.stringify(d)})}
export const desativarUsuario=(id:string)=>apiFetch<void>(`/api/v1/usuarios/${id}/desativar`,{method:"PATCH"});
export const obterPresenca=()=>apiFetch<{status:StatusPresenca}>("/api/v1/usuarios/me/presenca");export const atualizarPresenca=(status:StatusPresenca)=>apiFetch<{status:StatusPresenca}>("/api/v1/usuarios/me/presenca",{method:"PATCH",body:JSON.stringify({status})});
