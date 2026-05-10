import type {CardResponse, SearchType} from "../types";
import {useMutation, useQuery, useQueryClient} from "@tanstack/react-query";
import {createCard, type CreateCardData, deleteCard, fetchCards, updateCard, type UpdateCardData} from "../api";

const CARDS_QUERY_KEY = ['cards'] as const

export interface UpdateCardVariables {
    id: number
    data: UpdateCardData
}

/**
 * Fetch cards with optional filters
 */
export function useCards(
    topicId?: number,
    searchType?: SearchType,
    searchTerm?: string,
) {
    const hasSearch = !!searchTerm && !!searchType

    return useQuery<CardResponse[], Error>({
        queryKey: [...CARDS_QUERY_KEY, {topicId, searchType, searchTerm}],
        queryFn: () => {
            return fetchCards({
                topicId,
                searchType: hasSearch ? searchType : undefined,
                searchTerm: hasSearch ? searchTerm : undefined,
            })
        }
    })
}


/**
 * Mutation hook for creating a new card.
 *
 * On success, calls `invalidateQueries` with the `['cards']` key. This marks every
 * cached query whose key starts with `['cards']` as stale, so any mounted
 * `useCards(...)` — regardless of its filter args — refetches and reflects the
 * newly created card.
 */
export function useCreateCard() {
    const queryClient = useQueryClient()

    return useMutation<CardResponse, Error, CreateCardData>({
        mutationFn: (data) => createCard(data),
        onSuccess: async () => {
            await queryClient.invalidateQueries({ queryKey: CARDS_QUERY_KEY })
        }
    })
}

/**
 * Mutation hook for updating an existing card.
 */
export function useUpdateCard() {
    const queryClient = useQueryClient()

    return useMutation<CardResponse, Error, UpdateCardVariables>({
        mutationFn: ({id, data}) => updateCard(id, data),
        onSuccess: async () => {
            await queryClient.invalidateQueries({ queryKey: CARDS_QUERY_KEY })
        }
    })
}

/**
 * Mutation hook for deleting a card and its associated image.
 */
export function useDeleteCard() {
    const queryClient = useQueryClient()

    return useMutation<void, Error, number>({
        mutationFn: (id) => deleteCard(id),
        onSuccess: async () => {
            await queryClient.invalidateQueries({ queryKey: CARDS_QUERY_KEY })
        },
    })
}