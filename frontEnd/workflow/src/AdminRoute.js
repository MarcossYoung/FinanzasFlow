import React from 'react';
import {Navigate} from 'react-router-dom';
const AdminRoute = ({user, children}) => {
	if (!user) {
		return <Navigate to='/login' replace />;
	}
	if (user.role !== 'ADMIN' && user.role !== 'SUPER_ADMIN') {
		return <p>Access Denied: You must be an admin to view this page.</p>;
	}
	return children;
};
export default AdminRoute;
