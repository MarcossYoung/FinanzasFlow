import React, {useState, useEffect, useContext, useCallback} from 'react';
import axios from 'axios';
import {
	FaTrashAlt,
	FaChevronLeft,
	FaChevronRight,
	FaFilter,
	FaPlus,
} from 'react-icons/fa';
import {UserContext} from '../UserProvider';
import {BASE_URL} from '../api/config';
import InvoiceCreationModal from './invoiceCreationModal';

const formatMoney = (value) =>
	Number(value || 0).toLocaleString('es-AR', {
		style: 'currency',
		currency: 'ARS',
		maximumFractionDigits: 0,
	});

export default function InvoicesTable({endpoint}) {
	const {user} = useContext(UserContext);
	const canEdit = user?.role === 'ADMIN' || user?.role === 'SUPER_ADMIN' || user?.role === 'GESTOR';

	const [invoices, setInvoices] = useState([]);
	const [loading, setLoading] = useState(false);
	const [currentPage, setCurrentPage] = useState(0);
	const [totalPages, setTotalPages] = useState(0);
	const [searchTerm, setSearchTerm] = useState('');
	const [showMySales, setShowMySales] = useState(false);
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

			if (res.data.content) {
				setInvoices(res.data.content);
				setTotalPages(res.data.totalPages);
			} else {
				setInvoices(res.data);
				setTotalPages(1);
			}
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
		if (!window.confirm('Eliminar esta factura?')) return;
		try {
			const token = localStorage.getItem('token');
			await axios.delete(`${BASE_URL}/api/invoices/${id}`, {
				headers: {Authorization: `Bearer ${token}`},
			});
			fetchInvoices();
		} catch (err) {
			alert('Error al eliminar');
		}
	};

	const handleDetail = (id) => {
		window.location.href = `/invoices/${id}`;
	};

	const getRowClass = (status) => {
		if (status === 'CERRADO') return 'row-entregado';
		if (status === 'EN_DISPUTA' || status === 'INCOBRABLE') return 'row-atrasado';
		if (status === 'PROMETIO_PAGO') return 'row-terminado';
		return 'row-produccion';
	};

	const displayedInvoices = invoices
		.filter((invoice) => {
			if (!searchTerm) return true;
			const search = searchTerm.toLowerCase();
			return (
				invoice.titulo?.toLowerCase().includes(search) ||
				invoice.customerName?.toLowerCase().includes(search)
			);
		})
		.filter((invoice) => {
			if (!showMySales) return true;
			return invoice.ownerId === user?.id || invoice.owner?.id === user?.id;
		});

	return (
		<div className='orders-view-container'>
			<div className='admin-tools'>
				<input
					type='text'
					placeholder='Buscar por titulo o cliente...'
					value={searchTerm}
					onChange={(e) => setSearchTerm(e.target.value)}
				/>

				<button
					onClick={() => setShowMySales(!showMySales)}
					className={`btn-pill ${showMySales ? 'active' : ''}`}
				>
					<FaFilter size={14} />
					{showMySales ? 'Todas las facturas' : 'Ver mis facturas'}
				</button>

				{canEdit && (
					<button
						className='btn-pill'
						onClick={() => setIsModalOpen(true)}
						style={{
							backgroundColor: '#00b894',
							color: 'white',
							border: 'none',
						}}
					>
						<FaPlus size={12} />
						Nueva Factura
					</button>
				)}
			</div>

			<div className='table-wrapper full-height'>
				{loading ? (
					<div className='text-center' style={{padding: '2rem'}}>
						Cargando...
					</div>
				) : (
					<table className='orders-table'>
						<thead>
							<tr>
								<th>Id</th>
								<th>Titulo</th>
								<th>Cliente</th>
								<th>Cant.</th>
								<th>Emision</th>
								<th>Vencimiento</th>
								<th>Estado</th>
								<th>Saldo</th>
								{(user?.role === 'ADMIN' || user?.role === 'SUPER_ADMIN') && <th>Acciones</th>}
							</tr>
						</thead>
						<tbody>
							{displayedInvoices.length > 0 ? (
								displayedInvoices.map((invoice) => (
									<tr
										key={invoice.id}
										className={getRowClass(invoice.workOrderStatus)}
										onClick={() => handleDetail(invoice.id)}
									>
										<td>{invoice.id}</td>
										<td className='truncate'>
											<strong>{invoice.titulo}</strong>
										</td>
										<td>{invoice.customerName || '-'}</td>
										<td>{invoice.cantidad || '-'}</td>
										<td>{invoice.startDate || '-'}</td>
										<td>{invoice.fechaEntrega || invoice.fechaEstimada || '-'}</td>
										<td>{invoice.workOrderStatus || 'EN_GESTION'}</td>
										<td>
											{formatMoney(
												Number(invoice.precio || 0) -
													Number(invoice.totalPaid || 0),
											)}
										</td>
										{(user?.role === 'ADMIN' || user?.role === 'SUPER_ADMIN') && (
											<td onClick={(e) => e.stopPropagation()}>
												<button
													className='button-red-icon'
													style={{
														background: 'transparent',
														border: 'none',
														color: '#EF4444',
														cursor: 'pointer',
														fontSize: '1.2rem',
													}}
													onClick={() => handleDelete(invoice.id)}
												>
													<FaTrashAlt />
												</button>
											</td>
										)}
									</tr>
								))
							) : (
								<tr>
									<td
										colSpan='9'
										className='text-center'
										style={{padding: '2rem', color: '#888'}}
									>
										No se encontraron facturas.
									</td>
								</tr>
							)}
						</tbody>
					</table>
				)}
			</div>

			<div className='pagination-container'>
				<button
					disabled={currentPage === 0}
					onClick={() => setCurrentPage((prev) => prev - 1)}
					className='btn-pagination'
				>
					<FaChevronLeft /> Anterior
				</button>

				<span>
					Pagina <strong>{currentPage + 1}</strong> de {totalPages || 1}
				</span>

				<button
					disabled={currentPage >= totalPages - 1}
					onClick={() => setCurrentPage((prev) => prev + 1)}
					className='btn-pagination'
				>
					Siguiente <FaChevronRight />
				</button>
			</div>

			<InvoiceCreationModal isOpen={isModalOpen} onClose={handleModalClose} />
		</div>
	);
}
