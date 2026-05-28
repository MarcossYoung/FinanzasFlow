// src/components/Sidebar.jsx
import {NavLink} from 'react-router-dom';
import React, {useContext} from 'react';
import {UserContext} from '../UserProvider';

export default function Sidebar() {
	const linkClass = ({isActive}) =>
		isActive ? 'sidebar-link active' : 'sidebar-link';

	const {user} = useContext(UserContext);
	const isAdmin = user?.role === 'ADMIN' || user?.role === 'SUPER_ADMIN';
	const isSuperAdmin = user?.role === 'SUPER_ADMIN';
	const isGestor = user?.role === 'GESTOR';

	return (
		<aside className='sidebar'>
			<div className='sidebar-brand'>FinanzasFlow</div>

			<nav className='sidebar-nav'>
				{(isAdmin || isGestor) && (
					<NavLink to='/finance' className={linkClass}>
						Finanzas
					</NavLink>
				)}
				<NavLink to='/dashboard' className={linkClass}>
					Facturas
				</NavLink>
				{(isAdmin || isGestor) && (
					<NavLink to='/customers' className={linkClass}>
						Clientes
					</NavLink>
				)}
				{isAdmin && (
					<NavLink to='/costs' className={linkClass}>
						Costos
					</NavLink>
				)}
				{isAdmin && (
					<NavLink className={linkClass} to='/admin'>
						Panel Admin
					</NavLink>
				)}
				{isSuperAdmin && (
					<NavLink className={linkClass} to='/operator'>
						Operador
					</NavLink>
				)}
			</nav>

			<div className='sidebar-footer'>
				<NavLink to={`/profile/${user.id}`} className={linkClass}>
					Perfil
				</NavLink>
			</div>
		</aside>
	);
}
