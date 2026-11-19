export type Condition = {
    name: string;
    causes: string;
    symptoms: string;
    treatment: string;
    sourceUrl?: string | null;
};

export type SymptomSearchResponse = {
    disclaimer: string;
    possibilities: Condition[];
};

export type NearbyCare = {
    id: string;
    name: string;
    address: string;
    phoneNumber?: string; //phone number is usually started with 0, so it has to be string
    latitude: number;
    longitude: number;
    mapUrl?: string;
    rating?: number | null;
};

export type NearbyCareCategory = 'hospital' | 'pharmacy';

export type AuthResponse = {
    accessToken: string;
    tokenType: 'Bearer';
    expiresAt: string;
    email: string;
    role: 'USER' | 'ADMIN';
};

export type SearchHistoryEntry = {
    id: string;
    query: string;
    answer: Condition;
    searchedAt: string;
};