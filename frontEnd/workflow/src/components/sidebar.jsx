// src/components/Sidebar.jsx
import {NavLink} from 'react-router-dom';
import React, {useContext} from 'react';
import {UserContext} from '../UserProvider';

export default function Sidebar() {
	const linkClass = ({isActive}) =>
		`block p-2 rounded ${isActive ? 'bg-amber-200 font-bold' : ''}`;

	const {user} = useContext(UserContext);
	const isAdmin = user?.role === 'ADMIN' || user?.role === 'SUPER_ADMIN';
	const isGestor = user?.role === 'GESTOR';

	return (
		<aside className='sidebar bg-gray-100 p-4 justifyContent'>
			<div>
				<div>
					<NavLink to='/dashboard' className={linkClass}>
						Facturas
					</NavLink>
				</div>
				{(isAdmin || isGestor) && (
					<div>
						<NavLink to='/finance' className={linkClass}>
							Finanzas
						</NavLink>
					</div>
				)}
				{(isAdmin || isGestor) && (
					<div>
						<NavLink to='/customers' className={linkClass}>
							Clientes
						</NavLink>
					</div>
				)}
				{isAdmin && (
					<div>
						<NavLink to='/costs' className={linkClass}>
							Costos
						</NavLink>
					</div>
				)}
			</div>

			{(isAdmin ) && (
				<NavLink className={linkClass} to='/admin'>
					Panel Admin
				</NavLink>
			)}

			<div>
				<NavLink to={`/profile/${user.id}`}>Perfil</NavLink>
			</div>
		</aside>
	);

}
