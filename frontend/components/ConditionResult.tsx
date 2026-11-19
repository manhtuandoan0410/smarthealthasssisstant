import { Linking, Pressable, StyleSheet, Text, View } from 'react-native';
import type { Condition } from '../types';
import NhsPage from './NhsPage';

type Props = {
    condition: Condition;
    showNhsPage?: boolean;
};

export default function ConditionResult({ condition, showNhsPage = false }: Props) {
    const details = [
        { heading: 'Causes', value: condition.causes },
        { heading: 'Symptoms', value: condition.symptoms },
        { heading: 'Treatment', value: condition.treatment },
    ].filter(({ value }) => value.trim() && value !== 'Information not available in the NHS response.');

    return (
        <View style={styles.results}>
            <Text style={styles.title}>{condition.name}</Text>
            {details.map(({ heading, value }) => (
                <View key={heading}>
                    <Text style={styles.heading}>{heading}</Text>
                    <Text style={styles.body}>{value}</Text>
                </View>
            ))}
            <Text style={styles.disclaimer}>
                Educational information only. This is not a diagnosis or a substitute for advice from a healthcare professional.
            </Text>
            {showNhsPage && condition.sourceUrl ? (
                <NhsPage key={condition.sourceUrl} url={condition.sourceUrl} />
            ) : null}
            {condition.sourceUrl ? (
                <Pressable
                    accessibilityRole="link"
                    onPress={() => void Linking.openURL(condition.sourceUrl!)}
                >
                    <Text style={styles.source}>Open full information on the NHS website</Text>
                </Pressable>
            ) : null}
        </View>
    );
}

const styles = StyleSheet.create({
    results: {
        width: '100%',
        maxWidth: 600,
        marginTop: 24,
        padding: 20,
        borderWidth: 1,
        borderColor: '#dce9e4',
        borderRadius: 12,
        backgroundColor: '#ffffff',
        elevation: 2,
    },
    title: {
        marginBottom: 16,
        color: '#18352f',
        fontSize: 24,
        fontWeight: '700',
    },
    heading: {
        marginTop: 12,
        marginBottom: 4,
        color: '#167d68',
        fontSize: 17,
        fontWeight: '700',
    },
    body: {
        color: '#405b53',
        fontSize: 16,
        lineHeight: 24,
    },
    disclaimer: {
        marginTop: 16,
        color: '#7d361c',
        fontSize: 13,
        lineHeight: 19,
    },
    source: {
        marginTop: 10,
        color: '#167d68',
        fontSize: 14,
        fontWeight: '700',
    },
});