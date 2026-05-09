import {TOKEN_KEY} from "../auth/AuthProvider.tsx";

/** Backend base URL, read from Vite env variable */
export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'

export function getImageUrl(relativeImageUrl: string): string {
    return `${API_BASE_URL}${relativeImageUrl}`
}

/**
 * Returns headers carrying the JWT bearer token, or an empty object
 * when no token is stored.
 */
export function getAuthHeaders(): HeadersInit {
    const token = localStorage.getItem(TOKEN_KEY)
    if (!token) {
        return {}
    } else {
        return { 'Authorization': `Bearer ${token}` }
    }
}