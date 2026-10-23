import { PaginaAgenda } from "@/components/agenda/pagina-agenda";
import { ExigeCapacidade } from "@/components/gestao/exige-capacidade";

export default function Agenda() {
  return <ExigeCapacidade capacidade="clientes.acessar"><PaginaAgenda /></ExigeCapacidade>;
}
