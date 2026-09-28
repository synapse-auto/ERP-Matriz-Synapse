import { ExigeCapacidade } from "@/components/gestao/exige-capacidade";
import { PaginaDashboard } from "@/components/dashboard/pagina-dashboard";

export default function Dashboard() {
  return (
    <ExigeCapacidade capacidade="dashboard.ver">
      <PaginaDashboard />
    </ExigeCapacidade>
  );
}
