import {render, screen} from '@testing-library/react';
import Sidebar from './sidebar';
import {UserContext} from '../UserProvider';

jest.mock('axios', () => ({defaults: {headers: {common: {}}}}));
jest.mock('react-router-dom', () => ({
	NavLink: ({to, children}) => <a href={to}>{children}</a>,
}), {virtual: true});

const renderSidebar = (role) => render(
	<UserContext.Provider value={{user: {id: 1, role}}}>
		<Sidebar />
	</UserContext.Provider>,
);

test.each([
	['GESTOR', ['Finanzas', 'Facturas', 'Clientes'], ['Costos', 'Panel Admin', 'Operador']],
	['ADMIN', ['Finanzas', 'Facturas', 'Clientes', 'Costos', 'Panel Admin'], ['Operador']],
	['SUPER_ADMIN', ['Operador', 'Perfil'], ['Finanzas', 'Facturas', 'Clientes', 'Costos', 'Panel Admin']],
])('%s receives only its valid navigation', (role, visible, hidden) => {
	renderSidebar(role);
	visible.forEach((label) => expect(screen.getByRole('link', {name: label})).toBeInTheDocument());
	hidden.forEach((label) => expect(screen.queryByRole('link', {name: label})).not.toBeInTheDocument());
});
