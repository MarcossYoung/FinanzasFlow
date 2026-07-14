import React, {useCallback, useContext, useEffect, useState} from 'react';
import axios from 'axios';
import {FaChevronLeft, FaChevronRight, FaPlus, FaTrashAlt} from 'react-icons/fa';
import {useNavigate} from 'react-router-dom';
import {UserContext} from '../UserProvider';
import {BASE_URL} from '../api/config';
import InvoiceCreationModal from './invoiceCreationModal';
import {INVOICE_STATUS_OPTIONS, statusLabel} from '../constants/invoiceStatus';

const formatMoney = (value) =>
	Number(value || 0).toLocaleString('es-AR', {
		style: 'currency',
		currency: 'ARS',
		maximumFractionDigits: 0,
	});

export default function InvoicesTable({endpoint, allowManualCreate = false}) {
	const {user} = useContext(UserContext);
	const navigate = useNavigate();
	const canEdit = user?.role === 'ADMIN' || user?.role === 'GESTOR';
	const canDelete = user?.role === 'ADMIN';
	const canCreateManually = allowManualCreate && canEdit;
	const [invoices, setInvoices] = useState([]);
	const [loading, setLoading] = useState(true);
	const [currentPage, setCurrentPage] = useState(0);
	const [totalPages, setTotalPages] = useState(0);
	const [searchTerm, setSearchTerm] = useState('');
	const [debouncedSearchTerm, setDebouncedSearchTerm] = useState('');
	const [isModalOpen, setIsModalOpen] = useState(false);
	const [updatingStatusId, setUpdatingStatusId] = useState(null);

	const fetchInvoices = useCallback(async () => {
		if (!endpoint) return;
		setLoading(true);
		try {
			const token = localStorage.getItem('token');
			const isSearching = debouncedSearchTerm.trim().length > 0;
			const res = await axios.get(
				`${BASE_URL}${isSearching ? '/api/invoices/search' : endpoint}`,
				{
					headers: {Authorization: `Bearer ${token}`},
					params: isSearching
						? {q: debouncedSearchTerm.trim(), page: currentPage, size: 10}
						: {page: currentPage, size: 10},
				},
			);
			setInvoices(res.data.content ?? res.data);
			setTotalPages(res.data.totalPages ?? 1);
		} catch (err) {
			console.error('Error fetching invoices:', err);
		} finally {
			setLoading(false);
		}
	}, [endpoint, currentPage, debouncedSearchTerm]);

	useEffect(() => {
		const timeout = setTimeout(() => setDebouncedSearchTerm(searchTerm), 300);
		return () => clearTimeout(timeout);
	}, [searchTerm]);

	useEffect(() => {
		setCurrentPage(0);
	}, [debouncedSearchTerm]);

	useEffect(() => {
		fetchInvoices();
	}, [fetchInvoices]);

	const handleModalClose = () => {
		setIsModalOpen(false);
		fetchInvoices();
	};

	const handleDelete = async (id) => {
		if (!window.confirm('¿Eliminar esta factura?')) return;
		try {
			const token = localStorage.getItem('token');
			await axios.delete(`${BASE_URL}/api/invoices/${id}`, {
				headers: {Authorization: `Bearer ${token}`},
			});
			fetchInvoices();
		} catch {
			alert('Error al eliminar');
		}
	};

	const handleStatusChange = async (invoice, status) => {
		if (!invoice.workOrderId) {
			console.warn('Invoice has no linked work order, cannot update status', invoice.id);
			alert('Esta factura no tiene una orden de trabajo asociada; no se puede cambiar el estado.');
			return;
		}
		if (status === invoice.workOrderStatus) return;
		const previousStatus = invoice.workOrderStatus;
		setInvoices((prev) =>
			prev.map((inv) => (inv.id === invoice.id ? {...inv, workOrderStatus: status} : inv)),
		);
		setUpdatingStatusId(invoice.id);
		try {
			const token = localStorage.getItem('token');
			await axios.put(
				`${BASE_URL}/api/workorders/${invoice.workOrderId}/status`,
				null,
				{params: {status}, headers: {Authorization: `Bearer ${token}`}},
			);
		} catch (err) {
			console.error('Error updating status:', err);
			setInvoices((prev) =>
				prev.map((inv) => (inv.id === invoice.id ? {...inv, workOrderStatus: previousStatus} : inv)),
			);
			alert('No se pudo actualizar el estado.');
		} finally {
			setUpdatingStatusId(null);
		}
	};

	const getRowClass = (status) => {
		if (status === 'CERRADO') return 'row-entregado';
		if (status === 'CANCELADO' || status === 'EN_DISPUTA' || status === 'INCOBRABLE') return 'row-atrasado';
		if (status === 'PROMETIO_PAGO') return 'row-terminado';
		return 'row-produccion';
	};

	return (
		<div className='orders-view-container'>
			<div className='admin-tools'>
				<input
					type='text'
					placeholder='Buscar por título o cliente...'
					value={searchTerm}
					onChange={(e) => setSearchTerm(e.target.value)}
				/>
				{canCreateManually && (
					<button className='btn-pill manual-entry-action' onClick={() => setIsModalOpen(true)}>
						<FaPlus size={12} /> Agregar nuevo
					</button>
				)}
			</div>

			<div className='table-wrapper full-height'>
				{loading ? (
					<div className='orders-loading-state'>Cargando...</div>
				) : (
					<table className='orders-table invoices-strip-table'>
						<thead><tr>
							<th>Id</th><th>Título</th><th>Cliente</th>
							<th>Emisión</th><th>Vencimiento</th><th>Estado</th><th>Saldo</th>
							{canDelete && <th>Acciones</th>}
						</tr></thead>
						<tbody>
							{invoices.length > 0 ? invoices.map((invoice) => (
								<tr key={invoice.id} className={getRowClass(invoice.workOrderStatus)} onClick={() => navigate(`/invoices/${invoice.id}`)}>
									<td data-label='Id' className='inv-id-cell'>{invoice.id}</td>
									<td data-label='Titulo' className='truncate'><strong>{invoice.titulo}</strong></td>
									<td data-label='Cliente'>{invoice.customerName || '-'}</td>
									<td data-label='Emision'>{invoice.startDate || '-'}</td>
									<td data-label='Vencimiento'>{invoice.fechaEntrega || invoice.fechaEstimada || '-'}</td>
									<td data-label='Estado' className='inv-status-cell' onClick={(e) => e.stopPropagation()}>
										{canEdit ? (
											<select
												className={`status-select status-${(invoice.workOrderStatus || 'EN_GESTION').toLowerCase().replace(/_/g, '-')}`}
												value={invoice.workOrderStatus || 'EN_GESTION'}
												disabled={updatingStatusId === invoice.id}
												onChange={(e) => handleStatusChange(invoice, e.target.value)}
											>
												{!INVOICE_STATUS_OPTIONS.some((opt) => opt.value === invoice.workOrderStatus) && (
													<option value={invoice.workOrderStatus}>{statusLabel(invoice.workOrderStatus)}</option>
												)}
												{INVOICE_STATUS_OPTIONS.map(({value, label}) => (
													<option key={value} value={value}>{label}</option>
												))}
											</select>
										) : (
											<span className={`status-badge status-${(invoice.workOrderStatus || 'EN_GESTION').toLowerCase().replace(/_/g, '-')}`}>
												{statusLabel(invoice.workOrderStatus)}
											</span>
										)}
									</td>
									<td data-label='Saldo'>{formatMoney(Number(invoice.precio || 0) - Number(invoice.totalPaid || 0))}</td>
									{canDelete && <td data-label='Acciones' onClick={(e) => e.stopPropagation()}>
										<button className='button-red-icon orders-delete-action' onClick={() => handleDelete(invoice.id)} aria-label={`Eliminar factura ${invoice.id}`}>
											<FaTrashAlt />
										</button>
									</td>}
								</tr>
							)) : (
								<tr><td colSpan={canDelete ? 8 : 7} className='orders-empty-cell'>
									{searchTerm
										? 'No se encontraron resultados para esa búsqueda.'
										: 'Todavía no hay facturas. Las facturas ingresadas por Telegram aparecerán aquí.'}
								</td></tr>
							)}
						</tbody>
					</table>
				)}
			</div>

			<div className='pagination-container'>
				<button disabled={currentPage === 0} onClick={() => setCurrentPage((page) => page - 1)} className='btn-pagination'>
					<FaChevronLeft /> Anterior
				</button>
				<span>Página <strong>{currentPage + 1}</strong> de {totalPages || 1}</span>
				<button disabled={currentPage >= totalPages - 1} onClick={() => setCurrentPage((page) => page + 1)} className='btn-pagination'>
					Siguiente <FaChevronRight />
				</button>
			</div>

			{canCreateManually && <InvoiceCreationModal isOpen={isModalOpen} onClose={handleModalClose} />}
		</div>
	);
}
