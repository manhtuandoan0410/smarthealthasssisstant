import { Text } from 'react-native';

type Props = {
    url: string;
};

// Browser fallback: NHS pages cannot be embedded here; ConditionResult provides the external link.
export default function NhsPage(_props: Props) {
    return <Text>The NHS page cannot be embedded in a web browser. Use the link below to open it.</Text>;
}
