import { useEffect, useRef, useState } from 'react';
import { ActivityIndicator, Linking, Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import MapView, { Marker, type Region } from 'react-native-maps';
import * as Location from 'expo-location';
import { searchNearbyCare } from '../services/api';
import type { NearbyCare as NearbyCarePlace, NearbyCareCategory } from '../types';

export default function NearbyCare() {
	const [category, setCategory] = useState<NearbyCareCategory>('hospital');
	const [region, setRegion] = useState<Region | null>(null);
	const [places, setPlaces] = useState<NearbyCarePlace[]>([]);
	const [loading, setLoading] = useState(false);
	const [error, setError] = useState('');
	const [mapReady, setMapReady] = useState(false);
	const mapRef = useRef<MapView>(null);

	useEffect(() => {
		if (!mapReady || !region) return;
		if (places.length === 0) {
			mapRef.current?.animateToRegion(region);
			return;
		}
		mapRef.current?.fitToCoordinates(
			[{ latitude: region.latitude, longitude: region.longitude },
				...places.map(({ latitude, longitude }) => ({ latitude, longitude }))],
			{ edgePadding: { top: 45, right: 45, bottom: 45, left: 45 }, animated: true }
		);
	}, [mapReady, region, places]);

	async function findCare(nextCategory = category) {
		setError('');
		setPlaces([]);
		setLoading(true);
		try {
			const permission = await Location.requestForegroundPermissionsAsync();
			if (permission.status !== 'granted') {
				setError('Location permission is needed to find nearby care.');
				return;
			}
			const position = await Location.getCurrentPositionAsync({
				accuracy: Location.Accuracy.Balanced,
			});
			const currentRegion = {
				latitude: position.coords.latitude,
				longitude: position.coords.longitude,
				latitudeDelta: 0.1,
				longitudeDelta: 0.1,
			};
			setRegion(currentRegion);
			setPlaces(await searchNearbyCare(currentRegion.latitude, currentRegion.longitude, nextCategory));
		} catch (searchError) {
			setError(searchError instanceof Error ? searchError.message : 'Nearby search failed.');
		} finally {
			setLoading(false);
		}
	}

	function chooseCategory(nextCategory: NearbyCareCategory) {
		setCategory(nextCategory);
		if (region) void findCare(nextCategory);
	}

	return (
		<ScrollView contentContainerStyle={styles.content}>
			<View style={styles.inner}>
				<Text style={styles.title}>Nearby care</Text>
				<Text style={styles.disclaimer}>
					Choose hospitals or pharmacies within 5 km, then allow location access. Your coordinates are sent to the configured care-search service for this request only.
				</Text>

				<View style={styles.categoryRow}>
					<Pressable
						accessibilityRole="button"
						disabled={loading}
						onPress={() => chooseCategory('hospital')}
						style={[styles.categoryButton, category === 'hospital' && styles.categorySelected]}
					>
						<Text style={[styles.categoryText, category === 'hospital' && styles.categoryTextSelected]}>Hospitals</Text>
					</Pressable>
					<Pressable
						accessibilityRole="button"
						disabled={loading}
						onPress={() => chooseCategory('pharmacy')}
						style={[styles.categoryButton, category === 'pharmacy' && styles.categorySelected]}
					>
						<Text style={[styles.categoryText, category === 'pharmacy' && styles.categoryTextSelected]}>Pharmacies</Text>
					</Pressable>
				</View>

				<Pressable
					accessibilityRole="button"
					disabled={loading}
					onPress={() => void findCare()}
					style={({ pressed }) => [styles.searchButton, pressed && styles.pressed, loading && styles.disabled]}
				>
					{loading
						? <ActivityIndicator color="#ffffff" />
						: <Text style={styles.searchButtonText}>{region ? 'Refresh nearby results' : 'Use my location'}</Text>}
				</Pressable>

				{error ? <Text accessibilityRole="alert" style={styles.error}>{error}</Text> : null}

				{region && (
					<MapView ref={mapRef} style={styles.map} initialRegion={region} showsUserLocation onMapReady={() => setMapReady(true)}>
						{places.map((place) => (
							<Marker
								key={place.id}
								coordinate={{ latitude: place.latitude, longitude: place.longitude }}
								title={place.name}
								description={place.address}
							/>
						))}
					</MapView>
				)}

				{region && places.length === 0 && !loading && !error ? (
					<Text style={styles.empty}>No {category === 'hospital' ? 'hospitals' : 'pharmacies'} found within 5 km.</Text>
				) : null}

				{places.map((place) => (
					<View key={place.id} style={styles.placeRow}>
						<Text style={styles.placeName}>{place.name}</Text>
						<Text style={styles.placeDetail}>{place.address}</Text>
						{place.rating != null ? <Text style={styles.placeDetail}>Rating: {place.rating.toFixed(1)}</Text> : null}
						<View style={styles.links}>
							{place.phoneNumber ? (
								<Pressable onPress={() => void Linking.openURL(`tel:${place.phoneNumber}`)}>
									<Text style={styles.link}>Call {place.phoneNumber}</Text>
								</Pressable>
							) : null}
							{place.mapUrl ? (
								<Pressable onPress={() => void Linking.openURL(place.mapUrl!)}>
									<Text style={styles.link}>Open in Maps</Text>
								</Pressable>
							) : null}
						</View>
					</View>
				))}
			</View>
		</ScrollView>
	);
}

const styles = StyleSheet.create({
	content: { flexGrow: 1, alignItems: 'center', padding: 20 },
	inner: { width: '100%', maxWidth: 720, gap: 14 },
	title: { color: '#18352f', fontSize: 24, fontWeight: '700' },
	disclaimer: { color: '#526b63', fontSize: 14, lineHeight: 20 },
	categoryRow: { flexDirection: 'row', gap: 8 },
	categoryButton: { flex: 1, alignItems: 'center', paddingVertical: 11, borderRadius: 8, backgroundColor: '#eaf2ef' },
	categorySelected: { backgroundColor: '#167d68' },
	categoryText: { color: '#36544c', fontSize: 15, fontWeight: '600' },
	categoryTextSelected: { color: '#ffffff' },
	searchButton: { minHeight: 48, alignItems: 'center', justifyContent: 'center', paddingHorizontal: 16, borderRadius: 8, backgroundColor: '#167d68' },
	searchButtonText: { color: '#ffffff', fontSize: 15, fontWeight: '700' },
	pressed: { opacity: 0.8 },
	disabled: { opacity: 0.6 },
	error: { color: '#a52727', fontSize: 14 },
	map: { width: '100%', height: 340, borderRadius: 8 },
	empty: { color: '#60746d', fontSize: 14 },
	placeRow: { paddingVertical: 12 },
	placeName: { color: '#18352f', fontSize: 17, fontWeight: '700' },
	placeDetail: { marginTop: 4, color: '#526b63', fontSize: 14 },
	links: { flexDirection: 'row', gap: 18, marginTop: 8 },
	link: { color: '#167d68', fontSize: 14, fontWeight: '600' },
});
