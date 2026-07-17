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

	const normalize = (value) =>
		(value || '')
			.normalize('NFD')
			.replace(/[\u0300-\u036f]/g, '')
			.replace(/[.,]/g, '')
			.replace(/\s+/g, ' ')
			.trim()
			.toLowerCase();
	const normalizeTaxId = (value) => (value || '').replace(/\D/g, '');

	const isStrongCustomerMatch = (customer, extraction) => {
		const extractedName = normalize(
			extraction.counterpartyName || extraction.originName,
		);
		const extractedTaxId = normalizeTaxId(
			extraction.cuitDni || extraction.originTaxId,
		);
		const customerName = normalize(customer.name);
		const customerTaxId = normalizeTaxId(customer.cuitDni);
		return Boolean(
			(extractedTaxId && customerTaxId && extractedTaxId === customerTaxId) ||
			(extractedName && customerName && extractedName === customerName),
		);
	};

	const applyCustomerMatch = (customer, extraction = detectedCustomer) => {
		const detectedName = extraction?.counterpartyName || extraction?.originName;
		setInvoiceData((prev) => ({
			...prev,
			customerId: customer.id,
			clientPhone: extraction?.phone || customer.phone || prev.clientPhone,
		}));
		setSelectedCustomerLabel(customer.name || detectedName || '');
		setCustomerPickerKey((key) => key + 1);
		setDetectedCustomer(null);
	};

	const createDetectedCustomer = async () => {
		if (!detectedCustomer) return;
		const name = detectedCustomer.counterpartyName || detectedCustomer.originName;
		if (!name?.trim()) return;
		try {
			const res = await axios.post(
				`${BASE_URL}/api/customers/find-or-create`,
				{
					name: name.trim(),
					cuitDni: detectedCustomer.cuitDni || detectedCustomer.originTaxId || null,
					phone: detectedCustomer.phone || null,
					email: null,
				},
				{headers: authHeaders()},
			);
			applyCustomerMatch(res.data, detectedCustomer);
		} catch (err) {
			console.error('Error creating detected customer', err);
			setError('No se pudo crear el cliente detectado.');
		}
	};

	const resolveExtractedCustomer = async (extraction) => {
		// Transfer receipts don't have a single "counterparty" — the AI puts the
		// sender's identity in originName/originTaxId instead. The origin account
		// is the payer, i.e. the customer for a cobro.
		const detectedName = extraction.counterpartyName || extraction.originName;
		const detectedNameForLabel = detectedName || '';
		const detectedTaxId = extraction.cuitDni || extraction.originTaxId;
		const query = detectedName?.trim();
		if (!query) {
			setInvoiceData((prev) => ({...prev, customerId: ''}));
			setDetectedCustomer(
				detectedName || detectedTaxId ? {...extraction, candidates: []} : null,
			);
			return;
		}
		try {
			const res = await axios.get(`${BASE_URL}/api/customers/search`, {
				headers: authHeaders(),
				params: {q: query},
			});
			const matches = res.data || [];
			if (matches.length === 1 && isStrongCustomerMatch(matches[0], extraction)) {
				applyCustomerMatch(matches[0], extraction);
				return;
			}
			setInvoiceData((prev) => ({...prev, customerId: ''}));
			setDetectedCustomer({...extraction, candidates: matches});
			setSelectedCustomerLabel(detectedNameForLabel);
		} catch (err) {
			console.error('Error searching extracted customer', err);
			setInvoiceData((prev) => ({...prev, customerId: ''}));
			setDetectedCustomer({...extraction, candidates: []});
			setSelectedCustomerLabel(detectedNameForLabel);
		}
	};

	const handleExtracted = (extraction) => {
		setInvoiceData((prev) => ({
			...prev,
			titulo: extraction.titulo || prev.titulo,
			clientPhone: extraction.phone || prev.clientPhone,
			amount: extraction.amount ?? prev.amount,
			precio: extraction.amount ?? prev.precio,
			startDate: extraction.issueDate || prev.startDate,
			fechaEntrega: extraction.dueDate || prev.fechaEntrega,
			fechaEstimada: extraction.dueDate || prev.fechaEstimada,
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
			precio: Number(invoiceData.precio) || 0,
			lineItems: [],
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
				<h2>{isModal ? 'Factura' : 'Crear Factura'}</h2>
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
								<div className='detected-customer-hint'>
									<span>
										Detectado:{' '}
										{[
											detectedCustomer.counterpartyName ||
												detectedCustomer.originName,
											detectedCustomer.cuitDni || detectedCustomer.originTaxId,
										]
											.filter(Boolean)
											.join(' - ')}
									</span>
									<div className='detected-customer-actions'>
										{detectedCustomer.candidates?.map((customer, index) => (
											<button
												key={customer.id || index}
												type='button'
												className='btn-pill'
												onClick={() => applyCustomerMatch(customer)}
											>
												{[customer.name, customer.cuitDni]
													.filter(Boolean)
													.join(' - ')}
											</button>
										))}
										{(detectedCustomer.counterpartyName || detectedCustomer.originName) && (
											<button
												type='button'
												className='btn-pill btn-pill-create'
												onClick={createDetectedCustomer}
											>
												+ Crear cliente nuevo
											</button>
										)}
									</div>
								</div>
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
					<div className='input-group'>
						<label>Precio ($)</label>
						<input
							type='number'
							name='precio'
							value={invoiceData.precio}
							onChange={handleInputChange}
							required
							placeholder='Total de la factura'
							min='0'
						/>
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
					disabled={
						submitting || !invoiceData.titulo || Number(invoiceData.precio) <= 0
					}
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
