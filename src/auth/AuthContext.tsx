import {createContext} from "react";

/** Shape of the authentication context value. */
export interface AuthContextType {
    /** Whether the user is currently authenticated. */
    isLoggedIn: boolean

    /**
     * Attempts to log in with the given credentials.
     *
     * Compares against hardcoded values. On success the flag is persisted
     * to `localStorage` so state survives page refreshes.
     *
     * @param username - The username to validate.
     * @param password - The password to validate.
     * @returns `true` if credentials match, `false` otherwise.
     */
    login: (username: string, password: string) => boolean

    /**
     * Logs the user out and clears persisted auth state.
     */
    logout: () => void
}

/**
 * React Context for authentication state.
 *
 * Consumers should use the {@link useAuth} hook instead of consuming
 * this context directly.
 */
export const AuthContext = createContext<AuthContextType | null>(null)

