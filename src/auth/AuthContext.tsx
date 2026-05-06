import {createContext} from "react";

/**
 * Shape of the authentication context value.
 *
 * Stores the JWT and the user identity returned by the backend at
 * login/register time. The token is the source of truth for "is the
 * user logged in" — `isLoggedIn` is just `token !== null`.
 */
export interface AuthContextType {
    /** Whether the user is currently authenticated (i.e. a token is stored). */
    isLoggedIn: boolean

    /** Raw JWT to attach to authenticated API requests, or null when logged out. */
    token: string | null

    /** Matches the backend `users.id` column. */
    userId: number | null

    /** Display username, sourced from the AuthResponse. */
    username: string | null

    /** Backend role string: "USER" or "ADMIN". */
    role: string | null

    /**
     * Convenience flag derived from `role === 'ADMIN'`. Use this in
     * components instead of comparing role strings directly.
     */
    isAdmin: boolean

    /**
     * Stores the credentials returned from the backend.
     *
     * Called by `LoginPage` after a successful `loginUser()` call and
     * by `RegisterPage` after a successful `registerUser()` call.
     * The provider persists all four values to `localStorage` so the
     * session survives a page refresh.
     */
    loginWithToken: (
      token: string,
      userId: number,
      username: string,
      role: string,
    ) => void

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

