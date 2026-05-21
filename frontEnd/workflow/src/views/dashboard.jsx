import React, {useEffect, useContext} from 'react';
import axios from 'axios';
import {Outlet} from 'react-router-dom';
import {useInvoices} from '../InvoicesContext';
import {BASE_URL} from '../api/config';
import {UserContext} from '../UserProvider';
import {NavLink} from 'react-router-dom';

export default function Dashboard() {
	const {invoices, setInvoices} = useInvoices();
	const {user} = useContext(UserContext);

	const linkClass = ({isActive}) =>
		isActive ? 'nav-pill active' : 'nav-pill';

	const isAdmin = user?.role === 'ADMIN' || user?.role === 'SUPER_ADMIN';
	const isGestor = user?.role === 'GESTOR';

	useEffect(() => {
		const fetchOrders = async () => {
			try {
				const res = await axios.get(`${BASE_URL}/api/invoices`, {
					headers: {
						Authorization: `Bearer ${localStorage.getItem(
							'token',
						)}`,
					},
				});
				setInvoices(res.data.content || []);
			} catch (err) {
				console.error('Error fetching invoices:', err);
			}
		};
		fetchOrders();
	}, [setInvoices]);

	return (
		<div className='dashboard-layout flex'>
			<main className='dashboard-content w-100 p-3'>
				<nav style={{display: 'flex', gap: '10px'}}>
					{' '}
					{/* Horizontal layout for links */}
					{(isGestor || isAdmin) && (
						<NavLink to='/dashboard' end className={linkClass}>
							Facturas
						</NavLink>
					)}
					<NavLink to='por-vencer' className={linkClass}>
						Por vencer
					</NavLink>
					<NavLink to='vencidas' className={linkClass}>
						Vencidas
					</NavLink>
					<NavLink to='no-cobradas' className={linkClass}>
						Sin cobrar
					</NavLink>
				</nav>

				<div className='tab-content'>
					<Outlet context={invoices} />
				</div>
			</main>
		</div>
	);
}
