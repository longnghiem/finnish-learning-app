import {TOKEN_KEY} from "../auth/AuthProvider.tsx";

const rawBaseUrl = import.meta.env.VITE_API_BASE_URL

/**
 * Backend base URL.
 *
 * Resolution modes:
 *  - `undefined` (no env var set)       → dev fallback `http://localhost:8080`.
 *  - `""` (empty string, prod build)    → same-origin requests; nginx reverse-proxies
 *                                         `/api/*` and `/uploads/*` to the backend.
 *  - explicit absolute URL              → cross-origin requests to that backend.
 *
 * The single value is consumed by every `frontend/src/api/*.ts` module and by
 * `getImageUrl` below, so both API calls and image URLs share the same origin policy.
 */
export const API_BASE_URL = rawBaseUrl === undefined ? 'http://localhost:8080' : rawBaseUrl

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