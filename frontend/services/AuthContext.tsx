import { createContext, useCallback, useContext, useEffect, useRef, useState } from 'react';
import type { ReactNode } from 'react';
import { checkSession, clearLegacySession, logout } from './api';

type AuthStatus = 'checking' | 'signed-in' | 'signed-out' | 'error';

type AuthContextValue = {
    status: AuthStatus;
    error: string;
    email: string | null;
    refreshSession: () => Promise<void>;
    markSignedIn: (email: string) => void;
    signOut: () => Promise<void>;
};

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
    const [status, setStatus] = useState<AuthStatus>('checking');
    const [error, setError] = useState('');
    const [email, setEmail] = useState<string | null>(null);
    const generation = useRef(0);

    const refreshSession = useCallback(async () => {
        const request = ++generation.current;
        setStatus('checking');
        setError('');
        try {
            const account = await checkSession();
            if (request === generation.current) {
                setEmail(account);
                setStatus(account ? 'signed-in' : 'signed-out');
            }
        } catch (sessionError) {
            if (request === generation.current) {
                setEmail(null);
                setError(sessionError instanceof Error ? sessionError.message : 'Could not verify your session.');
                setStatus('error');
            }
        }
    }, []);

    useEffect(() => {
        let active = true;
        const request = ++generation.current;
        clearLegacySession()
            .then(() => {
                if (active && request === generation.current) void refreshSession();
            })
            .catch((sessionError) => {
                if (active && request === generation.current) {
                    setError(sessionError instanceof Error ? sessionError.message : 'Could not clear the previous session.');
                    setStatus('error');
                }
            });
        return () => { active = false; generation.current++; };
    }, [refreshSession]);

    const markSignedIn = useCallback((account: string) => {
        generation.current++;
        setEmail(account);
        setError('');
        setStatus('signed-in');
    }, []);

    const signOut = useCallback(async () => {
        generation.current++;
        setEmail(null);
        setError('');
        setStatus('signed-out');
        await logout();
    }, []);

    return (
        <AuthContext.Provider value={{ status, error, email, refreshSession, markSignedIn, signOut }}>
            {children}
        </AuthContext.Provider>
    );
}

export function useAuth(): AuthContextValue {
    const context = useContext(AuthContext);
    if (!context) throw new Error('useAuth must be used within AuthProvider.');
    return context;
}
