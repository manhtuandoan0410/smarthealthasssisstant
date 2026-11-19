import { useCallback, useEffect, useState } from 'react';
import { useFocusEffect } from '@react-navigation/native';
import { ActivityIndicator, FlatList, StyleSheet, Text, View } from 'react-native';

import ConditionResult from './ConditionResult';
import { useAuth } from '../services/AuthContext';
import { loadSearchHistory } from '../services/searchHistory';
import type { SearchHistoryEntry } from '../types';

export default function SearchHistory() {
    const { status, email, error: sessionError, refreshSession } = useAuth();
    const [history, setHistory] = useState<SearchHistoryEntry[]>([]);
    const [historyOwner, setHistoryOwner] = useState<string | null>(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');

    useFocusEffect(useCallback(() => {
        void refreshSession();
    }, [refreshSession]));

    useEffect(() => {
        let active = true;
        setHistory([]);
        setHistoryOwner(null);
        setError('');
        if (status !== 'signed-in' || !email) {
            setLoading(false);
            return () => { active = false; };
        }

        setLoading(true);
        loadSearchHistory(email)
            .then((saved) => {
                if (active) {
                    setHistory(saved);
                    setHistoryOwner(email);
                }
            })
            .catch((historyError) => {
                if (active) setError(historyError instanceof Error ? historyError.message : 'Could not load search history.');
            })
            .finally(() => {
                if (active) setLoading(false);
            });
        return () => { active = false; };
    }, [status, email]);

    if (status === 'signed-out') {
        return (
            <View style={styles.center}>
                <Text style={styles.secondaryText}>Log in or create an account to see search history.</Text>
            </View>
        );
    }

    if (status === 'error' || error) {
        return (
            <View style={styles.center}>
                <Text accessibilityRole="alert" style={styles.error}>{status === 'error' ? sessionError : error}</Text>
            </View>
        );
    }

    if (status === 'checking' || loading || historyOwner !== email) {
        return (
            <View style={styles.center}>
                <ActivityIndicator color="#167d68" />
                <Text style={styles.secondaryText}>Loading local search history...</Text>
            </View>
        );
    }

    return (
        <FlatList
            data={history}
            keyExtractor={(item) => item.id}
            contentContainerStyle={styles.list}
            ListEmptyComponent={<Text style={[styles.secondaryText, styles.empty]}>No searches yet.</Text>}
            renderItem={({ item }) => (
                <View style={styles.row}>
                    <Text style={styles.query}>{item.query}</Text>
                    <Text style={styles.secondaryText}>
                        {new Date(item.searchedAt).toLocaleString()}
                    </Text>
                    <ConditionResult condition={item.answer} />
                </View>
            )}
        />
    );
}

const styles = StyleSheet.create({
    center: { flex: 1, alignItems: 'center', justifyContent: 'center', gap: 8 },
    list: { flexGrow: 1, padding: 20 },
    row: { width: '100%', maxWidth: 640, alignSelf: 'center', paddingVertical: 12 },
    empty: { alignSelf: 'center' },
    query: { color: '#18352f', fontSize: 18, fontWeight: '700' },
    secondaryText: { color: '#60746d', fontSize: 13 },
    error: { color: '#a52727', fontSize: 14 },
});