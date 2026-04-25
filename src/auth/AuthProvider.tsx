import {type ReactNode, useCallback, useState} from "react";
import {VALID_PASSWORD, VALID_USERNAME} from "./credentials.ts";
import { AuthContext } from "./AuthContext.tsx";

const LOCAL_STORAGE_KEY = 'isLoggedIn'

interface AuthProviderProps {
    children: ReactNode
}

/**
 * Provides authentication state to the component tree.
 *
 * On mount, reads `localStorage` to restore a previous session.
 * Wrap the entire application with this provider (e.g. in `App.tsx`
 * or `main.tsx`).
 *
 * @example
 * ```tsx
 * <AuthProvider>
 *   <App />
 * </AuthProvider>
 */
export function AuthProvider({ children }: AuthProviderProps) {
    const [isLoggedIn, setIsLoggedIn] = useState<boolean>(
        () => localStorage.getItem(LOCAL_STORAGE_KEY) === 'true',
    )

    const login = useCallback((username: string, password: string): boolean => {
        if (username === VALID_USERNAME && password === VALID_PASSWORD) {
            localStorage.setItem(LOCAL_STORAGE_KEY, 'true')
            setIsLoggedIn(true)
            return true
        }
        return false
    }, [])

    const logout = useCallback((): void => {
        localStorage.removeItem(LOCAL_STORAGE_KEY)
        setIsLoggedIn(false)
    }, [])

    return (
        <AuthContext.Provider value={{ isLoggedIn, login, logout }}>
            {children}
        </AuthContext.Provider>
    )
}