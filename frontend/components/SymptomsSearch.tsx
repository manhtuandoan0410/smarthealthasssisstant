import { useCallback, useState } from 'react';
import { useFocusEffect } from '@react-navigation/native';
import {
    ActivityIndicator,
    Pressable,
    ScrollView,
    StyleSheet,
    Text,
    TextInput,
    View,
} from 'react-native';
import ConditionResult from './ConditionResult';
import { isUnauthorizedError, searchSymptoms } from '../services/api';
import { useAuth } from '../services/AuthContext';
import type { Condition } from '../types';

const SYMPTOM_WARNING =
    'Possible matches are for educational information only, not a diagnosis. Contact a healthcare professional for advice.';

// Earlier symptom-search UI remains available to signed-in users, pending a working medical API.
export default function SymptomsSearch() {
    const [symptomText, setSymptomText] = useState('');
    const [possibilities, setPossibilities] = useState<Condition[]>([]);
    const [disclaimer, setDisclaimer] = useState(SYMPTOM_WARNING);
    const [loading, setLoading] = useState(false);
    const [searched, setSearched] = useState(false);
    const [error, setError] = useState('');
    const { status: sessionState, error: sessionError, refreshSession } = useAuth();

    useFocusEffect(useCallback(() => {
        setPossibilities([]);
        setSymptomText('');
        setSearched(false);
        setError('');
        void refreshSession();
    }, [refreshSession]));

    // Validate distinct symptoms before calling the protected, API-dependent backend endpoint.
    async function handleSearch() {
        const symptoms = symptomText
            .split(/[\n,;]/)
            .map((symptom) => symptom.trim())
            .filter(Boolean);
        const uniqueSymptoms = [...new Set(symptoms.map((symptom) => symptom.toLowerCase()))];

        setError('');
        setSearched(true);
        setPossibilities([]);
        if (uniqueSymptoms.length < 2) {
            setError('Enter at least two different symptoms.');
            return;
        }

        setLoading(true);
        try {
            const response = await searchSymptoms(symptoms);
            setPossibilities(response.possibilities);
            setDisclaimer(response.disclaimer || SYMPTOM_WARNING);
        } catch (searchError) {
            setError(searchError instanceof Error ? searchError.message : 'Symptom search failed.');
            if (isUnauthorizedError(searchError)) void refreshSession();
        } finally {
            setLoading(false);
        }
    }

    return (
        <ScrollView contentContainerStyle={styles.content} keyboardShouldPersistTaps="handled">
            <View style={styles.inner}>
                <Text style={styles.title}>Search by symptoms</Text>
                <Text style={styles.warning}>{SYMPTOM_WARNING}</Text>
                {sessionState === 'checking' ? <ActivityIndicator color="#167d68" /> : null}
                {sessionState === 'signed-out' ? <Text style={styles.instructions}>Log in or create an account to search by symptoms.</Text> : null}
                {sessionState === 'error' ? <Text accessibilityRole="alert" style={styles.error}>{sessionError}</Text> : null}
                {sessionState === 'signed-in' ? <>
                <Text style={styles.instructions}>Enter at least two symptoms, separated by commas or new lines.</Text>
                <TextInput
                    accessibilityLabel="Symptoms, at least two"
                    multiline
                    value={symptomText}
                    onChangeText={setSymptomText}
                    placeholder={'For example:\nheadache, nausea'}
                    style={styles.input}
                    textAlignVertical="top"
                />
                <Pressable
                    accessibilityRole="button"
                    disabled={loading}
                    onPress={() => void handleSearch()}
                    style={({ pressed }) => [styles.button, pressed && styles.pressed, loading && styles.disabled]}
                >
                    {loading
                        ? <ActivityIndicator color="#ffffff" />
                        : <Text style={styles.buttonText}>Search possibilities</Text>}
                </Pressable>
                {error ? <Text accessibilityRole="alert" style={styles.error}>{error}</Text> : null}
                {!loading && searched && !error && possibilities.length === 0
                    ? <Text style={styles.instructions}>No possible matches were returned. Seek medical advice if you are concerned.</Text>
                    : null}
                <Text style={styles.warning}>{disclaimer}</Text>
                {possibilities.map((condition, index) => (
                    <ConditionResult key={`${condition.name}-${index}`} condition={condition} />
                ))}
                </> : null}
            </View>
        </ScrollView>
    );
}

const styles = StyleSheet.create({
    content: { flexGrow: 1, alignItems: 'center', padding: 20, paddingTop: 28 },
    inner: { width: '100%', maxWidth: 640, gap: 14 },
    title: { color: '#18352f', fontSize: 24, fontWeight: '700' },
    warning: { color: '#7d361c', fontSize: 14, lineHeight: 20 },
    instructions: { color: '#526b63', fontSize: 14, lineHeight: 20 },
    input: { minHeight: 132, padding: 14, borderWidth: 1, borderColor: '#b8cec6', borderRadius: 8, backgroundColor: '#ffffff', color: '#18352f', fontSize: 16 },
    button: { minHeight: 48, alignItems: 'center', justifyContent: 'center', borderRadius: 8, backgroundColor: '#167d68' },
    buttonText: { color: '#ffffff', fontSize: 16, fontWeight: '700' },
    pressed: { opacity: 0.8 },
    disabled: { opacity: 0.6 },
    error: { color: '#a52727', fontSize: 14 },
});