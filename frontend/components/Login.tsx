

import { useState } from 'react';
import { CommonActions, useNavigation } from '@react-navigation/native';
import {
    ActivityIndicator,
    Pressable,
    ScrollView,
    StyleSheet,
    Text,
    TextInput,
    View,
} from 'react-native';
import { login, requestPasswordReset, resetPassword, signup } from '../services/api';
import { useAuth } from '../services/AuthContext';

type LoginMode = 'login' | 'signup' | 'forgot' | 'reset';

export default function Login() {
    const navigation = useNavigation();
    const { markSignedIn } = useAuth();
    const [mode, setMode] = useState<LoginMode>('login');
    const [email, setEmail] = useState('');
    const [password, setPassword] = useState('');
    const [confirmPassword, setConfirmPassword] = useState('');
    const [resetCode, setResetCode] = useState('');
    const [firstName, setFirstName] = useState('');
    const [lastName, setLastName] = useState('');
    const [message, setMessage] = useState('');
    const [error, setError] = useState('');
    const [busy, setBusy] = useState(false);

    async function handleSubmit() {
        setMessage('');
        setError('');

        if ((mode === 'signup' || mode === 'reset') && password !== confirmPassword) {
            setError('The passwords do not match.');
            return;
        }

        setBusy(true);
        try {
            if (mode === 'signup') {
                const account = await signup({ email: email.trim(), password, firstName: firstName.trim(), lastName: lastName.trim() });
                markSignedIn(account);
                navigation.dispatch(CommonActions.reset({ index: 0, routes: [{ name: 'Home' }] }));
            } else if (mode === 'login') {
                const account = await login({ email: email.trim(), password });
                markSignedIn(account);
                navigation.dispatch(CommonActions.reset({ index: 0, routes: [{ name: 'Home' }] }));
            } else if (mode === 'forgot') {
                setMessage(await requestPasswordReset(email.trim()));
                setMode('reset');
            } else {
                setMessage(await resetPassword(email.trim(), resetCode.trim(), password));
                setPassword('');
                setConfirmPassword('');
                setResetCode('');
                setMode('login');
            }
        } catch (requestError) {
            setError(requestError instanceof Error ? requestError.message : 'Something went wrong.');
        } finally {
            setBusy(false);
        }
    }

    const title = mode === 'signup'
        ? 'Create account'
        : mode === 'forgot'
            ? 'Reset password'
            : mode === 'reset'
                ? 'Choose a new password'
                : 'Log in';

    return (
        <ScrollView contentContainerStyle={styles.content} keyboardShouldPersistTaps="handled">
            <View style={styles.form}>
                <Text style={styles.title}>{title}</Text>

                {(mode === 'login' || mode === 'signup') && (
                    <View style={styles.modeRow}>
                        <Pressable
                            accessibilityRole="button"
                            onPress={() => { setMode('login'); setMessage(''); setError(''); }}
                            style={[styles.modeButton, mode === 'login' && styles.modeButtonActive]}
                        >
                            <Text style={[styles.modeText, mode === 'login' && styles.modeTextActive]}>Log in</Text>
                        </Pressable>
                        <Pressable
                            accessibilityRole="button"
                            onPress={() => { setMode('signup'); setMessage(''); setError(''); }}
                            style={[styles.modeButton, mode === 'signup' && styles.modeButtonActive]}
                        >
                            <Text style={[styles.modeText, mode === 'signup' && styles.modeTextActive]}>Sign up</Text>
                        </Pressable>
                    </View>
                )}

                {mode === 'signup' && (
                    <>
                        <TextInput style={styles.input} placeholder="First name" value={firstName} onChangeText={setFirstName} autoCapitalize="words" />
                        <TextInput style={styles.input} placeholder="Last name" value={lastName} onChangeText={setLastName} autoCapitalize="words" />
                    </>
                )}

                <TextInput
                    style={styles.input}
                    placeholder="Email address"
                    value={email}
                    onChangeText={setEmail}
                    keyboardType="email-address"
                    autoCapitalize="none"
                    autoComplete="email"
                />

                {mode === 'reset' && (
                    <TextInput
                        style={styles.input}
                        placeholder="Reset code from your email"
                        value={resetCode}
                        onChangeText={setResetCode}
                        autoCapitalize="none"
                    />
                )}

                {(mode === 'login' || mode === 'signup' || mode === 'reset') && (
                    <>
                        <TextInput
                            style={styles.input}
                            placeholder={mode === 'login' ? 'Password' : 'New password (12+ characters)'}
                            value={password}
                            onChangeText={setPassword}
                            secureTextEntry
                            autoCapitalize="none"
                        />
                        {(mode === 'signup' || mode === 'reset') && (
                            <TextInput
                                style={styles.input}
                                placeholder="Confirm password"
                                value={confirmPassword}
                                onChangeText={setConfirmPassword}
                                secureTextEntry
                                autoCapitalize="none"
                            />
                        )}
                    </>
                )}

                {error ? <Text accessibilityRole="alert" style={styles.error}>{error}</Text> : null}
                {message ? <Text accessibilityRole="text" style={styles.message}>{message}</Text> : null}

                <Pressable
                    accessibilityRole="button"
                    disabled={busy}
                    onPress={handleSubmit}
                    style={({ pressed }) => [styles.submitButton, pressed && styles.pressed, busy && styles.disabled]}
                >
                    {busy
                        ? <ActivityIndicator color="#ffffff" />
                        : <Text style={styles.submitText}>{mode === 'signup' ? 'Create account' : mode === 'login' ? 'Log in' : mode === 'forgot' ? 'Send reset code' : 'Update password'}</Text>}
                </Pressable>

                {mode === 'login' && (
                    <Pressable onPress={() => { setMode('forgot'); setMessage(''); setError(''); }}>
                        <Text style={styles.link}>Forgot password?</Text>
                    </Pressable>
                )}
                {(mode === 'reset' || mode === 'forgot') && (
                    <Pressable onPress={() => { setMode('login'); setMessage(''); setError(''); }}>
                        <Text style={styles.link}>Back to log in</Text>
                    </Pressable>
                )}

                {mode === 'signup' && (
                    <Text style={styles.note}>Passwords are sent to the backend over the configured API connection and stored there as BCrypt hashes.</Text>
                )}
            </View>
        </ScrollView>
    );
}

