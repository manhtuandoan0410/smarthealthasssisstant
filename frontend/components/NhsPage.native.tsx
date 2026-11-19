import { ActivityIndicator, StyleSheet, Text, useWindowDimensions, View } from 'react-native';
import { WebView } from 'react-native-webview';

type Props = {
    url: string;
};

// Native app displays the NHS page directly; the backend only supplies its generated URL.
export default function NhsPage({ url }: Props) {
    const { height } = useWindowDimensions();

    return (
        <View style={[styles.container, { height: Math.max(400, height * 0.7) }]}>
            <WebView
                source={{ uri: url }}
                nestedScrollEnabled
                startInLoadingState
                renderLoading={() => (
                    <View style={styles.message}>
                        <ActivityIndicator color="#167d68" />
                        <Text>Loading NHS page...</Text>
                    </View>
                )}
                renderError={() => (
                    <View style={styles.message}>
                        <Text accessibilityRole="alert">Could not load the NHS page. Check your internet connection.</Text>
                    </View>
                )}
            />
        </View>
    );
}

const styles = StyleSheet.create({
    container: { width: '100%', marginTop: 16, overflow: 'hidden', borderRadius: 8 },
    message: { flex: 1, alignItems: 'center', justifyContent: 'center', gap: 8, padding: 16 },
});
