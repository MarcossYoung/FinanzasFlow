import {fireEvent, render, screen, waitFor} from '@testing-library/react';
import axios from 'axios';
import CostsManager from './CostsManager';
import {UserContext} from '../UserProvider';

jest.mock('axios', () => ({get: jest.fn(), post: jest.fn(), put: jest.fn(), delete: jest.fn()}));
jest.mock('../components/statCard', () => ({title}) => <div>{title}</div>);
jest.mock('../components/expensesPieChart', () => () => <div>Gráfico de costos</div>);

test('keeps reporting visible and reveals manual cost entry on demand', async () => {
	axios.get.mockImplementation((url) => Promise.resolve(url.endsWith('/summary')
		? {data: {total: 0, breakdown: []}}
		: {data: {content: [], totalPages: 0}}));
	render(
		<UserContext.Provider value={{user: {token: 'token'}}}>
			<CostsManager />
		</UserContext.Provider>,
	);
	await waitFor(() => expect(axios.get).toHaveBeenCalledTimes(2));
	expect(screen.getByText('Distribución por Tipo')).toBeInTheDocument();
	expect(screen.getByText('Gastos del Período')).toBeInTheDocument();
	expect(screen.queryByPlaceholderText('0.00')).not.toBeInTheDocument();
	fireEvent.click(screen.getByRole('button', {name: /agregar/i}));
	expect(screen.getByPlaceholderText('0.00')).toBeInTheDocument();
});
