import {useEffect, useState, useContext} from 'react';
import {useNavigate, useParams} from 'react-router-dom';
import axios from 'axios';
import {BASE_URL} from '../api/config';
import {UserContext} from '../UserProvider';

const statuses = [
	'EN_GESTION',
	'CONTACTADO',
	'PROMETIO_PAGO',
	'EN_DISPUTA',
	'INCOBRABLE',
	'CERRADO',
];

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

	if (error) return <p className='error-text'>{error}</p>;
	if (!invoice) return <p style={{padding: '2rem'}}>Cargando...</p>;

	const balance = Number(invoice.precio || 0) - Number(invoice.totalPaid || 0);

	return (
		<div className='p-6'>
			<div className='orders-header' style={{alignItems: 'center'}}>
				<div>
					<h1 className='main-title'>Factura #{invoiceId}</h1>
					<p style={{margin: 0, color: '#636e72'}}>{invoice.titulo}</p>
				</div>
				{canEdit && (
					<button
						className='btn-pill'
						onClick={() => navigate(`/invoices/${invoiceId}/edit`)}
					>
						Editar
					</button>
				)}
			</div>

			<div className='dashboard-grid' style={{display: 'grid', gap: 16, gridTemplateColumns: 'repeat(auto-fit, minmax(260px, 1fr))'}}>
				<section className='stat-card'>
					<h3>Datos de factura</h3>
					<p><strong>Cantidad:</strong> {invoice.cantidad || '-'}</p>
					<p><strong>Emision:</strong> {invoice.startDate || '-'}</p>
					<p><strong>Vencimiento:</strong> {invoice.fechaEntrega || invoice.fechaEstimada || '-'}</p>
					<p><strong>Notas:</strong> {invoice.notas || '-'}</p>
				</section>

				<section className='stat-card'>
					<h3>Cliente</h3>
					<p><strong>Nombre:</strong> {invoice.customerName || '-'}</p>
					<p><strong>Telefono:</strong> {invoice.customerPhone || invoice.clientPhone || '-'}</p>
					<p><strong>ID Cliente:</strong> {invoice.customerId || '-'}</p>
				</section>

				<section className='stat-card'>
					<h3>Gestion</h3>
					<label>Estado</label>
					<select
						value={invoice.workOrderStatus || 'EN_GESTION'}
						onChange={(e) => updateStatus(e.target.value)}
						disabled={!canEdit || !invoice.workOrderId}
						style={{width: '100%', padding: 10, marginTop: 6}}
					>
						{statuses.map((status) => (
							<option key={status} value={status}>{status}</option>
						))}
					</select>
				</section>

				<section className='stat-card'>
					<h3>Cobranza</h3>
					<p><strong>Total:</strong> {formatMoney(invoice.precio)}</p>
					<p><strong>Cobrado:</strong> {formatMoney(invoice.totalPaid)}</p>
					<p><strong>Saldo:</strong> {formatMoney(balance)}</p>
					{canEdit && (
						<form onSubmit={addPayment} style={{display: 'flex', gap: 8, marginTop: 12}}>
							<input
								type='number'
								min='0'
								placeholder='Monto'
								value={payment.amount}
								onChange={(e) => setPayment((prev) => ({...prev, amount: e.target.value}))}
								style={{minWidth: 0, flex: 1}}
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
					)}
				</section>
			</div>

			<section className='table-wrapper' style={{marginTop: 20}}>
				<table className='orders-table'>
					<thead>
						<tr>
							<th>Descripcion</th>
							<th>Cant.</th>
							<th>Precio unit.</th>
							<th>Subtotal</th>
						</tr>
					</thead>
					<tbody>
						{invoice.lineItems?.length ? (
							invoice.lineItems.map((item) => (
								<tr key={item.id}>
									<td>{item.description}</td>
									<td>{item.quantity}</td>
									<td>{formatMoney(item.unitPrice)}</td>
									<td>{formatMoney(item.subtotal)}</td>
								</tr>
							))
						) : (
							<tr>
								<td colSpan='4' style={{textAlign: 'center', padding: '2rem', color: '#888'}}>
									Sin items cargados.
								</td>
							</tr>
						)}
					</tbody>
				</table>
			</section>

			<section className='table-wrapper' style={{marginTop: 20}}>
				<table className='orders-table'>
					<thead>
						<tr>
							<th>Fecha</th>
							<th>Tipo</th>
							<th>Monto</th>
						</tr>
					</thead>
					<tbody>
						{payments.length ? (
							payments.map((p, idx) => (
								<tr key={p.id || idx}>
									<td>{p.paymentDate || '-'}</td>
									<td>{p.paymentType || '-'}</td>
									<td>{formatMoney(p.amount)}</td>
								</tr>
							))
						) : (
							<tr>
								<td colSpan='3' style={{textAlign: 'center', padding: '2rem', color: '#888'}}>
									Sin pagos registrados.
								</td>
							</tr>
						)}
					</tbody>
				</table>
			</section>
		</div>
	);
}
