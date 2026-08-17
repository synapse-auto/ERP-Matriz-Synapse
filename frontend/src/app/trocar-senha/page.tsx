import { TrocarSenhaForm } from "@/components/auth/trocar-senha-form";

export default function TrocarSenha() {
  return (
    <main className="min-h-0 flex-1 overflow-y-auto bg-background px-4">
      <div className="flex min-h-full items-center justify-center py-4">
        <TrocarSenhaForm />
      </div>
    </main>
  );
}
