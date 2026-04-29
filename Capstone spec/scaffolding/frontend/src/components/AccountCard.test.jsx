import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { describe, it, expect } from "vitest";
import AccountCard from "./AccountCard.jsx";

const mockAccount = {
  accountId: "acc_1",
  accountType: "CHECKING",
  currency: "USD",
  balance: "1500.00",
};

describe("AccountCard", () => {
  it("renders account type", () => {
    // TODO (Frontend Test 1): Render <AccountCard account={mockAccount} /> inside a <MemoryRouter>.
    // Assert that screen.getByText("CHECKING") is in the document.
    //
    // Pattern:
    //   render(<MemoryRouter><AccountCard account={mockAccount} /></MemoryRouter>);
    //   expect(screen.getByText("CHECKING")).toBeInTheDocument();
  });

  it("renders currency and balance", () => {
    // TODO (Frontend Test 2): Render <AccountCard> and assert that the text
    // matching /USD 1500.00/ is visible on screen.
    //
    // Use a regex: expect(screen.getByText(/USD 1500.00/)).toBeInTheDocument();
  });

  it("renders a link to the account detail page", () => {
    // TODO (Frontend Test 3): Render <AccountCard> and assert that the link
    // points to "/accounts/acc_1".
    //
    // Use: expect(screen.getByRole("link")).toHaveAttribute("href", "/accounts/acc_1");
  });
});
