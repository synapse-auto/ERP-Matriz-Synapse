import { redirect } from "next/navigation";

/** Rota antiga preservada: links e favoritos de /equipe caem na aba Equipe de Gestão. */
export default function Equipe() {
  redirect("/gestao");
}
