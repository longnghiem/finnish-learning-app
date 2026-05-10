import {useQuery} from "@tanstack/react-query";
import {fetchTopics} from "../api";

/**
 * Fetches all predefined Finnish learning topics.
 *
 * Topics are static reference data seeded in the database, so `staleTime`
 * is set to `Infinity` — they are fetched once and never refetched automatically.
 *
 * @returns A TanStack Query result containing the list of topics.
 */
export function useTopics() {
    return useQuery({
        queryKey: ['topic'],
        queryFn: fetchTopics,
        staleTime: Infinity,
    })
}