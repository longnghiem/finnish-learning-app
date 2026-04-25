import {AuthContext, type AuthContextType} from "./AuthContext.tsx";
import {useContext} from "react";

/**
 * Custom hook to access authentication state and actions.
 *
 * Must be used within an {@link AuthProvider}. Throws if called
 * outside the provider tree.
 *
 * @returns The current auth context value with `isLoggedIn`, `login`, and `logout`.
 */
export function useAuth(): AuthContextType {
    const context = useContext(AuthContext);
    if (context === null) {
        throw new Error("useAuth must be used within an AuthProvider");
    }
    return context;
}