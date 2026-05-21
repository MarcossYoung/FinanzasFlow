import React, {useContext} from 'react';
import {Navigate} from 'react-router-dom';
import {UserContext} from '../UserProvider';

const ProtectedRoute = ({children}) => {
	const {user, initialized} = useContext(UserContext);

	if (!initialized) return <p>Loading...</p>;
	if (!user) return <Navigate to='/login' replace />;

	return children;
};

export default ProtectedRoute;
