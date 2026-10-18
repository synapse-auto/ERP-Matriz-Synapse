import { ExigeCapacidade } from "@/components/gestao/exige-capacidade";
import { PaginaCancelamentos } from "@/components/cancelamentos/pagina-cancelamentos";

export default function Cancelamentos() {
  return <ExigeCapacidade capacidade="cancelamentos.ver"><PaginaCancelamentos /></ExigeCapacidade>;
}
