import { StatusBar, StyleSheet } from 'react-native';
import { createStaticNavigation } from '@react-navigation/native';
import { createNativeStackNavigator } from '@react-navigation/native-stack';
import type { NativeStackHeaderProps } from '@react-navigation/native-stack';
import { SafeAreaProvider, SafeAreaView } from 'react-native-safe-area-context';

import ButtonAppBar from './components/ButtonAppBar';
import Login from './components/Login';
import Mainpage from './components/Mainpage';
import NearbyCare from './components/NearbyCare';
import SearchHistory from './components/SearchHistory';
import SymptomsSearch from './components/SymptomsSearch';
import { AuthProvider } from './services/AuthContext';

function AppHeader({ navigation }: NativeStackHeaderProps) {
	return (
		<SafeAreaView edges={['top']} style={styles.headerSafeArea}>
			<ButtonAppBar
				onMainpagePress={() => navigation.reset({ index: 0, routes: [{ name: 'Home' }] })}
				onSearchHistoryPress={() => navigation.navigate('History')}
				onSymptomsPress={() => navigation.navigate('Symptoms')}
				onNearbyCarePress={() => navigation.navigate('NearbyCare')}
				onLoginPress={() => navigation.navigate('Login')}
			/>
		</SafeAreaView>
	);
}

const RootStack = createNativeStackNavigator({
	screenOptions: { header: (props) => <AppHeader {...props} /> },
	screens: {
		Home: Mainpage,
		History: SearchHistory,
		Symptoms: SymptomsSearch,
		NearbyCare,
		Login,
	},
});

const Navigation = createStaticNavigation(RootStack);

export default function App() {
	return (
		<SafeAreaProvider>
			<AuthProvider>
				<StatusBar barStyle="light-content" />
				<SafeAreaView style={styles.screenSafeArea} edges={['left', 'right', 'bottom']}>
					<Navigation />
				</SafeAreaView>
			</AuthProvider>
		</SafeAreaProvider>
	);
}

const styles = StyleSheet.create({
	screenSafeArea: {
		flex: 1,
	},
	headerSafeArea: {
		backgroundColor: '#167d68',
	},
});
