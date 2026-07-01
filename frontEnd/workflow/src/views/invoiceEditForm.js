import {useEffect, useState, useContext} from 'react';
import {useNavigate, useParams} from 'react-router-dom';
import axios from 'axios';
import {BASE_URL} from '../api/config';
import {UserContext} from '../UserProvider';
import {INVOICE_STATUS_OPTIONS} from '../constants/invoiceStatus';

export default function InvoiceEditForm() {
	const {invoiceId} = useParams();
	const navigate = useNavigate();
	const {user} = useContext(UserContext);
	const [customers, setCustomers] = useState([]);
	const [invoice, setInvoice] = useState(null);
	const [error, setError] = useState(null);
	const [submitting, setSubmitting] = useState(false);

	useEffect(() => {
		const token = user?.token || localStorage.getItem('token');
		const headers = token ? {Authorization: `Bearer ${token}`} : {};

		Promise.all([
			axios.get(`${BASE_URL}/api/invoices/${invoiceId}`, {headers}),
			axios.get(`${BASE_URL}/api/customers`, {headers}).catch(() => ({data: []})),
		])
			.then(([invoiceRes, customersRes]) => {
				const rows = invoiceRes.data.lineItems?.length
					? invoiceRes.data.lineItems
					: [
							{
								description: invoiceRes.data.titulo || 'Factura',
								quantity: 1,
								unitPrice: invoiceRes.data.precio || '',
							},
					  ];
				setInvoice({
					...invoiceRes.data,
					customerId: invoiceRes.data.customerId || '',
					fechaEntrega: invoiceRes.data.fechaEntrega || '',
					fechaEstimada: invoiceRes.data.fechaEstimada || '',
					lineItems: rows,
				});
				setCustomers(customersRes.data || []);
			})
			.catch((err) => {
				console.error(err);
				setError('No se pudo cargar la factura.');
			});
	}, [invoiceId, user?.token]);

	const handleChange = (e) => {
		const {name, value} = e.target;
		setInvoice((prev) => ({
			...prev,
			[name]:
				['precio', 'cantidad', 'amount', 'customerId'].includes(name)
					? value === ''
						? ''
						: Number(value)
					: value,
		}));
	};

	const updateLineItem = (index, field, value) => {
		setInvoice((prev) => ({
			...prev,
			lineItems: prev.lineItems.map((item, itemIndex) =>
				itemIndex === index
					? {
							...item,
							[field]:
								field === 'description'
									? value
									: value === ''
									  ? ''
									  : Number(value),
					  }
					: item,
			),
		}));
	};

	const addLineItem = () => {
		setInvoice((prev) => ({
			...prev,
			lineItems: [
				...prev.lineItems,
				{description: '', quantity: 1, unitPrice: ''},
			],
		}));
	};

	const removeLineItem = (index) => {
		setInvoice((prev) => ({
			...prev,
			lineItems:
				prev.lineItems.length === 1
					? [{description: '', quantity: 1, unitPrice: ''}]
					: prev.lineItems.filter((_, itemIndex) => itemIndex !== index),
		}));
	};

	const lineItemsTotal = invoice?.lineItems?.reduce(
		(sum, item) =>
			sum + Number(item.quantity || 0) * Number(item.unitPrice || 0),
		0,
	) || 0;

	// --- Validation -----------------------------------------------------------
	const validate = () => {
		if (!invoice.titulo?.trim()) return 'El título es obligatorio.';
		const validItems = invoice.lineItems.filter((item) =>
			item.description?.trim(),
		);
		if (validItems.length === 0)
			return 'Agregá al menos un item con descripción.';
		const hasNegative = validItems.some(
			(item) =>
				Number(item.quantity || 0) < 0 || Number(item.unitPrice || 0) < 0,
		);
		if (hasNegative)
			return 'Las cantidades y precios no pueden ser negativos.';
		if (
			invoice.startDate &&
			invoice.fechaEntrega &&
			invoice.fechaEntrega < invoice.startDate
		)
			return 'La fecha de vencimiento no puede ser anterior a la de emisión.';
		return null;
	};

	const handleSubmit = async (e) => {
		e.preventDefault();
		setError(null);
		const validationError = validate();
		if (validationError) {
			setError(validationError);
			return;
		}
		setSubmitting(true);
		const token = user?.token || localStorage.getItem('token');
		const payload = {
			titulo: invoice.titulo,
			customerId: invoice.customerId || null,
			cantidad: invoice.cantidad,
			precio: lineItemsTotal,
			lineItems: invoice.lineItems
				.filter((item) => item.description?.trim())
				.map((item) => ({
					id: item.id || null,
					description: item.description.trim(),
					quantity: Number(item.quantity || 0),
					unitPrice: Number(item.unitPrice || 0),
				})),
			clientPhone: invoice.clientPhone,
			fechaEntrega: invoice.fechaEntrega || null,
			fechaEstimada: invoice.fechaEstimada || invoice.fechaEntrega || null,
			notas: invoice.notas,
			workOrderStatus: invoice.workOrderStatus,
		};

		try {
			await axios.put(`${BASE_URL}/api/invoices/${invoiceId}`, payload, {
				headers: {Authorization: `Bearer ${token}`},
			});
			navigate(`/invoices/${invoiceId}`);
		} catch (err) {
			console.error(err);
			setError('No se pudo guardar la factura.');
		} finally {
			setSubmitting(false);
		}
	};

	if (error && !invoice) return <p className='error-text'>{error}</p>;
	if (!invoice) return <p style={{padding: '2rem'}}>Cargando...</p>;

	return (
		<div className='product-creation-container'>
			<div className='form-header'>
				<h2>Editar Factura #{invoiceId}</h2>
			</div>
			<form onSubmit={handleSubmit} className='creation-form'>
				<h3>Datos generales</h3>
				<div className='input-row'>
					<div className='input-group'>
						<label>Titulo</label>
						<input name='titulo' value={invoice.titulo || ''} onChange={handleChange} required />
					</div>
				</div>

				<div className='input-row'>
					<div className='input-group'>
						<label>Cliente</label>
						<select name='customerId' value={invoice.customerId || ''} onChange={handleChange}>
							<option value=''>Sin cliente asignado</option>
							{customers.map((customer) => (
								<option key={customer.id} value={customer.id}>{customer.name}</option>
							))}
						</select>
					</div>
					<div className='input-group'>
						<label>Telefono de contacto</label>
						<input name='clientPhone' value={invoice.clientPhone || ''} onChange={handleChange} />
					</div>
				</div>

				<div className='input-row'>
					<div className='input-group'>
						<label>Cant.</label>
						<input type='number' name='cantidad' value={invoice.cantidad || 1} onChange={handleChange} required min='1' />
					</div>
					<div className='input-group'>
						<label>Estado</label>
						<select name='workOrderStatus' value={invoice.workOrderStatus || 'EN_GESTION'} onChange={handleChange}>
							{INVOICE_STATUS_OPTIONS.map(({value, label}) => (
								<option key={value} value={value}>{label}</option>
							))}
						</select>
					</div>
				</div>

				<div className='line-items-section'>
					<div className='line-items-header'>
						<h3>Items</h3>
						<button type='button' className='btn-pill' onClick={addLineItem}>
							Agregar item
						</button>
					</div>
					<div className='line-items-grid line-items-grid-header'>
						<span>Descripcion</span>
						<span>Cant.</span>
						<span>Precio unit.</span>
						<span>Subtotal</span>
						<span></span>
					</div>
					{invoice.lineItems.map((item, index) => (
						<div className='line-items-grid' key={item.id || index}>
							<input
								type='text'
								value={item.description || ''}
								onChange={(e) =>
									updateLineItem(index, 'description', e.target.value)
								}
								required={index === 0}
							/>
							<input
								type='number'
								min='0'
								step='0.001'
								value={item.quantity || ''}
								onChange={(e) =>
									updateLineItem(index, 'quantity', e.target.value)
								}
							/>
							<input
								type='number'
								min='0'
								step='0.01'
								value={item.unitPrice || ''}
								onChange={(e) =>
									updateLineItem(index, 'unitPrice', e.target.value)
								}
							/>
							<div className='line-item-subtotal'>
								{(
									Number(item.quantity || 0) * Number(item.unitPrice || 0)
								).toLocaleString('es-AR', {
									style: 'currency',
									currency: 'ARS',
								})}
							</div>
							<button
								type='button'
								className='line-item-remove'
								onClick={() => removeLineItem(index)}
								aria-label='Quitar item'
							>
								x
							</button>
						</div>
					))}
					<div className='line-items-total'>
						<span>Total</span>
						<strong>
							{lineItemsTotal.toLocaleString('es-AR', {
								style: 'currency',
								currency: 'ARS',
							})}
						</strong>
					</div>
				</div>

				<h3>Fechas</h3>
				<div className='input-row'>
					<div className='input-group'>
						<label>Fecha Emisión</label>
						<input type='date' name='startDate' value={invoice.startDate || ''} onChange={handleChange} disabled />
						<small className='field-hint'>Se fija al crear la factura y no puede editarse.</small>
					</div>
					<div className='input-group'>
						<label>Fecha Vencimiento</label>
						<input type='date' name='fechaEntrega' value={invoice.fechaEntrega || ''} onChange={handleChange} />
					</div>
				</div>

				<div className='input-group full-width'>
					<label>Notas</label>
					<textarea name='notas' value={invoice.notas || ''} onChange={handleChange} rows='3' />
				</div>

				{error && <p className='error-text'>{error}</p>}
				<div className='form-actions'>
					<button
						type='button'
						className='btn-pill'
						onClick={() => navigate(`/invoices/${invoiceId}`)}
						disabled={submitting}
					>
						Cancelar
					</button>
					<button type='submit' className='submit-button' disabled={submitting}>
						{submitting ? 'Guardando...' : 'Guardar Cambios'}
					</button>
				</div>
			</form>
		</div>
	);
}
