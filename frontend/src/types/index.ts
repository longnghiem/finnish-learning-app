export interface CardResponse {
    id: number
    topicId: number
    name: string
    exampleSentence: string
    translation: string
    imageUrl: string       // relative path, e.g. "/api/images/uuid.png"
    createdAt: string
    updatedAt: string
}

export interface TopicResponse {
    id: number
    name: string
}

export interface ErrorResponse {
    status: number
    error: string
    message: string
}

/** Matches the backend SearchType enum */
export type SearchType = 'VERB' | 'SENTENCE'