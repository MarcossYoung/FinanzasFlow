import React, {
	createContext,
	useContext,
	useState,
	useEffect,
	useRef,
	useCallback,
	useMemo,
} from 'react';
import axios from 'axios';
import {BASE_URL} from './api/config';

const InvoicesContext = createContext();
export const useInvoices = () => useContext(InvoicesContext);

export const InvoicesProvider = ({children}) => {
	const [invoices, setInvoices] = useState([]);
	const [loading, setLoading] = useState(false);
	const [error, setError] = useState(null);
	const didInitRef = useRef(false);
	const inFlightRef = useRef(false);

	const getAuthHeaders = () => {
		const token = localStorage.getItem('token');
		return token ? {Authorization: `Bearer ${token}`} : {};
	};

	const fetchAllInvoices = useCallback(async () => {
		if (inFlightRef.current) return;
		inFlightRef.current = true;
		setLoading(true);
		setError(null);
		try {
			const res = await axios.get(`${BASE_URL}/api/invoices`, {
				headers: getAuthHeaders(),
			});
			const data = Array.isArray(res.data)
				? res.data
				: res.data?.content || [];
			setInvoices(data);
		} catch (err) {
			setError(err);
			console.error('fetchAllInvoices error:', err);
		} finally {
			setLoading(false);
			inFlightRef.current = false;
		}
	}, []);

	const fetchInvoicesByRange = useCallback(async (endpoint) => {
		try {
			const res = await axios.get(`${BASE_URL}/api/invoices${endpoint}`, {
				headers: getAuthHeaders(),
			});
			return Array.isArray(res.data) ? res.data : res.data?.content || [];
		} catch (err) {
			console.error(`fetchInvoicesByRange(${endpoint}) error:`, err);
			return [];
		}
	}, []);

	useEffect(() => {
		if (didInitRef.current) return;
		didInitRef.current = true;
		fetchAllInvoices();
	}, [fetchAllInvoices]);

	const value = useMemo(
		() => ({
			invoices,
			loading,
			error,
			fetchAllInvoices,
			fetchInvoicesByRange,
			setInvoices,
		}),
		[invoices, loading, error, fetchAllInvoices, fetchInvoicesByRange],
	);

	return (
		<InvoicesContext.Provider value={value}>
			{children}
		</InvoicesContext.Provider>
	);
};
