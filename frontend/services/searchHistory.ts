import AsyncStorage from '@react-native-async-storage/async-storage';
import type { SearchHistoryEntry } from '../types';
import { getApiBaseUrl } from './api';

function storageKey(email: string): string {
    return `@smarthhealth/search-history/v2/${encodeURIComponent(getApiBaseUrl())}/${encodeURIComponent(email.trim().toLowerCase())}`;
}

export async function loadSearchHistory(email: string): Promise<SearchHistoryEntry[]> {
    const saved = await AsyncStorage.getItem(storageKey(email));
    if (!saved) return [];
    const parsed: unknown = JSON.parse(saved);
    if (!Array.isArray(parsed)) throw new Error('Saved search history has an invalid format.');
    return parsed as SearchHistoryEntry[];
}

export async function saveSearchHistoryEntry(email: string, entry: SearchHistoryEntry): Promise<void> {
    const history = await loadSearchHistory(email);
    await AsyncStorage.setItem(storageKey(email), JSON.stringify([entry, ...history]));
}