const styles = StyleSheet.create({
    content: { flexGrow: 1, alignItems: 'center', padding: 20, paddingTop: 32 },
    form: { width: '100%', maxWidth: 480, gap: 14 },
    title: { color: '#18352f', fontSize: 24, fontWeight: '700' },
    modeRow: { flexDirection: 'row', backgroundColor: '#eaf2ef', borderRadius: 8, padding: 4 },
    modeButton: { flex: 1, alignItems: 'center', paddingVertical: 10, borderRadius: 6 },
    modeButtonActive: { backgroundColor: '#167d68' },
    modeText: { color: '#36544c', fontSize: 15, fontWeight: '600' },
    modeTextActive: { color: '#ffffff' },
    input: { minHeight: 50, paddingHorizontal: 14, borderWidth: 1, borderColor: '#b8cec6', borderRadius: 8, backgroundColor: '#ffffff', color: '#18352f', fontSize: 16 },
    submitButton: { minHeight: 48, alignItems: 'center', justifyContent: 'center', borderRadius: 8, backgroundColor: '#167d68' },
    submitText: { color: '#ffffff', fontSize: 16, fontWeight: '700' },
    pressed: { opacity: 0.8 },
    disabled: { opacity: 0.6 },
    error: { color: '#a52727', fontSize: 14 },
    message: { color: '#17634f', fontSize: 14 },
    link: { alignSelf: 'center', color: '#167d68', fontSize: 15, fontWeight: '600', paddingVertical: 6 },
    note: { color: '#60746d', fontSize: 13, lineHeight: 19 },
});