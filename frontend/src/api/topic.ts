import type {ErrorResponse, TopicResponse} from "../types";
import {API_BASE_URL} from "./config.ts";

export async function fetchTopics(): Promise<TopicResponse[]> {
    const response = await fetch(`${API_BASE_URL}/api/topics`)

    if (!response.ok) {
        const err: ErrorResponse = await response.json()
        throw new Error(`Failed to fetch topics: ${err.message}`)
    }

    return response.json()
}