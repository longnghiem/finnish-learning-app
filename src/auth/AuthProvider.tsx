import {type ReactNode, useCallback, useState} from "react";
import { AuthContext } from "./AuthContext.tsx";

export const TOKEN_KEY = 'auth_token'
const USER_ID_KEY = 'auth_userId'
const USERNAME_KEY = 'auth_username'
const ROLE_KEY = 'auth_role'

/**
 * Reads the persisted auth state from localStorage
 */
function readPersistedAuth(): {
    token: string | null
    userId: number | null
    username: string | null
    role: string | null
} {
    const token = localStorage.getItem(TOKEN_KEY)
    const userIdRaw = localStorage.getItem(USER_ID_KEY)
    const username = localStorage.getItem(USERNAME_KEY)
    const role = localStorage.getItem(ROLE_KEY)

    if (!token) {
        return { token: null, userId: null, username: null, role: null }
    }

    const userId = userIdRaw !== null ? Number(userIdRaw) : null
    const validUserId = userId !== null && Number.isFinite(userId) ? userId : null

    return {token, userId: validUserId, username, role}
}


interface AuthProviderProps {
    children: ReactNode
}

/**
 * Provides authentication state to the component tree.
 *
 * On mount, reads persisted auth from `localStorage` so the session
 * survives a page refresh. Wrap the entire application with this
 * provider (e.g. in `App.tsx`).
 *
 * @example
 * ```tsx
 * <AuthProvider>
 *   <App />
 * </AuthProvider>
 */
export function AuthProvider({ children }: AuthProviderProps) {
    const [authState, setAuthState] = useState(() => readPersistedAuth())

    const loginWithToken = useCallback(
      (token: string, userId: number, username: string, role: string): void => {
          localStorage.setItem(TOKEN_KEY, token)
          localStorage.setItem(USER_ID_KEY, String(userId))
          localStorage.setItem(USERNAME_KEY, username)
          localStorage.setItem(ROLE_KEY, role)
          setAuthState({ token, userId, username, role })
      },
      [],
    )

    const logout = useCallback((): void => {
        localStorage.removeItem(TOKEN_KEY)
        localStorage.removeItem(USER_ID_KEY)
        localStorage.removeItem(USERNAME_KEY)
        localStorage.removeItem(ROLE_KEY)
        setAuthState({ token: null, userId: null, username: null, role: null })
    }, [])

    const value = {
        isLoggedIn: authState.token !== null,
        token: authState.token,
        userId: authState.userId,
        username: authState.username,
        role: authState.role,
        isAdmin: authState.role === 'ADMIN',
        loginWithToken,
        logout,
    }

    return (
      <AuthContext.Provider value={value}>
          {children}
      </AuthContext.Provider>
    )
}