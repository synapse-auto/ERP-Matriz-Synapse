import { LoginForm } from "@/components/auth/login-form";

export default function LoginPage() {
  return (
    <main className="min-h-0 flex-1 overflow-y-auto bg-background px-4">
      <div className="flex min-h-full items-center justify-center py-4">
        <LoginForm />
      </div>
    </main>
  );
}
