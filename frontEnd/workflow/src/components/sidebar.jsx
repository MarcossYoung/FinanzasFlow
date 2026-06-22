// src/components/Sidebar.jsx
import {NavLink} from 'react-router-dom';
import React, {useContext} from 'react';
import {UserContext} from '../UserProvider';

export default function Sidebar() {
	const linkClass = ({isActive}) =>
		isActive ? 'sidebar-link active' : 'sidebar-link';

	const {user} = useContext(UserContext);
	const isAdmin = user?.role === 'ADMIN';
	const isSuperAdmin = user?.role === 'SUPER_ADMIN';
	const isGestor = user?.role === 'GESTOR';
	const isClientRole = isAdmin || isGestor;

	return (
		<aside className='sidebar'>
			<div className='sidebar-brand'>FinanzasFlow</div>

			<nav className='sidebar-nav' aria-label='Navegacion principal'>
				{isClientRole && (
					<div className='sidebar-group'>
						<span className='sidebar-group-label'>Principal</span>
					<NavLink to='/finance' className={linkClass}>
						Finanzas
					</NavLink>
					<NavLink to='/dashboard' className={linkClass}>
					Facturas
					</NavLink>
					<NavLink to='/customers' className={linkClass}>
						Clientes
					</NavLink>
					</div>
				)}
				{isAdmin && (
					<div className='sidebar-group sidebar-group-secondary'>
						<span className='sidebar-group-label'>Gestion</span>
						<NavLink to='/costs' className={linkClass}>Costos</NavLink>
						<NavLink className={linkClass} to='/admin'>Panel Admin</NavLink>
					</div>
				)}
				{isSuperAdmin && (
					<div className='sidebar-group'>
						<span className='sidebar-group-label'>Operacion</span>
						<NavLink className={linkClass} to='/operator'>Operador</NavLink>
					</div>
				)}
			</nav>

			<div className='sidebar-footer'>
				<NavLink to={`/profile/${user?.id}`} className={linkClass}>
					Perfil
				</NavLink>
			</div>
		</aside>
	);
}
