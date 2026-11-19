import { useState } from 'react';
import { Alert, Pressable, StyleSheet, Text, View } from 'react-native';
import { useAuth } from '../services/AuthContext';

type Props = {
    onSearchHistoryPress?: () => void;
    onMainpagePress?: () => void;
    onSymptomsPress?: () => void;
    onNearbyCarePress?: () => void;
    onLoginPress?: () => void;
};

export default function ButtonAppBar({ 
        onSearchHistoryPress, 
        onMainpagePress,
        onSymptomsPress,
        onNearbyCarePress,
        onLoginPress,
    }: Props) {
    const [menuOpen, setMenuOpen] = useState(false);
    const [loggingOut, setLoggingOut] = useState(false);
    const { status, error, signOut } = useAuth();

    async function handleAuthPress() {
        if (status !== 'signed-in') {
            onLoginPress?.();
            return;
        }
        setLoggingOut(true);
        setMenuOpen(false);
        try {
            await signOut();
            onMainpagePress?.();
        } catch (logoutError) {
            onMainpagePress?.();
            Alert.alert('Logout issue', logoutError instanceof Error ? logoutError.message : 'Could not log out.');
        } finally {
            setLoggingOut(false);
        }
    }

    return (
        <View>
            <View style={styles.bar}>
                <Pressable
                    accessibilityRole="button"
                    accessibilityLabel={menuOpen ? 'Close menu' : 'Open menu'}
                    onPress={() => setMenuOpen((open) => !open)}
                >
                    <Text style={styles.menu}>{menuOpen ? '×' : '☰'}</Text>
                </Pressable>
                <Text style={styles.title}>Smart Health Assistant</Text>
                <Pressable accessibilityRole="button" disabled={status === 'checking' || loggingOut} onPress={() => void handleAuthPress()}>
                    <Text style={styles.login}>{status === 'checking' ? 'Checking...' : status === 'signed-in' ? 'Log out' : 'Log in'}</Text>
                </Pressable>
            </View>
            {status === 'error' ? <Text accessibilityRole="alert" style={styles.sessionError}>{error}</Text> : null}
            {menuOpen && (
                <View style={styles.menuPanel}>
                    <Pressable style={styles.menuItem} onPress={() => { setMenuOpen(false); onMainpagePress?.(); }}>
                        <Text style={styles.menuItemText}>Home</Text>
                    </Pressable>
                    {status === 'signed-in' ? (
                        <Pressable style={styles.menuItem} onPress={() => { setMenuOpen(false); onSearchHistoryPress?.(); }}>
                            <Text style={styles.menuItemText}>Search History</Text>
                        </Pressable>
                    ) : null}
                    {status === 'signed-in' ? (
                        <Pressable style={styles.menuItem} onPress={() => { setMenuOpen(false); onSymptomsPress?.(); }}>
                            <Text style={styles.menuItemText}>Search Symptoms</Text>
                        </Pressable>
                    ) : null}
                    <Pressable style={styles.menuItem} onPress={() => { setMenuOpen(false); onNearbyCarePress?.(); }}>
                        <Text style={styles.menuItemText}>Nearby Care</Text>
                    </Pressable>
                </View>
            )}
        </View>
    );
}

const styles = StyleSheet.create({
    bar: {
        minHeight: 60,
        paddingHorizontal: 16,
        flexDirection: 'row',
        alignItems: 'center',
        backgroundColor: '#167d68',
        elevation: 3,
    },
    menu: {
        marginRight: 16,
        color: '#ffffff',
        fontSize: 24,
    },
    title: {
        flex: 1,
        color: '#ffffff',
        fontSize: 18,
        fontWeight: '700',
    },
    login: {
        color: '#ffffff',
        fontSize: 15,
        fontWeight: '600',
    },
    sessionError: {
        paddingHorizontal: 16,
        paddingBottom: 8,
        color: '#ffffff',
    },
    menuPanel: {
        paddingHorizontal: 16,
        paddingBottom: 8,
        backgroundColor: '#167d68',
        elevation: 3,
    },
    menuItem: {
        minHeight: 40,
        justifyContent: 'center',
    },
    menuItemText: {
        color: '#ffffff',
        fontSize: 15,
        fontWeight: '600',
    },
});