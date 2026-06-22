import React from 'react';
import {Navigate} from 'react-router-dom';

export const defaultRouteFor = (user) => {
	if (user?.role === 'SUPER_ADMIN') return '/operator';
	if (user?.role === 'ADMIN' || user?.role === 'GESTOR') return '/finance';
	return '/login';
};

const RoleRoute = ({user, allowedRoles, children}) => {
	if (!user) return <Navigate to='/login' replace />;
	if (!allowedRoles.includes(user.role))
		return <Navigate to={defaultRouteFor(user)} replace />;
	return children;
};

export default RoleRoute;
