import React, {useCallback, useContext, useEffect, useState} from 'react';
import axios from 'axios';
import {FaChevronLeft, FaChevronRight, FaPlus, FaTrashAlt} from 'react-icons/fa';
import {useNavigate} from 'react-router-dom';
import {UserContext} from '../UserProvider';
import {BASE_URL} from '../api/config';
import InvoiceCreationModal from './invoiceCreationModal';

const formatMoney = (value) =>
	Number(value || 0).toLocaleString('es-AR', {
		style: 'currency',
		currency: 'ARS',
		maximumFractionDigits: 0,
	});

const STATUS_LABELS = {
	CERRADO: 'Cobrada',
	EN_GESTION: 'En gestión',
	PROMETIO_PAGO: 'Prometió pago',
	EN_DISPUTA: 'En disputa',
	INCOBRABLE: 'Incobrable',
	CONTACTADO: 'Contactado',
};

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
	const [isModalOpen, setIsModalOpen] = useState(false);

	const fetchInvoices = useCallback(async () => {
		if (!endpoint) return;
		setLoading(true);
		try {
			const token = localStorage.getItem('token');
			const res = await axios.get(`${BASE_URL}${endpoint}`, {
				headers: {Authorization: `Bearer ${token}`},
				params: {page: currentPage, size: 10},
			});
			setInvoices(res.data.content ?? res.data);
			setTotalPages(res.data.totalPages ?? 1);
		} catch (err) {
			console.error('Error fetching invoices:', err);
		} finally {
			setLoading(false);
		}
	}, [endpoint, currentPage]);

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

	const getRowClass = (status) => {
		if (status === 'CERRADO') return 'row-entregado';
		if (status === 'EN_DISPUTA' || status === 'INCOBRABLE') return 'row-atrasado';
		if (status === 'PROMETIO_PAGO') return 'row-terminado';
		return 'row-produccion';
	};

	const displayedInvoices = invoices.filter((invoice) => {
		if (!searchTerm) return true;
		const search = searchTerm.toLowerCase();
		return invoice.titulo?.toLowerCase().includes(search) ||
			invoice.customerName?.toLowerCase().includes(search);
	});

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
						<FaPlus size={12} /> Agregar o corregir manualmente
					</button>
				)}
			</div>

			<div className='table-wrapper full-height'>
				{loading ? (
					<div className='orders-loading-state'>Cargando...</div>
				) : (
					<table className='orders-table'>
						<thead><tr>
							<th>Id</th><th>Título</th><th>Cliente</th><th>Cant.</th>
							<th>Emisión</th><th>Vencimiento</th><th>Estado</th><th>Saldo</th>
							{canDelete && <th>Acciones</th>}
						</tr></thead>
						<tbody>
							{displayedInvoices.length > 0 ? displayedInvoices.map((invoice) => (
								<tr key={invoice.id} className={getRowClass(invoice.workOrderStatus)} onClick={() => navigate(`/invoices/${invoice.id}`)}>
									<td>{invoice.id}</td>
									<td className='truncate'><strong>{invoice.titulo}</strong></td>
									<td>{invoice.customerName || '-'}</td>
									<td>{invoice.cantidad || '-'}</td>
									<td>{invoice.startDate || '-'}</td>
									<td>{invoice.fechaEntrega || invoice.fechaEstimada || '-'}</td>
									<td><span className={`status-badge status-${(invoice.workOrderStatus || 'EN_GESTION').toLowerCase().replace(/_/g, '-')}`}>
										{STATUS_LABELS[invoice.workOrderStatus] ?? invoice.workOrderStatus ?? 'En gestión'}
									</span></td>
									<td>{formatMoney(Number(invoice.precio || 0) - Number(invoice.totalPaid || 0))}</td>
									{canDelete && <td onClick={(e) => e.stopPropagation()}>
										<button className='button-red-icon orders-delete-action' onClick={() => handleDelete(invoice.id)} aria-label={`Eliminar factura ${invoice.id}`}>
											<FaTrashAlt />
										</button>
									</td>}
								</tr>
							)) : (
								<tr><td colSpan={canDelete ? 9 : 8} className='orders-empty-cell'>
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
