import {useCallback, useContext, useEffect, useMemo, useState} from 'react';
import axios from 'axios';
import {FaEdit, FaPlus, FaSearch, FaTimes, FaTrashAlt} from 'react-icons/fa';
import {BASE_URL} from '../api/config';
import {UserContext} from '../UserProvider';

const emptyCustomer = {
	name: '',
	cuitDni: '',
	email: '',
	phone: '',
	notes: '',
	paymentScore: 100,
};

export default function Customers() {
	const {user} = useContext(UserContext);
	const [customers, setCustomers] = useState([]);
	const [form, setForm] = useState(emptyCustomer);
	const [editingId, setEditingId] = useState(null);
	const [searchTerm, setSearchTerm] = useState('');
	const [loading, setLoading] = useState(false);
	const [saving, setSaving] = useState(false);
	const [error, setError] = useState('');
	const [success, setSuccess] = useState('');

	const authHeaders = useCallback(() => {
		const token = user?.token || localStorage.getItem('token');
		return token ? {Authorization: `Bearer ${token}`} : {};
	}, [user?.token]);

	const fetchCustomers = useCallback(async () => {
		setLoading(true);
		setError('');
		try {
			const endpoint = searchTerm.trim()
				? `${BASE_URL}/api/customers/search`
				: `${BASE_URL}/api/customers`;
			const res = await axios.get(endpoint, {
				headers: authHeaders(),
				params: searchTerm.trim() ? {q: searchTerm.trim()} : {},
			});
			setCustomers(res.data || []);
		} catch (err) {
			console.error(err);
			setError('No se pudieron cargar los clientes.');
		} finally {
			setLoading(false);
		}
	}, [authHeaders, searchTerm]);

	useEffect(() => {
		fetchCustomers();
	}, [fetchCustomers]);

	const filteredCustomers = useMemo(() => customers, [customers]);

	const handleChange = (e) => {
		const {name, value} = e.target;
		setForm((prev) => ({
			...prev,
			[name]: name === 'paymentScore' ? Number(value) : value,
		}));
	};

	const resetForm = () => {
		setForm(emptyCustomer);
		setEditingId(null);
		setError('');
	};

	const startEdit = (customer) => {
		setEditingId(customer.id);
		setForm({
			name: customer.name || '',
			cuitDni: customer.cuitDni || '',
			email: customer.email || '',
			phone: customer.phone || '',
			notes: customer.notes || '',
			paymentScore: customer.paymentScore ?? 100,
		});
		setSuccess('');
		setError('');
	};

	const handleSubmit = async (e) => {
		e.preventDefault();
		setSaving(true);
		setError('');
		setSuccess('');

		const payload = {
			...form,
			cuitDni: form.cuitDni || null,
			email: form.email || null,
			phone: form.phone || null,
			notes: form.notes || null,
			paymentScore: form.paymentScore || 100,
		};

		try {
			if (editingId) {
				await axios.put(`${BASE_URL}/api/customers/${editingId}`, payload, {
					headers: authHeaders(),
				});
				setSuccess('Cliente actualizado.');
			} else {
				await axios.post(`${BASE_URL}/api/customers`, payload, {
					headers: authHeaders(),
				});
				setSuccess('Cliente creado.');
			}
			resetForm();
			fetchCustomers();
		} catch (err) {
			console.error(err);
			setError(
				err.response?.data?.message ||
					'No se pudo guardar el cliente. Revise los datos e intente nuevamente.',
			);
		} finally {
			setSaving(false);
		}
	};

	const handleDelete = async (customer) => {
		if (!window.confirm(`Eliminar cliente "${customer.name}"?`)) return;
		setError('');
		setSuccess('');
		try {
			await axios.delete(`${BASE_URL}/api/customers/${customer.id}`, {
				headers: authHeaders(),
			});
			if (editingId === customer.id) resetForm();
			setSuccess('Cliente eliminado.');
			fetchCustomers();
		} catch (err) {
			console.error(err);
			setError(
				'No se pudo eliminar. Si el cliente tiene facturas asociadas, edite esas facturas primero.',
			);
		}
	};

	return (
		<div className='customers-page'>
			<div className='orders-header customers-header'>
				<div>
					<h1 className='main-title'>Clientes</h1>
					<p>Gestiona datos fiscales, contacto y score de pago.</p>
				</div>
			</div>

			<div className='customers-layout'>
				<section className='customer-form-panel'>
					<div className='form-header'>
						<h2>{editingId ? 'Editar Cliente' : 'Cliente'}</h2>
						{editingId && (
							<button type='button' className='close-x' onClick={resetForm}>
								<FaTimes />
							</button>
						)}
					</div>
					<form onSubmit={handleSubmit} className='creation-form'>
						<div className='input-group'>
							<label>Nombre</label>
							<input
								name='name'
								value={form.name}
								onChange={handleChange}
								required
								placeholder='Razon social o nombre'
							/>
						</div>
						<div className='input-row'>
							<div className='input-group'>
								<label>CUIT / DNI</label>
								<input
									name='cuitDni'
									value={form.cuitDni}
									onChange={handleChange}
								/>
							</div>
							<div className='input-group'>
								<label>Score</label>
								<input
									type='number'
									name='paymentScore'
									value={form.paymentScore}
									onChange={handleChange}
									min='0'
									max='100'
								/>
							</div>
						</div>
						<div className='input-group'>
							<label>Email</label>
							<input
								type='email'
								name='email'
								value={form.email}
								onChange={handleChange}
								placeholder='cliente@email.com'
							/>
						</div>
						<div className='input-group'>
							<label>Telefono</label>
							<input
								name='phone'
								value={form.phone}
								onChange={handleChange}
								placeholder='+54 11 1234-5678'
							/>
						</div>
						<div className='input-group full-width'>
							<label>Notas</label>
							<textarea
								name='notes'
								value={form.notes}
								onChange={handleChange}
								rows='4'
							/>
						</div>
						<button type='submit' className='submit-button' disabled={saving}>
							{saving ? 'Guardando...' : editingId ? 'Guardar Cambios' : 'Crear Cliente'}
						</button>
					</form>
				</section>

				<section className='customers-list-panel'>
					<div className='customers-toolbar'>
						<div className='customer-search'>
							<FaSearch />
							<input
								value={searchTerm}
								onChange={(e) => setSearchTerm(e.target.value)}
								placeholder='Buscar por nombre, email, telefono o CUIT'
							/>
						</div>
						<button className='btn-pill' type='button' onClick={resetForm}>
							<FaPlus /> Nuevo
						</button>
					</div>

					{error && <p className='error-text'>{error}</p>}
					{success && <p className='success-text'>{success}</p>}

					<div className='table-wrapper customers-table-wrapper'>
						<table className='orders-table customers-table'>
							<thead>
								<tr>
									<th>Cliente</th>
									<th>Contacto</th>
									<th>CUIT / DNI</th>
									<th>Score</th>
									<th>Acciones</th>
								</tr>
							</thead>
							<tbody>
								{loading ? (
									<tr>
										<td colSpan='5' className='empty-cell'>
											Cargando clientes...
										</td>
									</tr>
								) : filteredCustomers.length ? (
									filteredCustomers.map((customer) => (
										<tr key={customer.id}>
											<td>
												<strong>{customer.name}</strong>
												{customer.notes && <small>{customer.notes}</small>}
											</td>
											<td>
												<span>{customer.email || '-'}</span>
												<small>{customer.phone || '-'}</small>
											</td>
											<td>{customer.cuitDni || '-'}</td>
											<td>
												<span className='score-pill'>
													{customer.paymentScore ?? 100}
												</span>
											</td>
											<td>
												<div className='table-actions'>
													<button
														type='button'
														className='icon-action'
														onClick={() => startEdit(customer)}
														aria-label='Editar cliente'
													>
														<FaEdit />
													</button>
													<button
														type='button'
														className='icon-action danger'
														onClick={() => handleDelete(customer)}
														aria-label='Eliminar cliente'
													>
														<FaTrashAlt />
													</button>
												</div>
											</td>
										</tr>
									))
								) : (
									<tr>
										<td colSpan='5' className='empty-cell'>
											Sin clientes para mostrar.
										</td>
									</tr>
								)}
							</tbody>
						</table>
					</div>
				</section>
			</div>
		</div>
	);
}
