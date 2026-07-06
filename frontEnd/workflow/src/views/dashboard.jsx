import React, {useContext} from 'react';
import {Outlet} from 'react-router-dom';
import {UserContext} from '../UserProvider';
import {NavLink} from 'react-router-dom';

export default function Dashboard() {
	const {user} = useContext(UserContext);

	const linkClass = ({isActive}) =>
		isActive ? 'nav-pill active' : 'nav-pill';

	const isAdmin = user?.role === 'ADMIN';
	const isGestor = user?.role === 'GESTOR';

	return (
		<div className='dashboard-layout'>
			<main className='dashboard-content w-100'>
				<nav>
					{(isGestor || isAdmin) && (
						<NavLink to='/dashboard' end className={linkClass}>
							Facturas
						</NavLink>
					)}
					<NavLink to='no-cobradas' className={linkClass}>
						Sin cobrar
					</NavLink>
				</nav>

				<div className='tab-content'>
					<Outlet />
				</div>
			</main>
		</div>
	);
}
