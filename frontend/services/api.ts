import * as SecureStore from 'expo-secure-store';
import { Platform } from 'react-native';
import type { Condition } from '../types';
import type { AuthResponse, NearbyCare, NearbyCareCategory, SymptomSearchResponse } from '../types';

const API_BASE_URL = process.env.EXPO_PUBLIC_API_BASE_URL;
const AUTH_TOKEN_KEY = 'smart-health-auth-token';
let authToken: string | null = null;

type Credentials = { email: string; password: string };

class ApiError extends Error {
    constructor(message: string, readonly status: number) {
        super(message);
    }
}

export function isUnauthorizedError(error: unknown): boolean {
    return error instanceof ApiError && error.status === 401;
}

export function getApiBaseUrl(): string {
    if (!API_BASE_URL?.trim()) {
        throw new Error('EXPO_PUBLIC_API_BASE_URL must be configured.');
    }

    const baseUrl = API_BASE_URL.trim().replace(/\/+$/, '');
    let parsedUrl: URL;
    try {
        parsedUrl = new URL(baseUrl);
    } catch {
        throw new Error('EXPO_PUBLIC_API_BASE_URL must be an absolute HTTP or HTTPS URL.');
    }

    if (parsedUrl.protocol !== 'http:' && parsedUrl.protocol !== 'https:') {
        throw new Error('EXPO_PUBLIC_API_BASE_URL must use HTTP or HTTPS.');
    }

    const localHosts = ['localhost', '127.0.0.1', '::1', '[::1]', '0.0.0.0'];
    if (Platform.OS !== 'web' && localHosts.includes(parsedUrl.hostname.toLowerCase())) {
        throw new Error(
            "EXPO_PUBLIC_API_BASE_URL uses localhost, which points to the phone itself. "
            + "Use your computer's LAN IP, then restart Expo."
        );
    }

    return baseUrl;
}

async function requestJson<T>(path: string, init: RequestInit = {}): Promise<T> {
    const url = `${getApiBaseUrl()}${path}`;
    let response: Response;
    try {
        response = await fetch(url, {
            ...init,
            headers: {
                'Content-Type': 'application/json',
                ...init.headers,
            },
        });
    } catch (error) {
        if (error instanceof TypeError) {
            throw new Error('Cannot reach the backend. Check that it is running and that your phone and computer are on the same network.');
        }
        throw error;
    }
    const body = await response.json().catch(() => null);

    if (!response.ok) {
        throw new ApiError(body?.detail ?? body?.message ?? `Request failed (${response.status})`, response.status);
    }
    return body as T;
}

// Current flow: the backend converts the keyword into an NHS condition-page URL.
export async function searchCondition(query: string): Promise<Condition> {
    const params = new URLSearchParams({ query });
    return requestJson<Condition>(`/api/conditions?${params.toString()}`);
}

// Earlier API-based flow: requires a signed-in user and a verified medical data source.
export async function searchSymptoms(symptoms: string[]): Promise<SymptomSearchResponse> {
    const token = await getAuthToken();
    if (!token) {
        throw new Error('Log in or create an account to search by symptoms.');
    }
    return requestJson<SymptomSearchResponse>('/api/symptoms/search', {
        method: 'POST',
        headers: { Authorization: `Bearer ${token}` },
        body: JSON.stringify({ symptoms }),
    });
}

export async function searchNearbyCare(
    latitude: number,
    longitude: number,
    category: NearbyCareCategory
): Promise<NearbyCare[]> {
    const params = new URLSearchParams({
        latitude: String(latitude),
        longitude: String(longitude),
        category,
    });
    return requestJson<NearbyCare[]>(`/api/nearby-care?${params.toString()}`);
}

function activateSession(response: AuthResponse): string {
    if (typeof response?.accessToken !== 'string' || !response.accessToken.trim()
        || typeof response.email !== 'string' || !response.email.trim()) {
        throw new Error('The backend did not return a valid session.');
    }
    authToken = response.accessToken;
    return response.email;
}

export async function signup(input: Credentials & { firstName: string; lastName: string }): Promise<string> {
    const response = await requestJson<AuthResponse>('/api/auth/signup', {
        method: 'POST',
        body: JSON.stringify(input),
    });
    return activateSession(response);
}

export async function login(input: Credentials): Promise<string> {
    const response = await requestJson<AuthResponse>('/api/auth/login', {
        method: 'POST',
        body: JSON.stringify(input),
    });
    return activateSession(response);
}

export async function requestPasswordReset(email: string): Promise<string> {
    const response = await requestJson<{ message: string }>('/api/auth/forgot-password', {
        method: 'POST',
        body: JSON.stringify({ email }),
    });
    return response.message;
}

export async function resetPassword(email: string, token: string, newPassword: string): Promise<string> {
    const response = await requestJson<{ message: string }>('/api/auth/reset-password', {
        method: 'POST',
        body: JSON.stringify({ email, token, newPassword }),
    });
    return response.message;
}

export async function getAuthToken(): Promise<string | null> {
    return authToken;
}

export async function clearLegacySession(): Promise<void> {
    if (Platform.OS === 'web') return;
    const storedToken = await SecureStore.getItemAsync(AUTH_TOKEN_KEY);
    if (!storedToken) return;
    await SecureStore.deleteItemAsync(AUTH_TOKEN_KEY);
    try {
        await requestJson<void>('/api/auth/logout', {
            method: 'POST',
            headers: { Authorization: `Bearer ${storedToken}` },
        });
    } catch (error) {
        const detail = error instanceof Error ? ` ${error.message}` : '';
        throw new Error(`The previous saved session was removed from this device but could not be revoked on the server.${detail}`);
    }
}

export async function checkSession(): Promise<string | null> {
    const token = await getAuthToken();
    if (!token) return null;
    try {
        const response = await requestJson<{ message: string }>('/api/auth/me', {
            headers: { Authorization: `Bearer ${token}` },
        });
        if (typeof response?.message !== 'string' || !response.message.trim()) {
            throw new Error('The backend did not return a valid account for this session.');
        }
        return response.message;
    } catch (error) {
        if (error instanceof ApiError && error.status === 401) return null;
        throw error;
    }
}

export async function logout(): Promise<void> {
    const token = authToken;
    authToken = null;
    if (token) {
        await requestJson<void>('/api/auth/logout', {
            method: 'POST',
            headers: { Authorization: `Bearer ${token}` },
        });
    }
}