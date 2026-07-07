import {useEffect, useRef, useState} from 'react';
import axios from 'axios';
import {BASE_URL} from '../api/config';

export default function CustomerPicker({value, onChange, initialLabel = '', headers}) {
	const [query, setQuery] = useState(initialLabel);
	const [results, setResults] = useState([]);
	const [open, setOpen] = useState(false);
	const containerRef = useRef(null);
	const debounceRef = useRef(null);

	useEffect(() => {
		if (!value) setQuery(initialLabel || '');
	}, [value, initialLabel]);

	useEffect(() => {
		const handleClickOutside = (e) => {
			if (containerRef.current && !containerRef.current.contains(e.target)) {
				setOpen(false);
			}
		};
		document.addEventListener('mousedown', handleClickOutside);
		return () => document.removeEventListener('mousedown', handleClickOutside);
	}, []);

	useEffect(() => () => {
		if (debounceRef.current) clearTimeout(debounceRef.current);
	}, []);

	const handleQueryChange = (e) => {
		const q = e.target.value;
		setQuery(q);
		setOpen(true);
		onChange('');

		if (debounceRef.current) clearTimeout(debounceRef.current);
		if (!q.trim()) {
			setResults([]);
			return;
		}
		debounceRef.current = setTimeout(async () => {
			try {
				const res = await axios.get(`${BASE_URL}/api/customers/search`, {
					headers,
					params: {q: q.trim()},
				});
				setResults(res.data || []);
			} catch (err) {
				console.error('Error searching customers:', err);
			}
		}, 300);
	};

	const selectCustomer = (customer) => {
		setQuery(customer.name);
		onChange(customer.id);
		setResults([]);
		setOpen(false);
	};

	return (
		<div className='customer-picker' ref={containerRef}>
			<input
				type='text'
				value={query}
				onChange={handleQueryChange}
				onFocus={() => query.trim() && setOpen(true)}
				placeholder='Buscar cliente por nombre, email, telefono o CUIT'
				autoComplete='off'
			/>
			{open && results.length > 0 && (
				<ul className='customer-picker-results'>
					{results.map((customer) => (
						<li key={customer.id} onClick={() => selectCustomer(customer)}>
							<span>{customer.name}</span>
							{customer.cuitDni && (
								<small className='customer-picker-meta'>{customer.cuitDni}</small>
							)}
						</li>
					))}
				</ul>
			)}
		</div>
	);
}
