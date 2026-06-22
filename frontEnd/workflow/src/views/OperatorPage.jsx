import React, {useCallback, useEffect, useState} from 'react';
import axios from 'axios';
import {BASE_URL} from '../api/config';

export default function OperatorPage() {
	const [tenants, setTenants] = useState([]);
	const [actionQueue, setActionQueue] = useState([]);
	const [selectedTenant, setSelectedTenant] = useState(null);
	const [loading, setLoading] = useState(true);
	const [queueLoading, setQueueLoading] = useState(false);
	const [error, setError] = useState('');

	const fetchTenants = useCallback(async () => {
		const token = localStorage.getItem('token');
		try {
			const res = await axios.get(`${BASE_URL}/api/operator/tenants`, {
				headers: {Authorization: `Bearer ${token}`},
			});
			setTenants(res.data);
		} catch {
			setError('Error al cargar tenants.');
		} finally {
			setLoading(false);
		}
	}, []);

	const fetchActionQueue = useCallback(async (tenantId) => {
		const token = localStorage.getItem('token');
		setQueueLoading(true);
		try {
			const res = await axios.get(`${BASE_URL}/api/operator/tenants/${tenantId}/action-queue`, {
				headers: {Authorization: `Bearer ${token}`},
			});
			setActionQueue(res.data);
		} catch {
			setActionQueue([]);
		} finally {
			setQueueLoading(false);
		}
	}, []);

	useEffect(() => { fetchTenants(); }, [fetchTenants]);

	const handleSelectTenant = (tenant) => {
		setSelectedTenant(tenant);
		fetchActionQueue(tenant.id);
	};

	const fmt = (value) => new Intl.NumberFormat('es-AR', {
		style: 'currency', currency: 'ARS', maximumFractionDigits: 0,
	}).format(value);

	if (loading) return <div className='operator-state'>Cargando...</div>;

	return (
		<div className='operator-page'>
			<h1 className='page-title'>Panel Operador</h1>
			{error && <p className='operator-error' role='alert'>{error}</p>}
			{!error && tenants.length === 0 && <p className='operator-state'>No hay tenants disponibles.</p>}

			<div className='operator-tenant-grid'>
				{tenants.map((tenant) => (
					<button
						key={tenant.id}
						onClick={() => handleSelectTenant(tenant)}
						className={`operator-tenant-card${selectedTenant?.id === tenant.id ? ' operator-tenant-card-active' : ''}`}
						aria-pressed={selectedTenant?.id === tenant.id}
					>
						<div className='operator-tenant-header'>
							<div><strong>{tenant.name}</strong><span>ID: {tenant.id}</span></div>
							<span className='operator-tenant-badge'>Tenant</span>
						</div>
						<div className='operator-metric-grid'>
							<div><span>Facturas</span><strong>{tenant.totalInvoices}</strong></div>
							<div><span>Total facturado</span><strong>{fmt(tenant.totalOwed)}</strong></div>
						</div>
					</button>
				))}
			</div>

			{selectedTenant && <section className='operator-action-section'>
				<h2>Cola de acción hoy — <span>{selectedTenant.name}</span></h2>
				{queueLoading ? <p className='operator-state'>Cargando acciones...</p> : actionQueue.length === 0 ? (
					<p className='operator-state'>Sin acciones pendientes para hoy.</p>
				) : (
					<div className='operator-action-table-wrapper'>
						<table className='operator-action-table'>
							<thead><tr><th>ID</th><th>Estado</th><th>Fecha esperada</th></tr></thead>
							<tbody>{actionQueue.map((item) => <tr key={item.id}>
								<td>{item.id}</td><td>{item.status ?? '—'}</td><td>{item.expectedDate ?? '—'}</td>
							</tr>)}</tbody>
						</table>
					</div>
				)}
			</section>}
		</div>
	);
}
