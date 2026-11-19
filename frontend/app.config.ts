import type { ExpoConfig, ConfigContext } from 'expo/config';

export default ({ config }: ConfigContext): ExpoConfig => {
    const mapPluginOptions: Record<string, string> = {};
    const androidMapsKey = process.env.GOOGLE_MAPS_ANDROID_API_KEY;
    const iosMapsKey = process.env.GOOGLE_MAPS_IOS_API_KEY;

    if (androidMapsKey) mapPluginOptions.androidGoogleMapsApiKey = androidMapsKey;
    if (iosMapsKey) mapPluginOptions.iosGoogleMapsApiKey = iosMapsKey;

    return {
        ...config,
        plugins: [
            ...(config.plugins ?? []),
            [
                'expo-location',
                {
                    locationWhenInUsePermission:
                        'Allow Smart Health Assistant to find nearby hospitals and pharmacies.',
                },
            ],
            ['react-native-maps', mapPluginOptions],
        ],
    };
};