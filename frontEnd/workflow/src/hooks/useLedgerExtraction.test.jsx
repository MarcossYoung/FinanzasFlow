import {fireEvent, render, screen, waitFor} from '@testing-library/react';
import axios from 'axios';
import useLedgerExtraction from './useLedgerExtraction';
import {UserContext} from '../UserProvider';

jest.mock('axios', () => ({post: jest.fn()}));

function HookHarness() {
	const {file, setFile, extraction, status, error, extract} = useLedgerExtraction();
	return (
		<div>
			<span data-testid='status'>{status}</span>
			<span data-testid='file'>{file?.name || ''}</span>
			<span data-testid='title'>{extraction?.titulo || ''}</span>
			<span data-testid='error'>{error || ''}</span>
			<input
				type='file'
				onChange={(e) => setFile(e.target.files[0])}
				data-testid='file-input'
			/>
			<button type='button' onClick={() => extract('factura')}>Extraer</button>
		</div>
	);
}

const renderHookHarness = () => render(
	<UserContext.Provider value={{user: {token: 'token'}}}>
		<HookHarness />
	</UserContext.Provider>,
);

beforeEach(() => {
	jest.clearAllMocks();
});

test('transitions from idle to done after successful extraction', async () => {
	axios.post.mockResolvedValue({data: {titulo: 'Factura'}});
	renderHookHarness();

	fireEvent.change(screen.getByTestId('file-input'), {
		target: {files: [new File(['x'], 'factura.jpg', {type: 'image/jpeg'})]},
	});
	fireEvent.click(screen.getByRole('button', {name: /extraer/i}));

	expect(screen.getByTestId('status')).toHaveTextContent('extracting');
	await waitFor(() => expect(screen.getByTestId('status')).toHaveTextContent('done'));
	expect(screen.getByTestId('title')).toHaveTextContent('Factura');
	expect(axios.post).toHaveBeenCalledWith(
		expect.stringContaining('/api/ledger/extract'),
		expect.any(FormData),
		expect.objectContaining({headers: {Authorization: 'Bearer token'}}),
	);
});

test('transitions to error when extraction fails', async () => {
	axios.post.mockRejectedValue({response: {data: {error: 'No se pudo leer'}}});
	renderHookHarness();

	fireEvent.change(screen.getByTestId('file-input'), {
		target: {files: [new File(['x'], 'factura.jpg', {type: 'image/jpeg'})]},
	});
	fireEvent.click(screen.getByRole('button', {name: /extraer/i}));

	await waitFor(() => expect(screen.getByTestId('status')).toHaveTextContent('error'));
	expect(screen.getByTestId('error')).toHaveTextContent('No se pudo leer');
});
