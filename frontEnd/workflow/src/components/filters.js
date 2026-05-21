import React, {useState} from 'react';

const statusOptions = [
	'EN_GESTION',
	'CONTACTADO',
	'PROMETIO_PAGO',
	'EN_DISPUTA',
	'INCOBRABLE',
	'CERRADO',
];

const Filters = ({onFilterChange}) => {
	const [filters, setFilters] = useState({
		from: '',
		to: '',
		workOrderStatus: '',
		minAmount: '',
		maxAmount: '',
	});

	const handleChange = (e) => {
		const {name, value} = e.target;
		setFilters((prev) => ({...prev, [name]: value}));
	};

	const handleSubmit = (e) => {
		e.preventDefault();
		onFilterChange(filters);
	};

	return (
		<form onSubmit={handleSubmit} className='filters'>
			<label>
				Desde
				<input type='date' name='from' value={filters.from} onChange={handleChange} />
			</label>
			<label>
				Hasta
				<input type='date' name='to' value={filters.to} onChange={handleChange} />
			</label>
			<label>
				Estado de gestion
				<select
					name='workOrderStatus'
					value={filters.workOrderStatus}
					onChange={handleChange}
				>
					<option value=''>Todos</option>
					{statusOptions.map((status) => (
						<option key={status} value={status}>
							{status}
						</option>
					))}
				</select>
			</label>
			<label>
				Monto minimo
				<input
					type='number'
					name='minAmount'
					min='0'
					value={filters.minAmount}
					onChange={handleChange}
				/>
			</label>
			<label>
				Monto maximo
				<input
					type='number'
					name='maxAmount'
					min='0'
					value={filters.maxAmount}
					onChange={handleChange}
				/>
			</label>
			<button type='submit'>Aplicar Filtros</button>
		</form>
	);
};

export default Filters;
