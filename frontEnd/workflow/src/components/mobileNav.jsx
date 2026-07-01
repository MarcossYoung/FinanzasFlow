import React, {useContext, useState} from 'react';
import {NavLink} from 'react-router-dom';
import {
	FaChartLine,
	FaCog,
	FaEllipsisH,
	FaFileInvoice,
	FaTachometerAlt,
	FaUser,
	FaUsers,
} from 'react-icons/fa';
import {UserContext} from '../UserProvider';

export default function MobileNav() {
	const {user} = useContext(UserContext);
	const [isMoreOpen, setIsMoreOpen] = useState(false);
	const isAdmin = user?.role === 'ADMIN';
	const isSuperAdmin = user?.role === 'SUPER_ADMIN';
	const isGestor = user?.role === 'GESTOR';
	const isClientRole = isAdmin || isGestor;

	if (!user) return null;

	const linkClass = ({isActive}) =>
		isActive ? 'mobile-tab active' : 'mobile-tab';

	const closeMore = () => setIsMoreOpen(false);
	const primaryLinks = [
		isClientRole && {
			to: '/finance',
			label: 'Finanzas',
			icon: <FaChartLine aria-hidden='true' />,
		},
		isAdmin && {
			to: '/costs',
			label: 'Costos',
			icon: <FaCog aria-hidden='true' />,
		},
		isAdmin && {
			to: '/admin',
			label: 'Panel Admin',
			icon: <FaTachometerAlt aria-hidden='true' />,
		},
	].filter(Boolean);

	const moreLinks = [
		isClientRole && {
			to: '/dashboard',
			label: 'Facturas',
			icon: <FaFileInvoice aria-hidden='true' />,
		},
		isClientRole && {
			to: '/customers',
			label: 'Clientes',
			icon: <FaUsers aria-hidden='true' />,
		},
		isSuperAdmin && {
			to: '/operator',
			label: 'Operador',
			icon: <FaTachometerAlt aria-hidden='true' />,
		},
		{
			to: `/profile/${user.id}`,
			label: 'Perfil',
			icon: <FaUser aria-hidden='true' />,
		},
	].filter(Boolean);

	return (
		<>
			{isMoreOpen && (
				<button
					type='button'
					className='mobile-more-backdrop'
					aria-label='Cerrar menu movil'
					onClick={closeMore}
				/>
			)}
			{isMoreOpen && (
				<div className='mobile-more-sheet' id='mobile-more-menu'>
					{moreLinks.map((link) => (
						<NavLink
							key={link.to}
							to={link.to}
							className={({isActive}) =>
								isActive ? 'mobile-more-link active' : 'mobile-more-link'
							}
							onClick={closeMore}
						>
							{link.icon}
							<span>{link.label}</span>
						</NavLink>
					))}
				</div>
			)}
			<nav className='mobile-tabbar' aria-label='Navegacion movil'>
				{primaryLinks.map((link) => (
					<NavLink key={link.to} to={link.to} className={linkClass}>
						{link.icon}
						<span>{link.label}</span>
					</NavLink>
				))}
				{moreLinks.length > 0 && (
					<button
						type='button'
						className={isMoreOpen ? 'mobile-tab active' : 'mobile-tab'}
						aria-expanded={isMoreOpen}
						aria-controls='mobile-more-menu'
						onClick={() => setIsMoreOpen((open) => !open)}
					>
						<FaEllipsisH aria-hidden='true' />
						<span>Más</span>
					</button>
				)}
			</nav>
		</>
	);
}
