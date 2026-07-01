import {fireEvent, render, screen} from '@testing-library/react';
import MobileNav from './mobileNav';
import {UserContext} from '../UserProvider';

jest.mock('axios', () => ({defaults: {headers: {common: {}}}}));
jest.mock('react-router-dom', () => ({
	NavLink: ({to, children, className, onClick}) => {
		const resolvedClassName =
			typeof className === 'function' ? className({isActive: false}) : className;
		return (
			<a href={to} className={resolvedClassName} onClick={onClick}>
				{children}
			</a>
		);
	},
}), {virtual: true});

const renderMobileNav = (role) => render(
	<UserContext.Provider value={{user: {id: 1, role}}}>
		<MobileNav />
	</UserContext.Provider>,
);

test('admin gets requested primary tabs and remaining links in Mas', () => {
	renderMobileNav('ADMIN');

	expect(screen.getByRole('link', {name: /finanzas/i})).toHaveAttribute('href', '/finance');
	expect(screen.getByRole('link', {name: /costos/i})).toHaveAttribute('href', '/costs');
	expect(screen.getByRole('link', {name: /panel admin/i})).toHaveAttribute('href', '/admin');
	expect(screen.queryByRole('link', {name: /facturas/i})).not.toBeInTheDocument();

	fireEvent.click(screen.getByRole('button', {name: /m.s/i}));

	expect(screen.getByRole('link', {name: /facturas/i})).toHaveAttribute('href', '/dashboard');
	expect(screen.getByRole('link', {name: /clientes/i})).toHaveAttribute('href', '/customers');
	expect(screen.getByRole('link', {name: /perfil/i})).toHaveAttribute('href', '/profile/1');
	expect(screen.queryByRole('link', {name: /operador/i})).not.toBeInTheDocument();
});

test.each([
	['GESTOR', ['Finanzas'], ['Costos', 'Panel Admin', 'Operador'], ['Facturas', 'Clientes', 'Perfil']],
	['SUPER_ADMIN', [], ['Finanzas', 'Costos', 'Panel Admin', 'Facturas', 'Clientes'], ['Operador', 'Perfil']],
])('%s receives only role-appropriate mobile navigation', (role, primaryVisible, hidden, moreVisible) => {
	renderMobileNav(role);

	primaryVisible.forEach((label) => {
		expect(screen.getByRole('link', {name: label})).toBeInTheDocument();
	});
	hidden.forEach((label) => {
		expect(screen.queryByRole('link', {name: label})).not.toBeInTheDocument();
	});

	fireEvent.click(screen.getByRole('button', {name: /m.s/i}));

	moreVisible.forEach((label) => {
		expect(screen.getByRole('link', {name: label})).toBeInTheDocument();
	});
});
