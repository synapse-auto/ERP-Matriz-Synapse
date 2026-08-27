import { render } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import AdministracaoPage from "./page";
import { useAuthStore } from "@/lib/auth/auth-store";
import { useRouter } from "next/navigation";

vi.mock("@/lib/auth/auth-store", () => ({
  useAuthStore: vi.fn(),
}));

vi.mock("next/navigation", () => ({
  useRouter: vi.fn(),
}));

vi.mock("@/components/shell/placeholder", () => ({
  Placeholder: () => <div data-testid="placeholder">Placeholder</div>,
}));

type AuthStoreSelector = Parameters<typeof useAuthStore>[0];

describe("AdministracaoPage", () => {
  const mockReplace = vi.fn();

  beforeEach(() => {
    vi.clearAllMocks();
    (useRouter as import("vitest").Mock).mockReturnValue({ replace: mockReplace });
  });

  it("deve bloquear acesso para ATENDENTE", () => {
    (useAuthStore as unknown as import("vitest").Mock).mockImplementation(
      (selector: AuthStoreSelector) => selector({ papel: "ATENDENTE" } as Parameters<AuthStoreSelector>[0]),
    );
    const { queryByTestId } = render(<AdministracaoPage />);
    expect(mockReplace).toHaveBeenCalledWith("/");
    expect(queryByTestId("placeholder")).toBeNull();
  });

  it("deve permitir acesso para GESTOR", () => {
    (useAuthStore as unknown as import("vitest").Mock).mockImplementation(
      (selector: AuthStoreSelector) => selector({ papel: "GESTOR" } as Parameters<AuthStoreSelector>[0]),
    );
    const { getByTestId } = render(<AdministracaoPage />);
    expect(mockReplace).not.toHaveBeenCalled();
    expect(getByTestId("placeholder")).toBeInTheDocument();
  });

  it("deve permitir acesso para ADMINISTRADOR", () => {
    (useAuthStore as unknown as import("vitest").Mock).mockImplementation(
      (selector: AuthStoreSelector) => selector({ papel: "ADMINISTRADOR" } as Parameters<AuthStoreSelector>[0]),
    );
    const { getByTestId } = render(<AdministracaoPage />);
    expect(mockReplace).not.toHaveBeenCalled();
    expect(getByTestId("placeholder")).toBeInTheDocument();
  });
});
