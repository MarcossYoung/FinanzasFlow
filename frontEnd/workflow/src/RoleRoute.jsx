import React from 'react';
import {Navigate} from 'react-router-dom';

const RoleRoute = ({user, allowedRoles, children}) => {
	if (!user) return <Navigate to='/login' replace />;
	const effectiveRoles =
		user.role === 'SUPER_ADMIN' ? [...allowedRoles, 'SUPER_ADMIN', 'ADMIN'] : allowedRoles;
	if (!effectiveRoles.includes(user.role) && user.role !== 'SUPER_ADMIN')
		return <Navigate to='/dashboard' replace />;
	return children;
};

export default RoleRoute;
