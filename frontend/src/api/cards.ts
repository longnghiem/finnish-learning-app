import type {SearchType} from "../types";
import type {CardResponse, ErrorResponse} from "../types";
import {API_BASE_URL, getAuthHeaders } from "./config.ts";

export interface FetchCardsParams {
    topicId?: number
    searchType?: SearchType
    searchTerm?: string
}

export async function fetchCards(
    params?: FetchCardsParams
): Promise<CardResponse[]> {
    const url = new URL(`${API_BASE_URL}/api/cards`)

    if (params?.topicId !== undefined) {
        url.searchParams.set('topicId', String(params.topicId))
    }
    if (params?.searchType && params?.searchTerm) {
        url.searchParams.set('searchType', params.searchType)
        url.searchParams.set('searchTerm', params.searchTerm)
    }

    const response = await fetch(url.toString())

    if (!response.ok) {
        const err: ErrorResponse = await response.json()
        throw new Error(`Failed to fetch cards: ${err.message}`)
    }

    return response.json()
}

export async function fetchCardById(
    id: number
): Promise<CardResponse> {
    const response = await fetch(`${API_BASE_URL}/api/cards/${id}`)

    if (!response.ok) {
        const err: ErrorResponse = await response.json()
        throw new Error(err.message)
    }

    return response.json()
}

/** Payload for creating a new card. All fields are required. */
export interface CreateCardData {
    name: string
    exampleSentence: string
    translation: string
    topicId: number
    image: File
}

/**
 * Creates a new card.
 *
 * Text fields are sent as URL query parameters and the image as a
 * multipart form-data body part named `"image"`, matching the backend
 * `CardController.createCard` signature (`@RequestParam` + `@RequestPart`).
 *
 * Requires ADMIN role on the backend, so the JWT bearer token is
 * attached via `getAuthHeaders()`. Content-Type is intentionally NOT
 * set — the browser fills it in with the correct multipart boundary.
 */
export async function createCard(data: CreateCardData): Promise<CardResponse> {
    const url = new URL(`${API_BASE_URL}/api/cards`)
    url.searchParams.set('name', data.name)
    url.searchParams.set('exampleSentence', data.exampleSentence)
    url.searchParams.set('translation', data.translation)
    url.searchParams.set('topicId', String(data.topicId))

    const formData = new FormData()
    formData.append('image', data.image)

    // Do NOT set Content-Type manually — the browser sets it with the multipart boundary.
    const response = await fetch(url.toString(), {
        method: 'POST',
        headers: { ...getAuthHeaders() },
        body: formData,
    })

    if (!response.ok) {
        const err: ErrorResponse = await response.json()
        throw new Error(err.message)
    }

    return response.json()
}

/** Payload for updating an existing card. All fields are optional. */
export interface UpdateCardData {
    name?: string
    exampleSentence?: string
    translation?: string
    topicId?: number
    image?: File
}

/**
 * Updates an existing card. Only non-undefined fields are sent.
 *
 * Text fields are sent as URL query parameters and the image (if provided)
 * as a multipart form-data body part named `"image"`, matching the backend
 * `CardController.updateCard` signature.
 */
export async function updateCard(
    id: number,
    data: UpdateCardData
): Promise<CardResponse> {
    const url = new URL(`${API_BASE_URL}/api/cards/${id}`)

    if (data.name !== undefined) {
        url.searchParams.set('name', data.name)
    }
    if (data.exampleSentence !== undefined) {
        url.searchParams.set('exampleSentence', data.exampleSentence)
    }
    if (data.translation !== undefined) {
        url.searchParams.set('translation', data.translation)
    }
    if (data.topicId !== undefined) {
        url.searchParams.set('topicId', String(data.topicId))
    }

    const formData = new FormData()
    if (data.image) {
        formData.append('image', data.image)
    }

    const response = await fetch(url.toString(), {
        method: 'PUT',
        headers: { ...getAuthHeaders() },
        body: formData,
    })

    if (!response.ok) {
        const err: ErrorResponse = await response.json()
        throw new Error(err.message)
    }

    return response.json()
}

/**
 * Deletes a card and its associated image.
 */
export async function deleteCard(id: number): Promise<void> {
    const response = await fetch(`${API_BASE_URL}/api/cards/${id}`, {
        method: 'DELETE',
        headers: { ...getAuthHeaders() },
    })

    if (!response.ok) {
        const err: ErrorResponse = await response.json()
        throw new Error(err.message)
    }
}