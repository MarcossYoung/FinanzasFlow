import {useContext, useState} from 'react';
import axios from 'axios';
import {BASE_URL} from '../api/config';
import {UserContext} from '../UserProvider';

export default function useLedgerExtraction() {
	const {user} = useContext(UserContext);
	const [file, setFile] = useState(null);
	const [extraction, setExtraction] = useState(null);
	const [status, setStatus] = useState('idle');
	const [error, setError] = useState(null);

	const extract = async (caption) => {
		if (!file) {
			setError('Selecciona un archivo.');
			setStatus('error');
			return null;
		}
		const formData = new FormData();
		formData.append('file', file);
		if (caption) formData.append('caption', caption);

		setStatus('extracting');
		setError(null);
		setExtraction(null);
		try {
			const token = user?.token || localStorage.getItem('token');
			const res = await axios.post(`${BASE_URL}/api/ledger/extract`, formData, {
				headers: token ? {Authorization: `Bearer ${token}`} : {},
			});
			setExtraction(res.data);
			setStatus('done');
			return res.data;
		} catch (err) {
			const message =
				err.response?.data?.error ||
				err.response?.data?.message ||
				'No se pudieron extraer datos del documento.';
			setError(message);
			setStatus('error');
			return null;
		}
	};

	const reset = () => {
		setFile(null);
		setExtraction(null);
		setStatus('idle');
		setError(null);
	};

	return {file, setFile, extraction, status, error, extract, reset};
}
