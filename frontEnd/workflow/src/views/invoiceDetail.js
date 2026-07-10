import {useEffect, useState, useContext} from 'react';
import {useNavigate, useParams} from 'react-router-dom';
import axios from 'axios';
import {BASE_URL} from '../api/config';
import {UserContext} from '../UserProvider';
import {INVOICE_STATUS_OPTIONS, STATUS_LABELS} from '../constants/invoiceStatus';
import LedgerAttachInput from '../components/LedgerAttachInput';

const formatMoney = (value) =>
	value === null || value === undefined || value === ''
		? '-'
		: Number(value).toLocaleString('es-AR', {
				style: 'currency',
				currency: 'ARS',
		  });

export default function InvoiceDetail() {
	const {invoiceId} = useParams();
	const navigate = useNavigate();
	const {user} = useContext(UserContext);
	const [invoice, setInvoice] = useState(null);
	const [payments, setPayments] = useState([]);
	const [payment, setPayment] = useState({amount: '', paymentType: 'PAYMENT'});
	const [error, setError] = useState(null);
	const canEdit = user?.role === 'ADMIN' || user?.role === 'SUPER_ADMIN' || user?.role === 'GESTOR';

	const authHeaders = () => {
		const token = user?.token || localStorage.getItem('token');
		return token ? {Authorization: `Bearer ${token}`} : {};
	};

	const loadInvoice = async () => {
		try {
			const [invoiceRes, paymentsRes] = await Promise.all([
				axios.get(`${BASE_URL}/api/invoices/${invoiceId}`, {
					headers: authHeaders(),
				}),
				axios
					.get(`${BASE_URL}/api/payments/${invoiceId}`, {
						headers: authHeaders(),
					})
					.catch(() => ({data: []})),
			]);
			setInvoice(invoiceRes.data);
			setPayments(paymentsRes.data || []);
		} catch (err) {
			console.error(err);
			setError('No se pudo cargar la factura.');
		}
	};

	useEffect(() => {
		loadInvoice();
		// eslint-disable-next-line react-hooks/exhaustive-deps
	}, [invoiceId]);

	const updateStatus = async (status) => {
		if (!invoice?.workOrderId) return;
		try {
			await axios.put(
				`${BASE_URL}/api/workorders/${invoice.workOrderId}/status`,
				null,
				{params: {status}, headers: authHeaders()},
			);
			setInvoice((prev) => ({...prev, workOrderStatus: status}));
		} catch (err) {
			console.error(err);
			alert('No se pudo actualizar el estado.');
		}
	};

	const addPayment = async (e) => {
		e.preventDefault();
		if (!payment.amount) return;
		try {
			await axios.post(
				`${BASE_URL}/api/payments`,
				{
					product_id: Number(invoiceId),
					valor: Number(payment.amount),
					type: payment.paymentType,
				},
				{headers: authHeaders()},
			);
			setPayment({amount: '', paymentType: 'PAYMENT'});
			loadInvoice();
		} catch (err) {
			console.error(err);
			alert('No se pudo registrar el pago.');
		}
	};

	const handleExtracted = (extraction) => {
		setPayment((prev) => ({...prev, amount: extraction.amount ?? prev.amount}));
	};

	if (error) return <p className='error-text'>{error}</p>;
	if (!invoice) return <p className='invoice-detail-loading'>Cargando...</p>;

	const balance = Number(invoice.precio || 0) - Number(invoice.totalPaid || 0);

	return (
		<div className='invoice-detail-page'>

			{/* Hero */}
			<div className='invoice-hero'>
				<div className='invoice-hero-left'>
					<h1 className='main-title'>Factura #{invoiceId}</h1>
					{invoice.titulo && <p className='invoice-detail-subtitle'>{invoice.titulo}</p>}
				</div>
				<div className='invoice-hero-right'>
					<span className={`status-badge status-${(invoice.workOrderStatus || 'EN_GESTION').toLowerCase().replace(/_/g, '-')}`}>
						{STATUS_LABELS[invoice.workOrderStatus] ?? invoice.workOrderStatus ?? 'En gestión'}
					</span>
					{canEdit && (
						<button className='btn-pill' onClick={() => navigate(`/invoices/${invoiceId}/edit`)}>
							Editar
						</button>
					)}
				</div>
			</div>

			{/* KPI strip */}
			<div className='invoice-kpi-strip'>
				<div className='invoice-kpi-tile'>
					<span className='invoice-kpi-label'>Total</span>
					<span className='invoice-kpi-value'>{formatMoney(invoice.precio)}</span>
				</div>
				<div className='invoice-kpi-tile'>
					<span className='invoice-kpi-label'>Cobrado</span>
					<span className='invoice-kpi-value invoice-kpi-paid'>{formatMoney(invoice.totalPaid)}</span>
				</div>
				<div className={`invoice-kpi-tile${balance > 0 ? ' invoice-kpi-tile--debt' : ' invoice-kpi-tile--paid'}`}>
					<span className='invoice-kpi-label'>Saldo</span>
					<span className='invoice-kpi-value'>{formatMoney(balance)}</span>
				</div>
			</div>

			{/* Info grid */}
			<div className='invoice-info-grid'>
				<div className='panel'>
					<h3 className='card-section-title'>Cliente</h3>
					<p><strong>Nombre:</strong> {invoice.customerName || '-'}</p>
					<p><strong>Teléfono:</strong> {invoice.customerPhone || invoice.clientPhone || '-'}</p>
					<p><strong>ID Cliente:</strong> {invoice.customerId || '-'}</p>
				</div>
				<div className='panel'>
					<h3 className='card-section-title'>Datos de factura</h3>
					<p><strong>Cantidad:</strong> {invoice.cantidad || '-'}</p>
					<p><strong>Emisión:</strong> {invoice.startDate || '-'}</p>
					<p><strong>Vencimiento:</strong> {invoice.fechaEntrega || invoice.fechaEstimada || '-'}</p>
					{invoice.notas && <p><strong>Notas:</strong> {invoice.notas}</p>}
				</div>
			</div>

			{/* Gestión panel */}
			<div className='panel invoice-gestion-panel'>
				<h3 className='card-section-title'>Gestión de cobranza</h3>
				<label className='invoice-field-label'>Estado</label>
				<select
					value={invoice.workOrderStatus || 'EN_GESTION'}
					onChange={(e) => updateStatus(e.target.value)}
					disabled={!canEdit || !invoice.workOrderId}
					className='invoice-detail-status-select'
				>
					{INVOICE_STATUS_OPTIONS.map((status) => (
						<option key={status.value} value={status.value}>
							{STATUS_LABELS[status.value] ?? status.label}
						</option>
					))}
				</select>
				{canEdit && (
					<>
						<h4 className='invoice-payment-subheading'>Registrar pago</h4>
						<form onSubmit={addPayment} className='invoice-payment-form'>
							<LedgerAttachInput onExtracted={handleExtracted} />
							<input
								type='number'
								min='0'
								placeholder='Monto'
								value={payment.amount}
								onChange={(e) => setPayment((prev) => ({...prev, amount: e.target.value}))}
								className='invoice-payment-amount'
							/>
							<select
								value={payment.paymentType}
								onChange={(e) => setPayment((prev) => ({...prev, paymentType: e.target.value}))}
							>
								<option value='PAYMENT'>Pago</option>
								<option value='DEPOSIT'>Anticipo</option>
							</select>
							<button className='btn-pill' type='submit'>Agregar</button>
						</form>
					</>
				)}
			</div>

			{/* Line items */}
			<div className='panel invoice-detail-table'>
				<h3 className='card-section-title'>Items</h3>
				<div className='table-wrapper'>
					<table className='orders-table mobile-card-table'>
						<thead>
							<tr>
								<th>Descripcion</th><th>Cant.</th><th>Precio unit.</th><th>Subtotal</th>
							</tr>
						</thead>
						<tbody>
							{invoice.lineItems?.length ? (
								invoice.lineItems.map((item) => (
									<tr key={item.id}>
										<td data-label='Descripcion'>{item.description}</td>
										<td data-label='Cant.'>{item.quantity}</td>
										<td data-label='Precio unit.'>{formatMoney(item.unitPrice)}</td>
										<td data-label='Subtotal'>{formatMoney(item.subtotal)}</td>
									</tr>
								))
							) : (
								<tr><td colSpan='4' className='invoice-detail-empty-cell'>Sin items cargados.</td></tr>
							)}
						</tbody>
					</table>
				</div>
			</div>

			{/* Payment history */}
			<div className='panel invoice-detail-table'>
				<h3 className='card-section-title'>Historial de pagos</h3>
				<div className='table-wrapper'>
					<table className='orders-table mobile-card-table'>
						<thead>
							<tr><th>Fecha</th><th>Tipo</th><th>Monto</th></tr>
						</thead>
						<tbody>
							{payments.length ? (
								payments.map((p, idx) => (
									<tr key={p.id || idx}>
										<td data-label='Fecha'>{p.paymentDate || '-'}</td>
										<td data-label='Tipo'>{p.paymentType || '-'}</td>
										<td data-label='Monto'>{formatMoney(p.amount)}</td>
									</tr>
								))
							) : (
								<tr><td colSpan='3' className='invoice-detail-empty-cell'>Sin pagos registrados.</td></tr>
							)}
						</tbody>
					</table>
				</div>
			</div>

		</div>
	);
}
