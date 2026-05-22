import React, { useEffect, useState, useCallback } from 'react';
import axios from 'axios';
import { BASE_URL } from '../api/config';

export default function OperatorPage() {
	const [tenants, setTenants] = useState([]);
	const [actionQueue, setActionQueue] = useState([]);
	const [selectedTenant, setSelectedTenant] = useState(null);
	const [loading, setLoading] = useState(true);
	const [error, setError] = useState('');

	const token = localStorage.getItem('token');
	const authHeader = { headers: { Authorization: `Bearer ${token}` } };

	const fetchTenants = useCallback(async () => {
		try {
			const res = await axios.get(`${BASE_URL}/api/operator/tenants`, authHeader);
			setTenants(res.data);
		} catch {
			setError('Error al cargar tenants.');
		} finally {
			setLoading(false);
		}
	}, []);

	const fetchActionQueue = useCallback(async (tenantId) => {
		try {
			const res = await axios.get(
				`${BASE_URL}/api/operator/tenants/${tenantId}/action-queue`,
				authHeader,
			);
			setActionQueue(res.data);
		} catch {
			setActionQueue([]);
		}
	}, []);

	useEffect(() => {
		fetchTenants();
	}, [fetchTenants]);

	const handleSelectTenant = (tenant) => {
		setSelectedTenant(tenant);
		fetchActionQueue(tenant.id);
	};

	const fmt = (n) =>
		new Intl.NumberFormat('es-AR', { style: 'currency', currency: 'ARS', maximumFractionDigits: 0 }).format(n);

	if (loading) return <div className="p-8 text-gray-500">Cargando...</div>;

	return (
		<div className="p-6 max-w-5xl mx-auto">
			<h1 className="text-2xl font-bold mb-6">Panel Operador</h1>

			{error && <p className="text-red-500 mb-4">{error}</p>}

			{/* Tenant Cards */}
			<div className="grid grid-cols-1 md:grid-cols-2 gap-4 mb-8">
				{tenants.map((t) => (
					<button
						key={t.id}
						onClick={() => handleSelectTenant(t)}
						className={`text-left p-5 rounded-lg border shadow-sm hover:shadow-md transition-shadow ${
							selectedTenant?.id === t.id
								? 'border-amber-400 bg-amber-50'
								: 'border-gray-200 bg-white'
						}`}
					>
						<div className="flex justify-between items-start">
							<div>
								<p className="font-semibold text-lg">{t.name}</p>
								<p className="text-sm text-gray-500 mt-1">ID: {t.id}</p>
							</div>
							<span className="text-xs bg-gray-100 text-gray-600 px-2 py-1 rounded-full">
								Tenant
							</span>
						</div>
						<div className="mt-4 grid grid-cols-2 gap-3">
							<div className="bg-gray-50 rounded p-3">
								<p className="text-xs text-gray-500">Facturas</p>
								<p className="text-xl font-bold">{t.totalInvoices}</p>
							</div>
							<div className="bg-gray-50 rounded p-3">
								<p className="text-xs text-gray-500">Total facturado</p>
								<p className="text-xl font-bold">{fmt(t.totalOwed)}</p>
							</div>
						</div>
					</button>
				))}
			</div>

			{/* Action Queue for selected tenant */}
			{selectedTenant && (
				<div>
					<h2 className="text-lg font-semibold mb-3">
						Cola de acción hoy — <span className="text-amber-600">{selectedTenant.name}</span>
					</h2>
					{actionQueue.length === 0 ? (
						<p className="text-gray-400 text-sm">Sin acciones pendientes para hoy.</p>
					) : (
						<div className="overflow-x-auto">
							<table className="w-full text-sm border-collapse">
								<thead>
									<tr className="bg-gray-100 text-left">
										<th className="p-3 font-medium">ID</th>
										<th className="p-3 font-medium">Estado</th>
										<th className="p-3 font-medium">Fecha esperada</th>
									</tr>
								</thead>
								<tbody>
									{actionQueue.map((item) => (
										<tr key={item.id} className="border-t hover:bg-gray-50">
											<td className="p-3">{item.id}</td>
											<td className="p-3">{item.status ?? '—'}</td>
											<td className="p-3">{item.expectedDate ?? '—'}</td>
										</tr>
									))}
								</tbody>
							</table>
						</div>
					)}
				</div>
			)}
		</div>
	);
}
