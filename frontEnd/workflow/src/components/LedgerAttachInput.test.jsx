import {fireEvent, render, screen, waitFor} from '@testing-library/react';
import axios from 'axios';
import LedgerAttachInput from './LedgerAttachInput';
import {UserContext} from '../UserProvider';

jest.mock('axios', () => ({post: jest.fn()}));

const renderInput = (props = {}) => render(
	<UserContext.Provider value={{user: {token: 'token'}}}>
		<LedgerAttachInput onExtracted={jest.fn()} {...props} />
	</UserContext.Provider>,
);

beforeEach(() => {
	jest.clearAllMocks();
});

test('keeps extract button disabled until a file is chosen', () => {
	const {container} = renderInput();

	const button = screen.getByRole('button', {name: /extraer datos/i});
	expect(button).toBeDisabled();

	fireEvent.change(container.querySelector('input[type="file"]'), {
		target: {files: [new File(['x'], 'factura.jpg', {type: 'image/jpeg'})]},
	});
	expect(button).not.toBeDisabled();
});

test('disables controls while extracting and calls onExtracted', async () => {
	const onExtracted = jest.fn();
	axios.post.mockResolvedValue({data: {titulo: 'Factura'}});
	const {container} = renderInput({onExtracted});

	const input = container.querySelector('input[type="file"]');
	fireEvent.change(input, {
		target: {files: [new File(['x'], 'factura.jpg', {type: 'image/jpeg'})]},
	});
	fireEvent.click(screen.getByRole('button', {name: /extraer datos/i}));

	expect(screen.getByText(/extrayendo/i)).toBeInTheDocument();
	expect(screen.getByRole('button', {name: /extraer datos/i})).toBeDisabled();
	await waitFor(() => expect(onExtracted).toHaveBeenCalledWith({titulo: 'Factura'}));
});
