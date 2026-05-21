import React from 'react';
import ReactDOM from 'react-dom/client';
import UserProvider from './UserProvider';
import App from './App.js';
import './css/styles.css';

const rootElement = document.getElementById('root');
const root = ReactDOM.createRoot(rootElement);

root.render(
	<React.StrictMode>
		<UserProvider>
			<App />
		</UserProvider>
	</React.StrictMode>
);
