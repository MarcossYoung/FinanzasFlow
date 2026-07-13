import {useState, useContext} from 'react';
import axios from 'axios';
import {UserContext} from '../UserProvider';
import {useNavigate} from 'react-router-dom';
import {BASE_URL} from '../api/config';
import CustomerPicker from '../components/CustomerPicker';
import LedgerAttachInput from '../components/LedgerAttachInput';

const emptyInvoice = {
	titulo: '',
	customerId: '',
	precio: '',
	amount: '',
	cantidad: 1,
	startDate: new Date().toISOString().split('T')[0],
	fechaEntrega: '',
	fechaEstimada: '',
	notas: '',
	clientPhone: '',
	lineItems: [{description: '', quantity: 1, unitPrice: ''}],
};

const InvoiceCreateForm = ({isModal = false, onClose}) => {
	const navigate = useNavigate();
	const {user} = useContext(UserContext);
	const [invoiceData, setInvoiceData] = useState(emptyInvoice);
	const [error, setError] = useState(null);
	const [success, setSuccess] = useState(false);
	const [submitting, setSubmitting] = useState(false);
	const [detectedCustomer, setDetectedCustomer] = useState(null);
	const [selectedCustomerLabel, setSelectedCustomerLabel] = useState('');
	const [customerPickerKey, setCustomerPickerKey] = useState(0);

	const authHeaders = () => {
		const token = user?.token || localStorage.getItem('token');
		return token ? {Authorization: `Bearer ${token}`} : {};
	};

	const handleCustomerChange = (customerId) => {
		setInvoiceData((prev) => ({...prev, customerId: customerId || ''}));
		if (customerId) setDetectedCustomer(null);
	};

	const handleInputChange = (e) => {
		const {name, value} = e.target;
		setInvoiceData((prev) => ({
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
		setInvoiceData((prev) => ({
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
		setInvoiceData((prev) => ({
			...prev,
			lineItems: [
				...prev.lineItems,
				{description: '', quantity: 1, unitPrice: ''},
			],
		}));
	};

	const removeLineItem = (index) => {
		setInvoiceData((prev) => ({
			...prev,
			lineItems:
				prev.lineItems.length === 1
					? [{description: '', quantity: 1, unitPrice: ''}]
					: prev.lineItems.filter((_, itemIndex) => itemIndex !== index),
		}));
	};

	const lineItemsTotal = invoiceData.lineItems.reduce(
		(sum, item) =>
			sum + Number(item.quantity || 0) * Number(item.unitPrice || 0),
		0,
	);

	const normalize = (value) => (value || '').trim().toLowerCase();
	const normalizeTaxId = (value) => (value || '').replace(/\D/g, '');

	const isStrongCustomerMatch = (customer, extraction) => {
		const extractedName = normalize(extraction.counterpartyName || extraction.originName);
		const extractedTaxId = normalizeTaxId(extraction.cuitDni || extraction.originTaxId);
		const customerName = normalize(customer.name);
		const customerTaxId = normalizeTaxId(customer.cuitDni);
		return Boolean(
			(extractedTaxId && customerTaxId && extractedTaxId === customerTaxId) ||
			(extractedName && customerName && extractedName === customerName),
		);
	};

	const resolveExtractedCustomer = async (extraction) => {
		// Transfer receipts don't have a single "counterparty" — the AI puts the
		// sender's identity in originName/originTaxId instead. The origin account
		// is the payer, i.e. the customer for a cobro.
		const detectedName = extraction.counterpartyName || extraction.originName;
		const detectedTaxId = extraction.cuitDni || extraction.originTaxId;
		const query = detectedName?.trim();
		if (!query) {
			setInvoiceData((prev) => ({...prev, customerId: ''}));
			setDetectedCustomer(detectedName || detectedTaxId ? extraction : null);
			return;
		}
		try {
			const res = await axios.get(`${BASE_URL}/api/customers/search`, {
				headers: authHeaders(),
				params: {q: query},
			});
			const matches = res.data || [];
			if (matches.length === 1 && isStrongCustomerMatch(matches[0], extraction)) {
				setInvoiceData((prev) => ({
					...prev,
					customerId: matches[0].id,
					clientPhone: extraction.phone || matches[0].phone || prev.clientPhone,
				}));
				setSelectedCustomerLabel(matches[0].name || query);
				setCustomerPickerKey((key) => key + 1);
				setDetectedCustomer(null);
				return;
			}
			setInvoiceData((prev) => ({...prev, customerId: ''}));
			setDetectedCustomer(extraction);
		} catch (err) {
			console.error('Error searching extracted customer', err);
			setInvoiceData((prev) => ({...prev, customerId: ''}));
			setDetectedCustomer(extraction);
		}
	};

	const handleExtracted = (extraction) => {
		setInvoiceData((prev) => ({
			...prev,
			titulo: extraction.titulo || prev.titulo,
			clientPhone: extraction.phone || prev.clientPhone,
			amount: extraction.amount ?? prev.amount,
			startDate: extraction.issueDate || prev.startDate,
			fechaEntrega: extraction.dueDate || prev.fechaEntrega,
			fechaEstimada: extraction.dueDate || prev.fechaEstimada,
			lineItems: extraction.lineItems?.length
				? extraction.lineItems.map((li) => ({
						description: li.description || '',
						quantity: li.quantity ?? 1,
						unitPrice: li.unitPrice ?? '',
				  }))
				: prev.lineItems,
		}));
		resolveExtractedCustomer(extraction);
	};

	const handleSubmit = async (e) => {
		e.preventDefault();
		if (submitting) return;
		setSubmitting(true);
		setError(null);
		setSuccess(false);

		const token = user?.token || localStorage.getItem('token');
		const payload = {
			...invoiceData,
			precio: lineItemsTotal,
			lineItems: invoiceData.lineItems
				.filter((item) => item.description?.trim())
				.map((item) => ({
					description: item.description.trim(),
					quantity: Number(item.quantity || 0),
					unitPrice: Number(item.unitPrice || 0),
				})),
			customerId: invoiceData.customerId || null,
			amount: invoiceData.amount > 0 ? invoiceData.amount : null,
			fechaEntrega: invoiceData.fechaEntrega || null,
			fechaEstimada: invoiceData.fechaEstimada || invoiceData.fechaEntrega || null,
		};

		try {
			await axios.post(`${BASE_URL}/api/invoices/create`, payload, {
				headers: {
					Authorization: `Bearer ${token}`,
					'Content-Type': 'application/json',
				},
			});
			setSuccess(true);
			setTimeout(() => {
				if (isModal && onClose) onClose();
				else navigate('/dashboard');
			}, 800);
		} catch (err) {
			console.error(err);
			setError(
				err.response?.data?.message ||
					'Error al crear la factura. Intente nuevamente.',
			);
		} finally {
			setSubmitting(false);
		}
	};

	return (
		<div className={`product-creation-container ${isModal ? 'is-modal' : ''}`}>
			<div className='form-header'>
				<h2>{isModal ? 'Nueva Factura' : 'Crear Factura'}</h2>
				{isModal && (
					<button className='close-x' onClick={onClose} type='button'>
						&times;
					</button>
				)}
			</div>

			<form onSubmit={handleSubmit} className='creation-form'>
				<LedgerAttachInput onExtracted={handleExtracted} disabled={submitting} />
				<div className='input-row'>
					<div className='input-group'>
						<label>Titulo</label>
						<input
							type='text'
							name='titulo'
							value={invoiceData.titulo}
							onChange={handleInputChange}
							required
							placeholder='Factura A 0001-00001234'
						/>
					</div>
			</div>

				<div className='input-row'>
					<div className='input-group'>
						<label>Cliente</label>
						<CustomerPicker
							key={customerPickerKey}
							value={invoiceData.customerId}
							onChange={handleCustomerChange}
							initialLabel={selectedCustomerLabel}
							headers={authHeaders()}
						/>
						{detectedCustomer &&
							(detectedCustomer.counterpartyName ||
								detectedCustomer.cuitDni ||
								detectedCustomer.originName ||
								detectedCustomer.originTaxId) && (
								<p className='detected-customer-hint'>
									Detectado:{' '}
									{[
										detectedCustomer.counterpartyName || detectedCustomer.originName,
										detectedCustomer.cuitDni || detectedCustomer.originTaxId,
									]
										.filter(Boolean)
										.join(' - ')}
								</p>
							)}
					</div>
					<div className='input-group'>
						<label>Telefono de contacto</label>
						<input
							type='tel'
							name='clientPhone'
							value={invoiceData.clientPhone}
							onChange={handleInputChange}
							placeholder='+54 11 1234-5678'
						/>
					</div>
				</div>

				<div className='input-row'>
					<div className='input-group'>
						<label>Pago inicial ($)</label>
						<input
							type='number'
							name='amount'
							value={invoiceData.amount}
							onChange={handleInputChange}
							placeholder='Opcional'
							min='0'
						/>
					</div>
					<div className='input-group' style={{flex: '0.5'}}>
						<label>Cant.</label>
						<input
							type='number'
							name='cantidad'
							value={invoiceData.cantidad}
							onChange={handleInputChange}
							required
							min='1'
						/>
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
					{invoiceData.lineItems.map((item, index) => (
						<div className='line-items-grid' key={index}>
							<input
								type='text'
								value={item.description}
								onChange={(e) =>
									updateLineItem(index, 'description', e.target.value)
								}
								placeholder='Servicio, producto o concepto'
								required={index === 0}
							/>
							<input
								type='number'
								min='0'
								step='0.001'
								value={item.quantity}
								onChange={(e) =>
									updateLineItem(index, 'quantity', e.target.value)
								}
							/>
							<input
								type='number'
								min='0'
								step='0.01'
								value={item.unitPrice}
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

				<div className='input-row'>
					<div className='input-group'>
						<label>Fecha Emisión</label>
						<input
							type='date'
							name='startDate'
							value={invoiceData.startDate}
							onChange={handleInputChange}
						/>
					</div>
					<div className='input-group'>
						<label>Fecha Vencimiento</label>
						<input
							type='date'
							name='fechaEntrega'
							value={invoiceData.fechaEntrega}
							onChange={handleInputChange}
						/>
					</div>
				</div>

				<div className='input-group full-width'>
					<label>Notas</label>
					<textarea
						name='notas'
						value={invoiceData.notas}
						onChange={handleInputChange}
						rows='3'
					/>
				</div>

				<button
					type='submit'
					className='submit-button'
					disabled={submitting || !invoiceData.titulo || lineItemsTotal <= 0}
				>
					{submitting ? 'Guardando...' : 'Guardar Factura'}
				</button>

				{error && <p className='error-text'>{error}</p>}
				{success && <p className='success-text'>Factura creada correctamente.</p>}
			</form>
		</div>
	);
};

export default InvoiceCreateForm;
