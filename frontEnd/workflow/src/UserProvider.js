import React, {createContext, useState, useEffect} from 'react';
import axios from 'axios';

export const UserContext = createContext();

const UserProvider = ({children}) => {
	const [user, setUser] = useState(null);
	const [initialized, setInitialized] = useState(false);

	useEffect(() => {
		const initUser = async () => {
			const storedUser = localStorage.getItem('user');
			if (storedUser) {
				const parsedUser = JSON.parse(storedUser);
				setUser(parsedUser);
				axios.defaults.headers.common[
					'Authorization'
				] = `Bearer ${parsedUser.token}`;
			}
			setInitialized(true);
		};

		initUser();
	}, []);

	return (
		<UserContext.Provider value={{user, setUser, initialized}}>
			{children}
		</UserContext.Provider>
	);
};

export default UserProvider;
