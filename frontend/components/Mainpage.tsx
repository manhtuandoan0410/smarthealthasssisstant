import { useState } from 'react';
import { ActivityIndicator, Pressable, ScrollView, StyleSheet, Text, TextInput, View } from 'react-native';

import ConditionResult from './ConditionResult';
import type { Condition, SearchHistoryEntry } from '../types';
import { searchCondition } from '../services/api';
import { useAuth } from '../services/AuthContext';
import { saveSearchHistoryEntry } from '../services/searchHistory';

export default function Mainpage() {
    const [name, setName] = useState("");
    const [result, setResult] = useState<Condition | null>(null);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState('');
    const { status, email } = useAuth();

    // Ask the backend for a generated NHS URL; save the search only for the signed-in account.
    const handleSubmit = async () => {
        const query = name.trim();
        if (!query) return;
        const account = status === 'signed-in' ? email : null;
        setError('');
        setResult(null);
        setLoading(true);
        try {
            const answer = await searchCondition(query);
            setResult(answer);
            setName('');
            if (account) {
                const entry: SearchHistoryEntry = {
                    id: `${Date.now()}`,
                    query,
                    answer,
                    searchedAt: new Date().toISOString(),
                };
                try {
                    await saveSearchHistoryEntry(account, entry);
                } catch (historyError) {
                    setError(`Search succeeded, but history could not be saved: ${historyError instanceof Error ? historyError.message : 'Storage error.'}`);
                }
            }
        } catch (searchError) {
            setResult(null);
            setError(searchError instanceof Error ? searchError.message : 'Condition search failed.');
        } finally {
            setLoading(false);
        }
    }

    return (
        <ScrollView
            contentContainerStyle={styles.content}
            keyboardShouldPersistTaps="handled"
        >
            <View style={styles.searchRow}>
                <TextInput
                    accessibilityLabel="Disease or condition"
                    placeholder="Disease or condition"
                    value={name}
                    onChangeText={setName}
                    onSubmitEditing={handleSubmit}
                    returnKeyType="search"
                    style={styles.input}
                />
                <Pressable
                    accessibilityRole="button"
                    disabled={loading}
                    onPress={() => void handleSubmit()}
                    style={({ pressed }) => [styles.searchButton, pressed && styles.pressed]}
                >
                    {loading ? <ActivityIndicator color="#ffffff" /> : <Text style={styles.searchButtonText}>Search</Text>}
                </Pressable>
            </View>
            {error ? <Text accessibilityRole="alert" style={styles.error}>{error}</Text> : null}
            {result ? <ConditionResult condition={result} showNhsPage /> : null}
        </ScrollView>
    );
}

const styles = StyleSheet.create({
    content: {
        flexGrow: 1,
        alignItems: 'center',
        paddingHorizontal: 20,
        paddingTop: 32,
        paddingBottom: 24,
    },
    searchRow: {
        width: '100%',
        maxWidth: 600,
        flexDirection: 'row',
        alignItems: 'center',
        gap: 12,
    },
    input: {
        flex: 1,
        minHeight: 52,
        paddingHorizontal: 14,
        borderWidth: 1,
        borderColor: '#b8cec6',
        borderRadius: 10,
        backgroundColor: '#ffffff',
        color: '#18352f',
        fontSize: 16,
    },
    searchButton: {
        minHeight: 48,
        justifyContent: 'center',
        paddingHorizontal: 18,
        borderRadius: 10,
        backgroundColor: '#167d68',
    },
    pressed: {
        opacity: 0.8,
    },
    searchButtonText: {
        color: '#ffffff',
        fontSize: 16,
        fontWeight: '700',
    },
    error: {
        width: '100%',
        maxWidth: 600,
        marginTop: 16,
        color: '#a52727',
        fontSize: 14,
    },
});