import {fireEvent, render, screen, waitFor} from '@testing-library/react';
import axios from 'axios';
import InvoicesTable from './ordersTable';
import {UserContext} from '../UserProvider';

jest.mock('axios', () => ({get: jest.fn(), delete: jest.fn()}));
jest.mock('react-router-dom', () => ({useNavigate: () => jest.fn()}), {virtual: true});
jest.mock('./invoiceCreationModal', () => ({isOpen, onClose}) =>
	isOpen ? <button onClick={onClose}>Cerrar creación</button> : null,
);

const renderTable = (role, props = {}) => render(
	<UserContext.Provider value={{user: {role}}}>
		<InvoicesTable endpoint='/api/invoices' {...props} />
	</UserContext.Provider>,
);

beforeEach(() => {
	axios.get.mockResolvedValue({data: {content: [], totalPages: 0}});
});

test('main editable list exposes manual creation and refreshes when it closes', async () => {
	renderTable('GESTOR', {allowManualCreate: true});
	const trigger = await screen.findByRole('button', {name: /agregar nuevo/i});
	fireEvent.click(trigger);
	fireEvent.click(screen.getByRole('button', {name: /cerrar creación/i}));
	await waitFor(() => expect(axios.get).toHaveBeenCalledTimes(2));
});

test.each([
	['GESTOR', {}],
	['SUPER_ADMIN', {allowManualCreate: true}],
])('does not expose manual creation when the view or role is read-only', async (role, props) => {
	renderTable(role, props);
	await screen.findByText(/todavía no hay facturas/i);
	expect(screen.queryByRole('button', {name: /agregar nuevo/i})).not.toBeInTheDocument();
});
