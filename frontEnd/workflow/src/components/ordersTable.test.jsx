import {fireEvent, render, screen, waitFor} from '@testing-library/react';
import axios from 'axios';
import InvoicesTable from './ordersTable';
import {UserContext} from '../UserProvider';

jest.mock('axios', () => ({get: jest.fn(), delete: jest.fn(), put: jest.fn()}));
jest.mock('react-router-dom', () => ({useNavigate: () => jest.fn()}), {virtual: true});
jest.mock('./invoiceCreationModal', () => ({isOpen, onClose}) =>
	isOpen ? <button onClick={onClose}>Cerrar creacion</button> : null,
);

const renderTable = (role, props = {}) => render(
	<UserContext.Provider value={{user: {role}}}>
		<InvoicesTable endpoint='/api/invoices' {...props} />
	</UserContext.Provider>,
);

beforeEach(() => {
	jest.clearAllMocks();
	axios.get.mockResolvedValue({data: {content: [], totalPages: 0}});
});

test('main editable list exposes manual creation and refreshes when it closes', async () => {
	renderTable('GESTOR', {allowManualCreate: true});
	const trigger = await screen.findByRole('button', {name: /agregar nuevo/i});
	fireEvent.click(trigger);
	fireEvent.click(screen.getByRole('button', {name: /cerrar creacion/i}));
	await waitFor(() => expect(axios.get).toHaveBeenCalledTimes(2));
});

test.each([
	['GESTOR', {}],
	['SUPER_ADMIN', {allowManualCreate: true}],
])('does not expose manual creation when the view or role is read-only', async (role, props) => {
	renderTable(role, props);
	await screen.findByText(/todav/i);
	expect(screen.queryByRole('button', {name: /agregar nuevo/i})).not.toBeInTheDocument();
});

test('invoice cells include labels for the mobile card layout', async () => {
	axios.get.mockResolvedValueOnce({
		data: {
			content: [{
				id: 42,
				titulo: 'Factura demo',
				customerName: 'Cliente demo',
				cantidad: 2,
				startDate: '2026-07-01',
				fechaEntrega: '2026-07-10',
				workOrderStatus: 'EN_GESTION',
				precio: 1000,
				totalPaid: 250,
			}],
			totalPages: 1,
		},
	});

	renderTable('ADMIN');

	expect((await screen.findByText('Factura demo')).closest('td')).toHaveAttribute('data-label', 'Titulo');
	expect(screen.getByText('Cliente demo')).toHaveAttribute('data-label', 'Cliente');
	expect(screen.getByLabelText('Eliminar factura 42').closest('td')).toHaveAttribute('data-label', 'Acciones');
	expect(screen.queryByText('Cant.')).not.toBeInTheDocument();
});

test('editors can change the status inline via the dropdown', async () => {
	axios.get.mockResolvedValueOnce({
		data: {
			content: [{
				id: 42,
				workOrderId: 7,
				titulo: 'Factura demo',
				customerName: 'Cliente demo',
				startDate: '2026-07-01',
				workOrderStatus: 'EN_GESTION',
				precio: 1000,
				totalPaid: 250,
			}],
			totalPages: 1,
		},
	});
	axios.put.mockResolvedValueOnce({data: {}});

	renderTable('ADMIN');

	const select = await screen.findByDisplayValue('En gestión');
	fireEvent.change(select, {target: {value: 'CERRADO'}});

	await waitFor(() =>
		expect(axios.put).toHaveBeenCalledWith(
			expect.stringContaining('/api/workorders/7/status'),
			null,
			expect.objectContaining({params: {status: 'CERRADO'}}),
		),
	);
});
